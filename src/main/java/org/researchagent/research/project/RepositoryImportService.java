package org.researchagent.research.project;

import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.researchagent.research.workspace.WorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class RepositoryImportService {
    private static final Pattern REPOSITORY_PATH = Pattern.compile("/[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}(?:\\.git)?/?");
    private final JdbcTemplate jdbc;
    private final ResearchProjectService projects;
    private final WorkspaceService workspaces;
    private final String git;

    public RepositoryImportService(JdbcTemplate jdbc, ResearchProjectService projects, WorkspaceService workspaces,
                                   @Value("${research.repository.git-executable:git}") String git) {
        this.jdbc = jdbc; this.projects = projects; this.workspaces = workspaces; this.git = git;
    }

    public List<ImportedRepository> list(long projectId) {
        projects.getOwnedProject(1, projectId);
        return jdbc.query("select * from imported_repository where project_id=? order by imported_at desc limit 50",
                (r,n) -> new ImportedRepository(r.getLong("id"), r.getString("repository_url"), r.getString("repository_name"),
                        r.getString("local_path"), r.getString("commit_hash"), r.getTimestamp("imported_at").toLocalDateTime()), projectId);
    }

    @Transactional
    public ImportedRepository importGithub(long projectId, String rawUrl) throws IOException, InterruptedException {
        var project = projects.getOwnedProject(1, projectId);
        String url = normalizeGithubUrl(rawUrl);
        String name = url.substring(url.lastIndexOf('/') + 1).replaceFirst("(?i)\\.git$", "");
        Path projectRoot = Path.of(project.workspacePath());
        Path parent = workspaces.resolve(projectRoot, "repositories");
        Files.createDirectories(parent);
        Path destination = workspaces.resolve(parent, name);
        if (Files.exists(destination)) throw new BusinessException(ErrorCode.OPERATION_ERROR, "仓库目录已存在；请先在项目工作区移除或改名后再导入");
        Files.createDirectories(destination.getParent());
        // Clone into an empty, unique staging directory so failure cannot leave a half-imported target.
        Path staging = parent.resolve(".import-" + java.util.UUID.randomUUID());
        try {
            Result clone = run(List.of(git, "-c", "credential.helper=", "clone", "--depth", "1", "--", url, staging.toString()), parent, Duration.ofMinutes(2));
            if (clone.exitCode != 0) throw new BusinessException(ErrorCode.OPERATION_ERROR, "GitHub 仓库下载失败：" + safeOutput(clone.output));
            Result head = run(List.of(git, "-C", staging.toString(), "rev-parse", "HEAD"), parent, Duration.ofSeconds(15));
            if (head.exitCode != 0) throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法读取导入仓库的提交版本");
            String commit = head.output.strip();
            workspaces.check(staging);
            Files.move(staging, destination);
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("insert into imported_repository(project_id,repository_url,repository_name,local_path,commit_hash) values(?,?,?,?,?)", new String[]{"id"});
                statement.setLong(1, projectId); statement.setString(2, url); statement.setString(3, name);
                statement.setString(4, projectRoot.relativize(destination).toString().replace('\\','/')); statement.setString(5, commit); return statement;
            }, key);
            return list(projectId).stream().filter(item -> item.id() == key.getKey().longValue()).findFirst().orElseThrow();
        } catch (IOException | InterruptedException | RuntimeException error) {
            deleteTree(staging);
            deleteTree(destination);
            throw error;
        }
    }

    static String normalizeGithubUrl(String raw) {
        try {
            var uri = java.net.URI.create(raw == null ? "" : raw.strip());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String[] segments = uri.getPath() == null ? new String[0] : uri.getPath().replaceFirst("/$", "").split("/");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !(host.equals("github.com") || host.equals("www.github.com"))
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                    || !REPOSITORY_PATH.matcher(uri.getPath()).matches() || segments.length != 3
                    || segments[1].equals(".") || segments[1].equals("..") || segments[2].equals(".") || segments[2].equals("..")) {
                throw new IllegalArgumentException();
            }
            String path = uri.getPath().replaceFirst("/$", "");
            if (!path.toLowerCase(Locale.ROOT).endsWith(".git")) path += ".git";
            return "https://github.com" + path;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持格式为 https://github.com/owner/repository 的公开仓库");
        }
    }

    private record Result(int exitCode, String output) { }
    private static Result run(List<String> command, Path directory, Duration timeout) throws IOException, InterruptedException {
        Path stdout = Files.createTempFile(directory, "repo-command-", ".out");
        Path stderr = Files.createTempFile(directory, "repo-command-", ".err");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
            builder.environment().put("GIT_CONFIG_GLOBAL", System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null");
            Process process = builder.start();
            if (!process.waitFor(timeout.toSeconds(), java.util.concurrent.TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Git 操作超过时限（" + timeout.toMinutes() + " 分钟）");
            }
            String output = (Files.readString(stdout) + "\n" + Files.readString(stderr));
            return new Result(process.exitValue(), output.length() > 4000 ? output.substring(output.length() - 4000) : output);
        } finally { Files.deleteIfExists(stdout); Files.deleteIfExists(stderr); }
    }
    private static String safeOutput(String value) { return value.replaceAll("(?i)(https://)[^/@\\s]+@", "$1[redacted]@").strip(); }
    private static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }
}
