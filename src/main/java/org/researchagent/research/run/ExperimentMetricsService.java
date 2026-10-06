package org.researchagent.research.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads bounded numeric metrics emitted by an approved run. No model claims are inferred. */
@Service
public class ExperimentMetricsService {
    public record Point(double step, double value) { }
    public record Series(String name, List<Point> points, double first, double last, double best, double delta) { }
    public record Report(String status, String source, List<Series> series, String summary) { }
    private static final int MAX_BYTES = 1_048_576;
    private static final int MAX_ROWS = 1000;
    private static final int MAX_SERIES = 8;
    private final RunService runs;
    private final ObjectMapper json;
    public ExperimentMetricsService(RunService runs, ObjectMapper json) { this.runs = runs; this.json = json; }

    public Report report(long runId) throws IOException {
        RunView run = runs.get(runId);
        if (run.startedAt() == null) return new Report(run.status(), "", List.of(), "实验尚未开始。");
        Path root = Path.of(run.workingDirectory());
        Path file = null;
        for (String name : List.of("metrics.jsonl", "metrics.csv")) {
            Path candidate = root.resolve(name);
            if (Files.isRegularFile(candidate) && !Files.isSymbolicLink(candidate) &&
                    !Files.getLastModifiedTime(candidate).toInstant().isBefore(
                            run.startedAt().atZone(ZoneId.systemDefault()).toInstant().minusSeconds(1))) {
                file = candidate;
                break;
            }
        }
        if (file == null)
            return new Report(run.status(), "", List.of(), "尚未发现本次运行写出的 metrics.jsonl 或 metrics.csv。");
        if (Files.size(file) > MAX_BYTES) throw new BusinessException(ErrorCode.PARAMS_ERROR, "指标文件超过 1 MB");
        Map<String,List<Point>> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        boolean csv = file.getFileName().toString().endsWith(".csv");
        String[] headers = csv && !lines.isEmpty() ? lines.getFirst().split(",", -1) : new String[0];
        int first = csv ? 1 : 0;
        for (int i = first; i < lines.size() && i - first < MAX_ROWS; i++) {
            try {
                Map<String,Double> row = csv ? csvRow(headers, lines.get(i)) : jsonRow(lines.get(i));
                double step = row.getOrDefault("step", row.getOrDefault("epoch", (double) (i - first)));
                if (!Double.isFinite(step)) continue;
                for (var entry : row.entrySet()) {
                    if (entry.getKey().equals("step") || entry.getKey().equals("epoch") || !Double.isFinite(entry.getValue())) continue;
                    if (!values.containsKey(entry.getKey()) && values.size() >= MAX_SERIES) continue;
                    values.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(new Point(step, entry.getValue()));
                }
            } catch (Exception ignored) { /* Ignore an incomplete line while the process is still writing. */ }
        }
        List<Series> series = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        for (var entry : values.entrySet()) {
            List<Point> points = entry.getValue();
            if (points.isEmpty()) continue;
            double firstValue = points.getFirst().value();
            double last = points.getLast().value();
            double best = entry.getKey().toLowerCase(Locale.ROOT).matches(".*(loss|error|mae|rmse).*")
                    ? points.stream().mapToDouble(Point::value).min().orElse(last)
                    : points.stream().mapToDouble(Point::value).max().orElse(last);
            double delta = last - firstValue;
            series.add(new Series(entry.getKey(), points, firstValue, last, best, delta));
            statements.add(entry.getKey() + "：起始 " + fmt(firstValue) + "，最新 " + fmt(last)
                    + "，变化 " + fmt(delta) + "，观测最优 " + fmt(best) + "。");
        }
        String summary = series.isEmpty() ? "指标文件中没有可读取的数值列。" :
                "基于本次运行已记录的 " + Math.min(MAX_ROWS, Math.max(0, lines.size() - first))
                        + " 行数据；仅描述观测值，不推断模型有效性。\n" + String.join("\n", statements);
        return new Report(run.status(), file.getFileName().toString(), series, summary);
    }

    private Map<String,Double> jsonRow(String line) throws IOException {
        JsonNode node = json.readTree(line);
        Map<String,Double> row = new LinkedHashMap<>();
        if (!node.isObject()) return row;
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,39}") && entry.getValue().isNumber())
                row.put(entry.getKey(), entry.getValue().doubleValue());
        }
        return row;
    }
    private static Map<String,Double> csvRow(String[] headers, String line) {
        String[] cells = line.split(",", -1);
        Map<String,Double> row = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(headers.length, cells.length); i++) {
            String key = headers[i].strip();
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{0,39}")) continue;
            try { row.put(key, Double.parseDouble(cells[i].strip())); } catch (NumberFormatException ignored) { }
        }
        return row;
    }
    private static String fmt(double number) { return String.format(Locale.ROOT, "%.4f", number); }

    public String svg(long runId, String metric) throws IOException {
        Series series = report(runId).series().stream().filter(item -> item.name().equals(metric)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "指标不存在"));
        double xMin = series.points().stream().mapToDouble(Point::step).min().orElse(0);
        double xMax = series.points().stream().mapToDouble(Point::step).max().orElse(1);
        double yMin = series.points().stream().mapToDouble(Point::value).min().orElse(0);
        double yMax = series.points().stream().mapToDouble(Point::value).max().orElse(1);
        StringBuilder points = new StringBuilder();
        for (Point point : series.points()) {
            double x = 70 + 670 * (point.step() - xMin) / Math.max(0.000001, xMax - xMin);
            double y = 320 - 260 * (point.value() - yMin) / Math.max(0.000001, yMax - yMin);
            points.append(fmt(x)).append(',').append(fmt(y)).append(' ');
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"400\" viewBox=\"0 0 800 400\">"
                + "<rect width=\"800\" height=\"400\" fill=\"white\"/><text x=\"70\" y=\"32\" font-size=\"20\" fill=\"#172554\">"
                + series.name() + "</text><path d=\"M70 60 V320 H740\" fill=\"none\" stroke=\"#64748b\"/>"
                + "<polyline points=\"" + points + "\" fill=\"none\" stroke=\"#2563eb\" stroke-width=\"3\"/>"
                + "<text x=\"70\" y=\"355\" font-size=\"13\">step " + fmt(xMin) + "–" + fmt(xMax) + "</text>"
                + "<text x=\"570\" y=\"355\" font-size=\"13\">value " + fmt(yMin) + "–" + fmt(yMax) + "</text></svg>";
    }
}
