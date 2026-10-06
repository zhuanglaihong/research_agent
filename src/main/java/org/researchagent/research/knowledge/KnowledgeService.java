package org.researchagent.research.knowledge;

import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.PreparedStatement;

/** Local retrieval over user-supplied paper notes. No external model is needed in demo mode. */
@Service
public class KnowledgeService {
    public record Document(long id, String source, String content) { }
    public record Hit(long id, String source, String excerpt, int score) { }

    private static final Pattern WORD = Pattern.compile("[a-z0-9_+#]{2,}|[\\p{IsHan}]{2,}");
    private final JdbcTemplate jdbc;

    public KnowledgeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Document add(long projectId, String source, String content) {
        if (source == null || source.isBlank() || source.length() > 240 ||
                content == null || content.isBlank() || content.length() > 20_000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "来源或笔记内容无效，正文最多 20000 字符");
        }
        String name = source.strip();
        String text = content.strip();
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into knowledge_document(project_id,source,content) values(?,?,?)", new String[]{"id"});
            statement.setLong(1, projectId);
            statement.setString(2, name);
            statement.setString(3, text);
            return statement;
        }, key);
        return new Document(key.getKey().longValue(), name, text);
    }

    public List<Document> list(long projectId) {
        return jdbc.query("select id,source,content from knowledge_document where project_id=? order by id desc limit 100",
                (row, n) -> new Document(row.getLong("id"), row.getString("source"), row.getString("content")), projectId);
    }

    public List<Hit> search(long projectId, String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        Set<String> terms = terms(query.toLowerCase(Locale.ROOT));
        if (terms.isEmpty()) return List.of();
        List<Hit> found = new ArrayList<>();
        for (Document document : list(projectId)) {
            String searchable = (document.source() + " " + document.content()).toLowerCase(Locale.ROOT);
            int score = 0;
            int first = -1;
            for (String term : terms) {
                int at = searchable.indexOf(term);
                if (at >= 0) {
                    score += term.length();
                    if (first < 0 || at < first) first = at;
                }
            }
            if (score > 0) {
                int contentAt = Math.max(0, first - document.source().length() - 1);
                int start = Math.max(0, contentAt - 80);
                int end = Math.min(document.content().length(), start + 500);
                found.add(new Hit(document.id(), document.source(), document.content().substring(start, end), score));
            }
        }
        found.sort(Comparator.comparingInt(Hit::score).reversed().thenComparingLong(Hit::id));
        return found.stream().limit(Math.max(1, Math.min(limit, 10))).toList();
    }

    private static Set<String> terms(String query) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(query);
        while (matcher.find()) {
            String part = matcher.group();
            if (part.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                for (int at = 0; at + 2 <= part.length(); at++) found.add(part.substring(at, at + 2));
            } else {
                found.add(part);
            }
        }
        return found;
    }
}
