package org.researchagent.research.task;

import org.researchagent.research.agent.ResearchAgentEngine;
import jakarta.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Single backend replica MVP: jobs outlive browser subscriptions, not JVM restarts. */
@Component
public class TaskWorker {
    private static final Logger log=LoggerFactory.getLogger(TaskWorker.class);
    private final TaskRepository tasks;
    private final ResearchAgentEngine engine;
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final Semaphore slots=new Semaphore(2);
    public TaskWorker(TaskRepository tasks, ResearchAgentEngine engine) { this.tasks=tasks; this.engine=engine; }
    @PostConstruct public void recover() { tasks.recover(); }
    @Scheduled(fixedDelay=1000) public void poll() {
        for (TaskView task:tasks.queued()) {
            if (!slots.tryAcquire()) return;
            if (!tasks.transition(task.id(),"QUEUED","RUNNING")) { slots.release(); continue; }
            executor.submit(() -> {
                try { engine.execute(tasks.get(task.id())); }
                catch (Exception e) {
                    log.warn("Research task {} failed: {}",task.id(),e.getClass().getSimpleName());
                    log.debug("Research task {} failure details",task.id(),e);
                    String reason=e instanceof IllegalStateException?e.getMessage():"科研任务失败，请检查模型服务、项目文件和后台日志";
                    tasks.fail(task.id(),reason);
                } finally { slots.release(); }
            });
        }
    }
    public void cancel(long id) {
        tasks.cancel(id);
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
