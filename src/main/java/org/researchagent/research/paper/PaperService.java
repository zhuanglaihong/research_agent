package org.researchagent.research.paper;

import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.researchagent.research.knowledge.KnowledgeService;
import org.researchagent.research.project.ResearchProjectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PaperService {
    public record Subscription(String topic, boolean enabled, LocalDateTime lastChecked, String lastError) { }
    public record Paper(long id, String arxivId, String title, String abstractText, String url, LocalDateTime publishedAt) { }
    private record Incoming(String arxivId, String title, String abstractText, String url, LocalDateTime publishedAt) { }
    private static final Pattern TOPIC = Pattern.compile("[a-zA-Z0-9 ._+-]{2,100}");
    private static final String ATOM = "http://www.w3.org/2005/Atom";
    private final JdbcTemplate jdbc;
    private final ResearchProjectService projects;
    private final KnowledgeService knowledge;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private long lastRequestMillis;

    public PaperService(JdbcTemplate jdbc, ResearchProjectService projects, KnowledgeService knowledge) {
        this.jdbc = jdbc; this.projects = projects; this.knowledge = knowledge;
    }
    private void owned(long projectId) { projects.getOwnedProject(1, projectId); }

    public Subscription subscription(long projectId) {
        owned(projectId);
        List<Subscription> rows = jdbc.query("select * from paper_subscription where project_id=?", (r,n) ->
                new Subscription(r.getString("topic"), r.getBoolean("enabled"),
                        r.getTimestamp("last_checked") == null ? null : r.getTimestamp("last_checked").toLocalDateTime(),
                        r.getString("last_error")), projectId);
        return rows.isEmpty() ? new Subscription("", false, null, null) : rows.getFirst();
    }

    public Subscription save(long projectId, String topic, boolean enabled) {
        owned(projectId);
        String value = topic == null ? "" : topic.trim();
        if (!TOPIC.matcher(value).matches()) throw new BusinessException(ErrorCode.PARAMS_ERROR,
                "主题请输入 2–100 个英文关键词、数字或空格");
        jdbc.update("merge into paper_subscription(project_id,topic,enabled) key(project_id) values(?,?,?)", projectId, value, enabled);
        return subscription(projectId);
    }

    public List<Paper> list(long projectId) {
        owned(projectId);
        RowMapper<Paper> mapper = (r,n) -> new Paper(r.getLong("id"), r.getString("arxiv_id"), r.getString("title"),
                r.getString("abstract_text"), r.getString("url"),
                r.getTimestamp("published_at") == null ? null : r.getTimestamp("published_at").toLocalDateTime());
        return jdbc.query("select * from paper_item where project_id=? order by published_at desc, id desc limit 100", mapper, projectId);
    }

    public synchronized int sync(long projectId) {
        Subscription config = subscription(projectId);
        if (config.topic().isBlank()) throw new BusinessException(ErrorCode.OPERATION_ERROR, "请先设置论文主题");
        try {
            long delay = 3000 - (System.currentTimeMillis() - lastRequestMillis);
            if (delay > 0) Thread.sleep(delay);
            String query = URLEncoder.encode("all:\"" + config.topic() + "\"", StandardCharsets.UTF_8);
            URI uri = URI.create("https://export.arxiv.org/api/query?search_query=" + query
                    + "&start=0&max_results=5&sortBy=submittedDate&sortOrder=descending");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "research_agent/0.1 (personal research assistant)").GET().build();
            lastRequestMillis = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IllegalStateException("论文源返回 HTTP " + response.statusCode());
            int added = 0;
            for (Incoming paper : parse(response.body())) {
                int count = jdbc.update("insert into paper_item(project_id,arxiv_id,title,abstract_text,url,published_at) "
                        + "select ?,?,?,?,?,? where not exists(select 1 from paper_item where project_id=? and arxiv_id=?)",
                        projectId, paper.arxivId(), paper.title(), paper.abstractText(), paper.url(),
                        paper.publishedAt() == null ? null : Timestamp.valueOf(paper.publishedAt()), projectId, paper.arxivId());
                if (count == 1) {
                    String source = "arXiv: " + paper.title() + " (" + paper.url() + ")";
                    knowledge.add(projectId, source.substring(0, Math.min(240, source.length())),
                            paper.abstractText().substring(0, Math.min(20_000, paper.abstractText().length())));
                    added++;
                }
            }
            jdbc.update("update paper_subscription set last_checked=current_timestamp,last_error=null where project_id=?", projectId);
            return added;
        } catch (Exception e) {
            jdbc.update("update paper_subscription set last_checked=current_timestamp,last_error=? where project_id=?",
                    e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).substring(0, Math.min(350, String.valueOf(e.getMessage()).length())), projectId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "论文同步失败，请稍后重试或检查网络");
        }
    }

    static List<Incoming> parse(String atom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(atom)));
        NodeList entries = document.getElementsByTagNameNS(ATOM, "entry");
        var result = new java.util.ArrayList<Incoming>();
        for (int i = 0; i < Math.min(entries.getLength(), 5); i++) {
            Element entry = (Element) entries.item(i);
            String url = field(entry, "id");
            if (!url.matches("https?://(export\\.)?arxiv\\.org/abs/[a-zA-Z0-9.\\-/]+")) continue;
            String id = url.substring(url.lastIndexOf('/') + 1).replaceFirst("v[0-9]+$", "");
            String title = field(entry, "title").replaceAll("\\s+", " ").trim();
            String summary = field(entry, "summary").replaceAll("\\s+", " ").trim();
            if (title.isBlank() || summary.isBlank()) continue;
            LocalDateTime published = null;
            try { published = OffsetDateTime.parse(field(entry, "published")).toLocalDateTime(); }
            catch (Exception ignored) { }
            result.add(new Incoming(id, title, summary, url.replace("http://", "https://"), published));
        }
        return result;
    }
    private static String field(Element entry, String tag) {
        NodeList values = entry.getElementsByTagNameNS(ATOM, tag);
        return values.getLength() == 0 ? "" : values.item(0).getTextContent();
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void daily() {
        List<Long> projects = jdbc.queryForList("select project_id from paper_subscription where enabled=true order by project_id", Long.class);
        for (Long projectId : projects) {
            try { sync(projectId); }
            catch (Exception ignored) { /* Last error is stored per subscription. */ }
        }
    }
}
