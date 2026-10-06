package org.researchagent.research.task;

import org.researchagent.common.*;
import org.researchagent.exception.*;
import org.researchagent.research.LocalWorkspace;
import org.researchagent.research.project.ResearchProjectService;
import org.researchagent.research.agent.ResearchAgentEngine;
import org.researchagent.research.workspace.WorkspaceService;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;

@RestController
public class TaskController {
    public record CreateTask(String prompt) { }
    private final TaskRepository tasks;
    private final TaskWorker worker;
    private final ResearchProjectService projects;
    private final ResearchAgentEngine engine;
    private final WorkspaceService workspaces;
    public TaskController(TaskRepository tasks, TaskWorker worker, ResearchProjectService projects,
                          ResearchAgentEngine engine, WorkspaceService workspaces) {
        this.tasks=tasks; this.worker=worker; this.projects=projects; this.engine=engine; this.workspaces=workspaces;
    }
    private long user() { return LocalWorkspace.ID; }
    @PostMapping("/projects/{projectId}/tasks") @Transactional
    public BaseResponse<TaskView> create(@PathVariable long projectId,@RequestBody CreateTask body,HttpServletRequest request) {
        long user=user(); projects.getOwnedProject(user,projectId);
        if(body==null || body.prompt()==null || body.prompt().isBlank() || body.prompt().length()>20000)
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请输入不超过 20000 字符的任务描述");
        return ResultUtils.success(tasks.get(tasks.create(projectId,user,body.prompt().trim(),engine.mode())));
    }
    @GetMapping("/projects/{projectId}/tasks")
    public BaseResponse<List<TaskView>> list(@PathVariable long projectId,HttpServletRequest request) {
        long user=user(); projects.getOwnedProject(user,projectId);
        return ResultUtils.success(tasks.list(projectId,user));
    }
    @GetMapping("/tasks/{id}") public BaseResponse<TaskView> get(@PathVariable long id,HttpServletRequest request) {
        return ResultUtils.success(tasks.owned(id,user()));
    }
    @PostMapping("/tasks/{id}/approve") @Transactional
    public BaseResponse<TaskView> approve(@PathVariable long id,HttpServletRequest request) {
        tasks.owned(id,user());
        if(!tasks.transition(id,"WAITING_APPROVAL","QUEUED")) throw new BusinessException(ErrorCode.OPERATION_ERROR,"任务已提交或已经结束");
        return ResultUtils.success(tasks.get(id));
    }
    @PostMapping("/tasks/{id}/cancel") public BaseResponse<TaskView> cancel(@PathVariable long id,HttpServletRequest request) {
        tasks.owned(id,user()); worker.cancel(id); return ResultUtils.success(tasks.get(id));
    }
    @GetMapping("/tasks/{id}/events") public BaseResponse<List<TaskEvent>> events(@PathVariable long id,@RequestParam(defaultValue="0") long after,HttpServletRequest request) {
        tasks.owned(id,user()); return ResultUtils.success(tasks.events(id,Math.max(after,0)));
    }
    @GetMapping(value="/tasks/{id}/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TaskEvent>> stream(@PathVariable long id,@RequestParam(defaultValue="0") long after,
                    @RequestHeader(value="Last-Event-ID",required=false) String lastId,HttpServletRequest request) {
        tasks.owned(id,user());
        long cursor=Math.max(after,0);
        if(lastId!=null) try { cursor=Math.max(cursor,Long.parseLong(lastId)); } catch(NumberFormatException ignored) { }
        AtomicLong next=new AtomicLong(cursor);
        return Flux.interval(Duration.ZERO,Duration.ofSeconds(1)).publishOn(Schedulers.boundedElastic())
                .concatMap(tick->Flux.fromIterable(tasks.events(id,next.get())))
                .doOnNext(event->next.set(event.id()))
                .takeUntil(event->Set.of("DONE","FAILED","CANCELED","INTERRUPTED").contains(event.type()))
                .map(event->ServerSentEvent.<TaskEvent>builder(event).id(Long.toString(event.id())).event("task-event").build());
    }
    private Path output(long id,HttpServletRequest request) throws IOException {
        TaskView task=tasks.owned(id,user());
        var project=projects.getOwnedProject(user(),task.projectId());
        return workspaces.resolve(Path.of(project.workspacePath()),".research_agent/tasks/"+id);
    }
    @GetMapping("/tasks/{id}/files") public BaseResponse<List<String>> files(@PathVariable long id,HttpServletRequest request) throws IOException {
        Path directory=output(id,request);
        if(!Files.exists(directory)) return ResultUtils.success(List.of());
        try(var paths=Files.walk(directory)) {
            return ResultUtils.success(paths.filter(Files::isRegularFile).filter(p->!Files.isSymbolicLink(p))
                    .map(directory::relativize).map(Path::toString).limit(100).toList());
        }
    }
    @GetMapping("/tasks/{id}/file") public BaseResponse<String> file(@PathVariable long id,@RequestParam String path,HttpServletRequest request) throws IOException {
        Path target=workspaces.resolve(output(id,request),path);
        if(!Files.isRegularFile(target) || Files.size(target)>262144) throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件不存在或超过 256 KB");
        return ResultUtils.success(Files.readString(target));
    }
    @GetMapping("/tasks/{id}/download") public void download(@PathVariable long id,HttpServletRequest request,HttpServletResponse response) throws IOException {
        Path directory=output(id,request);
        if(!Files.isDirectory(directory)) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"尚未生成代码产物");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition","attachment; filename=research-task-"+id+".zip");
        try(var zip=new ZipOutputStream(response.getOutputStream()); var paths=Files.walk(directory)) {
            for(Path file:paths.filter(Files::isRegularFile).limit(100).toList()) {
                workspaces.check(file);
                zip.putNextEntry(new ZipEntry(directory.relativize(file).toString().replace('\\','/')));
                Files.copy(file,zip); zip.closeEntry();
            }
        }
    }
}
