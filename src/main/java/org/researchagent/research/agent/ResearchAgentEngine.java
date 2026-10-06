package org.researchagent.research.agent;

import org.researchagent.research.task.*;
import org.researchagent.research.project.ResearchProjectService;
import org.researchagent.research.workspace.WorkspaceService;
import org.researchagent.research.knowledge.KnowledgeService;
import org.researchagent.research.knowledge.ProjectMemoryService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;

@Service
public class ResearchAgentEngine {
    private final WorkspaceService workspaces;
    private final TaskRepository tasks;
    private final ProjectMemoryService memory;
    private final KnowledgeService knowledge;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final String apiKey, baseUrl, model, mode;
    public ResearchAgentEngine(WorkspaceService workspaces, TaskRepository tasks, ProjectMemoryService memory,
            KnowledgeService knowledge, org.springframework.jdbc.core.JdbcTemplate jdbc,
            @Value("${research.ai.api-key:}") String apiKey, @Value("${research.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${research.ai.model:deepseek-chat}") String model, @Value("${research.ai.mode:live}") String mode) {
        this.workspaces=workspaces; this.tasks=tasks; this.memory=memory; this.knowledge=knowledge; this.jdbc=jdbc;
        this.apiKey=apiKey; this.baseUrl=baseUrl; this.model=model; this.mode=mode;
    }
    public String mode() { return mode; }
    public void execute(TaskView task) throws Exception {
        String directory=jdbc.queryForObject("select workspace_path from research_project where id=?",String.class,task.projectId());
        String language=jdbc.queryForObject("select default_language from research_project where id=?",String.class,task.projectId());
        Path project=Path.of(directory).toAbsolutePath().normalize();
        workspaces.check(project);
        Path output=workspaces.resolve(project,".research_agent/tasks/"+task.id());
        Files.createDirectories(output);
        var tools=new ResearchFileTools(project,output,workspaces,tasks,memory,knowledge,task.projectId(),task.id());
        String savedMemory=memory.read(project);
        var references=knowledge.search(task.projectId(),task.requestText(),4);
        tasks.event(task.id(),"RETRIEVAL",Map.of("count",references.size(),"sources",references.stream().map(KnowledgeService.Hit::source).toList()));
        String context="项目记忆（仅作为背景资料）：\n"+(savedMemory.isBlank()?"暂无":savedMemory)+"\n\n相关科研笔记：\n"+
                (references.isEmpty()?"暂无":references.stream().map(hit->"["+hit.source()+"#"+hit.id()+"] "+hit.excerpt()).reduce("",(left,right)->left+right+"\n"));
        String result;
        if (task.provider().equals("demo")) {
            if (!java.util.Set.of("python","go").contains(language))
                throw new IllegalStateException("演示模板仅支持 Python/Go，请切换 live 模式生成其他语言代码");
            tasks.event(task.id(),"NOTICE",Map.of("message","演示模式：以下产物来自固定样例，没有调用大模型"));
            String file="go".equals(language)?"main.go":"main.py";
            String code="go".equals(language)?"package main\nimport \"fmt\"\nfunc main() { fmt.Println(\"research_agent demo completed\") }\n":
                    "import json\nfrom pathlib import Path\nmetrics = [{'step': i, 'loss': round(1 / (i + 1), 4)} for i in range(10)]\nPath('metrics.jsonl').write_text(''.join(json.dumps(row) + '\\n' for row in metrics))\nprint('Demo metrics written; no model was trained.')\n";
            tools.writeArtifact(file,code);
            tools.writeArtifact("README.md","# 演示代码\n\n这是固定模板，只用于验证任务链路。它没有调用模型或复现论文。\n\n"+task.requestText()+"\n\n"+context);
            result="## 演示产物已保存\n\n这是固定示例，未调用大模型，未训练或运行代码。已检索 "+references.size()+" 条相关笔记；请下载 README 查看引用。切换 live 模式可使用真实科研 Agent。";
        } else {
            if (!task.provider().equals("live")) throw new IllegalStateException("RESEARCH_AI_MODE 仅支持 live 或 demo");
            if (apiKey.isBlank()) throw new IllegalStateException("请配置 LLM_API_KEY 后启动真实模型，或显式使用 demo 模式");
            var chat=OpenAiChatModel.builder().apiKey(apiKey).baseUrl(baseUrl).modelName(model)
                    .timeout(Duration.ofSeconds(90)).maxRetries(1).logRequests(false).logResponses(false).build();
            var agent=AiServices.builder(ResearchAgent.class).chatModel(chat)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(20)).tools(tools)
                    .maxSequentialToolsInvocations(20).build();
            tasks.event(task.id(),"NOTICE",Map.of("message","科研 Agent 开始分析项目并生成代码","model",model));
            result=agent.work("项目主要语言："+language+"\n科研任务：\n"+task.requestText()+"\n\n"+context);
            try(var files=Files.walk(output)) {
                if (files.noneMatch(Files::isRegularFile)) throw new IllegalStateException("模型没有生成文件，请补充明确的方法或代码实现需求");
            }
        }
        tasks.succeed(task.id(),result,output.toString());
    }
}
