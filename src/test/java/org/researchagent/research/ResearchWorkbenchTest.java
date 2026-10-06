package org.researchagent.research;

import com.fasterxml.jackson.databind.*;
import org.researchagent.research.agent.ResearchAgentEngine;
import org.researchagent.research.task.*;
import org.researchagent.research.workspace.WorkspaceService;
import org.researchagent.research.knowledge.KnowledgeService;
import org.researchagent.research.knowledge.ProjectMemoryService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:research;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.cache.type=simple", "research.ai.mode=demo"
})
@AutoConfigureMockMvc
@Import(ResearchWorkbenchTest.Database.class)
class ResearchWorkbenchTest {
    static final Path ROOT;
    static { try { ROOT=Files.createTempDirectory("research-agent-test-"); } catch(Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("research.workspace-root",ROOT::toString); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TaskRepository tasks;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkspaceService workspaces;
    @Autowired KnowledgeService knowledge;
    @Autowired ProjectMemoryService memory;

    @TestConfiguration static class Database {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) throws Exception {
            var jdbc=new JdbcTemplate(source);
            for (String name:List.of("V1__local_research_schema.sql", "V2__knowledge_documents.sql", "V3__paper_subscriptions.sql", "V4__paper_to_code.sql")) {
                String sql=new String(new ClassPathResource("db/local/"+name).getInputStream().readAllBytes(),StandardCharsets.UTF_8);
                try (var connection=source.getConnection()) {
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                            new org.springframework.core.io.support.EncodedResource(
                                    new org.springframework.core.io.ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8));
                }
            }
            return jdbc;
        }
    }
    JsonNode body(org.springframework.test.web.servlet.ResultActions action) throws Exception {
        return json.readTree(action.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    String project(String language) throws Exception {
        JsonNode result=body(mvc.perform(post("/research-projects").contentType("application/json")
                .content(json.writeValueAsString(Map.of("name","测试科研项目","defaultLanguage",language)))));
        assertThat(result.get("code").asInt()).isZero();
        return result.path("data").path("id").asText();
    }
    String task(String project) throws Exception {
        JsonNode result=body(mvc.perform(post("/projects/"+project+"/tasks").contentType("application/json")
                .content("{\"prompt\":\"生成可复现的科研示例代码和说明\"}")));
        assertThat(result.get("code").asInt()).isZero();
        assertThat(result.path("data").path("status").asText()).isEqualTo("WAITING_APPROVAL");
        return result.path("data").path("id").asText();
    }
    @Test void projectApprovalBackgroundGenerationAndDownload() throws Exception {
        String project=project("python");
        String id=task(project);
        assertThat(body(mvc.perform(get("/tasks/"+id+"/files"))).path("data").size()).isZero();
        assertThat(body(mvc.perform(post("/tasks/"+id+"/approve"))).get("code").asInt()).isZero();
        assertThat(body(mvc.perform(post("/tasks/"+id+"/approve"))).get("code").asInt()).isNotZero();
        long deadline=System.currentTimeMillis()+10000;
        while(!Set.of("SUCCEEDED","FAILED").contains(tasks.get(Long.parseLong(id)).status()) && System.currentTimeMillis()<deadline) Thread.sleep(100);
        assertThat(tasks.get(Long.parseLong(id)).status()).isEqualTo("SUCCEEDED");
        var files=body(mvc.perform(get("/tasks/"+id+"/files"))).path("data");
        assertThat(files.toString()).contains("main.py","README.md");
        assertThat(body(mvc.perform(get("/tasks/"+id+"/events"))).path("data").toString()).contains("TOOL","DONE");
        byte[] zip=mvc.perform(get("/tasks/"+id+"/download")).andReturn().getResponse().getContentAsByteArray();
        try(var archive=new ZipInputStream(new ByteArrayInputStream(zip))) { assertThat(archive.getNextEntry()).isNotNull(); }
    }
    @Test void cancellationAndPathEscapeAreEnforced() throws Exception {
        String project=project("go"),id=task(project);
        assertThat(body(mvc.perform(post("/tasks/"+id+"/cancel"))).path("data").path("status").asText()).isEqualTo("CANCELED");
        assertThat(body(mvc.perform(post("/tasks/"+id+"/approve"))).get("code").asInt()).isNotZero();
        assertThat(body(mvc.perform(get("/tasks/"+id+"/file").param("path","../../../../secret.txt"))).get("code").asInt()).isEqualTo(40000);
        assertThat(body(mvc.perform(post("/research-projects").contentType("application/json")
                .content(json.writeValueAsString(Map.of("name","越界目录","workspacePath",ROOT.getParent().toString()))))).get("code").asInt()).isEqualTo(40000);
    }
    @Test void projectMemoryAndKnowledgeAreUsedByTheDemoTask() throws Exception {
        String project = project("python");
        String note = json.writeValueAsString(Map.of("source", "Early Stopping Paper", "content", "早停方法：验证损失连续三轮无改善时停止。"));
        assertThat(body(mvc.perform(post("/research-projects/"+project+"/knowledge")
                .contentType("application/json").content(note))).path("data").path("source").asText()).isEqualTo("Early Stopping Paper");
        assertThat(body(mvc.perform(put("/research-projects/"+project+"/memory")
                .contentType("application/json").content("{\"content\":\"数据集由用户提供\"}"))).path("data").asText())
                .isEqualTo("数据集由用户提供");
        assertThat(body(mvc.perform(get("/research-projects/"+project+"/knowledge/search").param("query", "早停")))
                .path("data").toString()).contains("Early Stopping Paper");
        String id = body(mvc.perform(post("/projects/"+project+"/tasks")
                .contentType("application/json").content("{\"prompt\":\"实现早停代码\"}")))
                .path("data").path("id").asText();
        assertThat(body(mvc.perform(post("/tasks/"+id+"/approve"))).get("code").asInt()).isZero();
        long deadline = System.currentTimeMillis() + 10000;
        while (!Set.of("SUCCEEDED", "FAILED").contains(tasks.get(Long.parseLong(id)).status()) && System.currentTimeMillis() < deadline)
            Thread.sleep(100);
        assertThat(tasks.get(Long.parseLong(id)).status()).isEqualTo("SUCCEEDED");
        assertThat(body(mvc.perform(get("/tasks/"+id+"/events"))).path("data").toString())
                .contains("RETRIEVAL", "Early Stopping Paper");
        assertThat(body(mvc.perform(get("/research-projects/"+project+"/memory"))).path("data").asText())
                .isEqualTo("数据集由用户提供");
    }
    @Test void paperSubscriptionCanBeSavedWithoutFetchingTheNetwork() throws Exception {
        String project = project("python");
        assertThat(body(mvc.perform(put("/research-projects/"+project+"/papers/subscription")
                .contentType("application/json").content("{\"topic\":\"remote sensing\",\"enabled\":true}")))
                .path("data").path("enabled").asBoolean()).isTrue();
        assertThat(body(mvc.perform(get("/research-projects/"+project+"/papers/subscription")))
                .path("data").path("topic").asText()).isEqualTo("remote sensing");
        assertThat(body(mvc.perform(get("/research-projects"))).path("data").get(0).path("createdAt").isTextual()).isTrue();
        assertThat(body(mvc.perform(put("/research-projects/"+project+"/papers/subscription")
                .contentType("application/json").content("{\"topic\":\"../../secret\",\"enabled\":true}")))
                .path("code").asInt()).isNotZero();
    }
    @Test void pdfImportCreatesReviewablePaperToCodeTask() throws Exception {
        String project = project("python");
        byte[] pdf;
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(); document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(40, 750);
                content.showText("Method"); content.newLineAtOffset(0, -20);
                String line = "We train an encoder with three random seeds and compare held out accuracy. ";
                for (int i = 0; i < 6; i++) { content.showText(line); content.newLineAtOffset(0, -20); }
                content.endText();
            }
            document.save(output); pdf = output.toByteArray();
        }
        var response = body(mvc.perform(multipart("/research-projects/"+project+"/paper-methods")
                .file(new MockMultipartFile("file", "paper.pdf", "application/pdf", pdf)).param("title", "Encoder Paper")));
        assertThat(response.path("code").asInt()).isZero();
        String methodId = response.path("data").path("id").asText();
        assertThat(response.path("data").path("methodBrief").asText()).contains("random seeds");
        assertThat(body(mvc.perform(get("/research-projects/"+project+"/paper-methods"))).path("data").size()).isEqualTo(1);
        var task = body(mvc.perform(post("/research-projects/"+project+"/paper-methods/"+methodId+"/tasks")
                .contentType("application/json").content("{\"instructions\":\"use local data\"}")));
        assertThat(task.path("data").path("status").asText()).isEqualTo("WAITING_APPROVAL");
        assertThat(task.path("data").path("requestText").asText()).contains("Encoder Paper", "use local data");
        var bad = body(mvc.perform(multipart("/research-projects/"+project+"/paper-methods")
                .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "bad".getBytes(StandardCharsets.UTF_8)))
                .param("title", "Bad")));
        assertThat(bad.path("code").asInt()).isNotZero();
    }
    @Test void actualLangChainToolLoopWritesAnArtifactUsingCompatibleMockEndpoint() throws Exception {
        long project=Long.parseLong(project("python"));
        long owner=jdbc.queryForObject("select user_id from research_project where id=?",Long.class,project);
        long id=tasks.create(project,owner,"写入科研脚本","live");
        tasks.transition(id,"WAITING_APPROVAL","RUNNING");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger calls=new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean first=calls.getAndIncrement()==0;
            Map<String,Object> message=first?Map.of("role","assistant","tool_calls",List.of(Map.of("id","call-1","type","function",
                    "function",Map.of("name","writeArtifact","arguments",json.writeValueAsString(Map.of("path","main.py","content","print('research')\n"))))))
                    :Map.of("role","assistant","content","代码已生成，尚未运行。");
            byte[] data=json.writeValueAsBytes(Map.of("id","mock-completion","object","chat.completion","created",1,"model","mock",
                    "choices",List.of(Map.of("index",0,"message",message,"finish_reason",first?"tool_calls":"stop")),
                    "usage",Map.of("prompt_tokens",10,"completion_tokens",10,"total_tokens",20)));
            exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,data.length);
            exchange.getResponseBody().write(data); exchange.close();
        });
        server.start();
        try {
            var engine=new ResearchAgentEngine(workspaces,tasks,memory,knowledge,jdbc,"test-key","http://127.0.0.1:"+server.getAddress().getPort()+"/v1","mock","live");
            engine.execute(tasks.get(id));
            assertThat(tasks.get(id).status()).isEqualTo("SUCCEEDED");
            assertThat(Files.readString(Path.of(tasks.get(id).outputPath()).resolve("main.py"))).contains("print('research')");
            assertThat(calls.get()).isEqualTo(2);
        } finally { server.stop(0); }
    }
}
