package org.researchagent.research.knowledge;

import jakarta.annotation.PostConstruct;
import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Durable project-scoped chunk retrieval. BM25 runs locally without an embedding API or external database. */
@Service
public class KnowledgeService {
    public record Document(long id, String source, String content) { }
    public record Hit(long id, long chunkId, String source, String excerpt, double score) { }
    private record Chunk(long id, long documentId, String source, String content) { }
    private record Ranked(Chunk chunk, double score) { }
    private static final Pattern WORD = Pattern.compile("[a-z0-9_+#]{2,}|[\\p{IsHan}]{2,}");
    private static final int CHUNK_SIZE = 900;
    private static final int OVERLAP = 150;
    private final JdbcTemplate jdbc;

    public KnowledgeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void indexExistingDocuments() {
        var old = jdbc.query("select d.id,d.project_id,d.content from knowledge_document d "
                        + "where not exists(select 1 from knowledge_chunk c where c.document_id=d.id)",
                (r,n) -> new Object[]{r.getLong("id"), r.getLong("project_id"), r.getString("content")});
        for (Object[] row : old) index((long) row[1], (long) row[0], (String) row[2]);
    }

    @Transactional
    public Document add(long projectId, String source, String content) {
        if (source == null || source.isBlank() || source.length() > 240 ||
                content == null || content.isBlank() || content.length() > 120_000)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "来源或正文无效，正文最多 120000 字符");
        String name = source.strip();
        String text = content.strip();
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into knowledge_document(project_id,source,content) values(?,?,?)", new String[]{"id"});
            statement.setLong(1, projectId); statement.setString(2, name); statement.setString(3, text);
            return statement;
        }, key);
        long id = key.getKey().longValue();
        index(projectId, id, text);
        return new Document(id, name, text);
    }

    private void index(long projectId, long documentId, String text) {
        int index = 0;
        for (int start = 0; start < text.length(); start += CHUNK_SIZE - OVERLAP) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            jdbc.update("insert into knowledge_chunk(project_id,document_id,chunk_index,content) values(?,?,?,?)",
                    projectId, documentId, index++, text.substring(start, end));
            if (end == text.length()) break;
        }
    }

    public List<Document> list(long projectId) {
        return jdbc.query("select id,source,content from knowledge_document where project_id=? order by id desc limit 100",
                (r,n) -> new Document(r.getLong("id"), r.getString("source"), r.getString("content")), projectId);
    }

    @Transactional
    public void delete(long projectId, long documentId) {
        jdbc.update("delete from knowledge_chunk where project_id=? and document_id=?", projectId, documentId);
        if (jdbc.update("delete from knowledge_document where project_id=? and id=?", projectId, documentId) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库文档不存在");
    }

    public List<Hit> search(long projectId, String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        Set<String> terms = terms(query.toLowerCase(Locale.ROOT));
        if (terms.isEmpty()) return List.of();
        List<Chunk> chunks = jdbc.query("select c.id,c.document_id,d.source,c.content from knowledge_chunk c "
                        + "join knowledge_document d on d.id=c.document_id where c.project_id=? order by c.id limit 10000",
                (r,n) -> new Chunk(r.getLong("id"), r.getLong("document_id"), r.getString("source"), r.getString("content")), projectId);
        if (chunks.isEmpty()) return List.of();
        Map<String,Integer> frequency = new HashMap<>();
        int totalLength = 0;
        for (Chunk chunk : chunks) {
            String lower = chunk.content().toLowerCase(Locale.ROOT);
            totalLength += Math.max(1, lower.length());
            for (String term : terms) if (lower.contains(term) || chunk.source().toLowerCase(Locale.ROOT).contains(term))
                frequency.merge(term, 1, Integer::sum);
        }
        double average = (double) totalLength / chunks.size();
        List<Ranked> ranked = new ArrayList<>();
        for (Chunk chunk : chunks) {
            String content = chunk.content().toLowerCase(Locale.ROOT);
            String source = chunk.source().toLowerCase(Locale.ROOT);
            double score = 0;
            for (String term : terms) {
                int tf = count(content, term);
                if (tf == 0 && source.contains(term)) tf = 1;
                if (tf == 0) continue;
                int df = frequency.getOrDefault(term, 0);
                double idf = Math.log(1 + (chunks.size() - df + 0.5) / (df + 0.5));
                score += idf * tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * chunk.content().length() / average));
                if (source.contains(term)) score += 0.2;
            }
            if (score > 0) ranked.add(new Ranked(chunk, score));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparingLong(r -> r.chunk().id()));
        List<Hit> result = new ArrayList<>();
        Map<Long,Integer> selectedPerDocument = new HashMap<>();
        for (Ranked item : ranked) {
            long documentId = item.chunk().documentId();
            if (selectedPerDocument.getOrDefault(documentId, 0) >= 2) continue;
            selectedPerDocument.merge(documentId, 1, Integer::sum);
            result.add(new Hit(item.chunk().documentId(), item.chunk().id(), item.chunk().source(),
                    item.chunk().content(), Math.round(item.score() * 1000.0) / 1000.0));
            if (result.size() >= Math.max(1, Math.min(limit, 10))) break;
        }
        return result;
    }

    private static int count(String text, String term) {
        int result = 0, at = 0;
        while ((at = text.indexOf(term, at)) >= 0) { result++; at += term.length(); }
        return result;
    }

    private static Set<String> terms(String query) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(query);
        while (matcher.find()) {
            String part = matcher.group();
            if (part.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                for (int at = 0; at + 2 <= part.length(); at++) found.add(part.substring(at, at + 2));
            } else found.add(part);
        }
        return found;
    }
}
