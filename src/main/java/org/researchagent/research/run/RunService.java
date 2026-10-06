package org.researchagent.research.run;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.researchagent.research.project.ResearchProjectService;
import org.researchagent.research.task.TaskRepository;
import org.researchagent.research.workspace.WorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/** Explicitly approved local Python runs. Never constructs a shell command from model output. */
@Service
public class RunService {
    private static final long MAX_LOG_BYTES = 1_048_576;
    private static final Duration MAX_RUNTIME = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final TaskRepository tasks;
    private final ResearchProjectService projects;
    private final WorkspaceService workspaces;
    private final boolean enabled;
    private final String python;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Semaphore slot = new Semaphore(1);
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();
    private final RowMapper<RunView> mapper = (r, n) -> new RunView(r.getLong("id"), r.getLong("task_id"),
            r.getString("command_json"), r.getString("working_directory"), r.getString("status"), (Integer) r.getObject("exit_code"),
            r.getTimestamp("created_at").toLocalDateTime(), timestamp(r.getTimestamp("started_at")),
            timestamp(r.getTimestamp("finished_at")));

    public RunService(JdbcTemplate jdbc, TaskRepository tasks, ResearchProjectService projects,
            WorkspaceService workspaces,
            @Value("${research.runner.enabled:false}") boolean enabled,
            @Value("${research.runner.python-executable:python}") String python) {
        this.jdbc = jdbc; this.tasks = tasks; this.projects = projects; this.workspaces = workspaces;
        this.enabled = enabled; this.python = python;
    }

    private static java.time.LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public boolean enabled() { return enabled; }
    public String pythonExecutable() { return python; }

    private Path output(long taskId) throws IOException {
        var task = tasks.owned(taskId, 1);
        var project = projects.getOwnedProject(1, task.projectId());
        return workspaces.resolve(Path.of(project.workspacePath()), ".research_agent/tasks/" + taskId);
    }

    public RunView create(long taskId, String scriptPath) throws IOException {
        if (!enabled) throw new BusinessException(ErrorCode.OPERATION_ERROR, "本地实验运行未启用；请配置 RESEARCH_RUNNER_ENABLED=true");
        if (!"SUCCEEDED".equals(tasks.owned(taskId, 1).status()))
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "代码任务完成后才能运行实验");
        if (scriptPath == null || !scriptPath.matches("[a-zA-Z0-9_./-]{1,200}\\.py") || scriptPath.startsWith("/"))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择任务产物中的 Python 脚本");
        Path script = workspaces.resolve(output(taskId), scriptPath);
        if (!Files.isRegularFile(script) || Files.size(script) > 262_144)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "脚本不存在或超过 256 KB");
        var keys = new GeneratedKeyHolder();
        String workingDirectory = output(taskId).toString();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into experiment_run(task_id,run_key,runtime_language,working_directory,command_json,status) values(?,?,?,?,?,?)", new String[]{"id"});
            statement.setLong(1, taskId); statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, "python"); statement.setString(4, workingDirectory);
            statement.setString(5, scriptPath); statement.setString(6, "WAITING_APPROVAL");
            return statement;
        }, keys);
        return get(keys.getKey().longValue());
    }

    public RunView get(long id) {
        List<RunView> rows = jdbc.query("select * from experiment_run where id=?", mapper, id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "实验记录不存在");
        tasks.owned(rows.getFirst().taskId(), 1);
        return rows.getFirst();
    }

    public List<RunView> list(long taskId) {
        tasks.owned(taskId, 1);
        return jdbc.query("select * from experiment_run where task_id=? order by id desc limit 30", mapper, taskId);
    }

    public RunView approve(long id) {
        get(id);
        if (jdbc.update("update experiment_run set status='QUEUED' where id=? and status='WAITING_APPROVAL'", id) != 1)
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "实验已提交或结束");
        return get(id);
    }

    public RunView cancel(long id) {
        get(id);
        if (jdbc.update("update experiment_run set status='CANCELED',finished_at=current_timestamp where id=? and status in ('WAITING_APPROVAL','QUEUED','RUNNING')", id) != 1)
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "实验已经结束");
        Process process = processes.get(id);
        if (process != null) stop(process);
        return get(id);
    }

    public Map<String, String> logs(long id) throws IOException {
        RunView run = get(id);
        Path directory = workspaces.resolve(output(run.taskId()), ".research_agent/runs/" + id);
        return Map.of("stdout", tail(workspaces.resolve(directory, "stdout.log")),
                "stderr", tail(workspaces.resolve(directory, "stderr.log")));
    }

    private String tail(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return "";
        byte[] bytes = Files.readAllBytes(file);
        int start = Math.max(0, bytes.length - 65_536);
        return new String(bytes, start, bytes.length - start, java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostConstruct
    public void recover() {
        jdbc.update("update experiment_run set status='INTERRUPTED',finished_at=current_timestamp where status='RUNNING'");
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        if (!enabled || !slot.tryAcquire()) return;
        List<RunView> queued = jdbc.query("select * from experiment_run where status='QUEUED' order by id limit 1", mapper);
        if (queued.isEmpty()) { slot.release(); return; }
        RunView run = queued.getFirst();
        if (jdbc.update("update experiment_run set status='RUNNING',started_at=current_timestamp where id=? and status='QUEUED'", run.id()) != 1) {
            slot.release(); return;
        }
        executor.submit(() -> {
            try { execute(run); }
            finally { slot.release(); }
        });
    }

    private void execute(RunView run) {
        Process process = null;
        try {
            Path output = output(run.taskId());
            Path script = workspaces.resolve(output, run.scriptPath());
            if (!Files.isRegularFile(script)) throw new IOException("脚本不存在");
            Path directory = workspaces.resolve(output, ".research_agent/runs/" + run.id());
            Files.createDirectories(directory);
            Path stdout = workspaces.resolve(directory, "stdout.log");
            Path stderr = workspaces.resolve(directory, "stderr.log");
            var builder = new ProcessBuilder(python, script.toString()).directory(output.toFile())
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            Map<String, String> environment = builder.environment();
            String path = environment.getOrDefault("PATH", environment.getOrDefault("Path", ""));
            String systemRoot = environment.getOrDefault("SystemRoot", "");
            String temp = environment.getOrDefault("TEMP", environment.getOrDefault("TMP", ""));
            environment.clear();
            environment.put("PATH", path);
            if (!systemRoot.isBlank()) environment.put("SystemRoot", systemRoot);
            if (!temp.isBlank()) environment.put("TEMP", temp);
            environment.put("PYTHONNOUSERSITE", "1");
            Path config = workspaces.resolve(directory, "config");
            Files.createDirectories(config);
            environment.put("MPLCONFIGDIR", config.toString());
            environment.put("USERPROFILE", config.toString());
            environment.put("HOME", config.toString());
            environment.put("PYTHONUNBUFFERED", "1");
            process = builder.start();
            processes.put(run.id(), process);
            process.getOutputStream().close();
            Instant deadline = Instant.now().plus(MAX_RUNTIME);
            while (process.isAlive()) {
                if (!"RUNNING".equals(get(run.id()).status()) || Instant.now().isAfter(deadline)
                        || Files.size(stdout) + Files.size(stderr) > MAX_LOG_BYTES) {
                    stop(process);
                    break;
                }
                process.waitFor(250, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            int exit = process.waitFor();
            jdbc.update("update experiment_run set status=?,exit_code=?,finished_at=current_timestamp,stdout_path=?,stderr_path=? where id=? and status='RUNNING'",
                    exit == 0 ? "SUCCEEDED" : "FAILED", exit, stdout.toString(), stderr.toString(), run.id());
        } catch (Exception e) {
            if (process != null) stop(process);
            jdbc.update("update experiment_run set status='FAILED',finished_at=current_timestamp where id=? and status='RUNNING'", run.id());
        } finally {
            processes.remove(run.id());
        }
    }

    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    @PreDestroy
    public void close() {
        processes.values().forEach(RunService::stop);
        executor.shutdownNow();
    }
}
