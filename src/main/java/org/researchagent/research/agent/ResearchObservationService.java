package org.researchagent.research.agent;

import org.researchagent.research.project.ResearchProjectService;
import org.researchagent.research.paper.PaperService;
import org.researchagent.research.run.RunService;
import org.researchagent.research.run.ExperimentMetricsService;
import org.researchagent.research.knowledge.KnowledgeService;
import org.researchagent.research.task.TaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.Map;

/** Shared read-only capabilities for the internal Agent and external MCP clients. */
@Service
public class ResearchObservationService {
    private final ResearchProjectService projects;
    private final KnowledgeService knowledge;
    private final PaperService papers;
    private final RunService runs;
    private final ExperimentMetricsService metrics;
    private final TaskRepository tasks;
    private final JdbcTemplate jdbc;
    public ResearchObservationService(ResearchProjectService projects,KnowledgeService knowledge,
            PaperService papers,RunService runs,ExperimentMetricsService metrics,TaskRepository tasks,JdbcTemplate jdbc) {
        this.projects=projects;this.knowledge=knowledge;this.papers=papers;
        this.runs=runs;this.metrics=metrics;this.tasks=tasks;this.jdbc=jdbc;
    }
    public Object search(long project,String query) {
        projects.getOwnedProject(1,project);
        if(query==null||query.isBlank()||query.length()>2000) throw new IllegalArgumentException("检索问题需为 1–2000 字符");
        return knowledge.search(project,query,5);
    }
    public Object papers(long project) {
        projects.getOwnedProject(1,project);
        return papers.list(project).stream().limit(10).toList();
    }
    public Object experiments(long project) {
        projects.getOwnedProject(1,project);
        return jdbc.queryForList("select r.id,r.task_id,r.status,r.command_json,r.exit_code,r.created_at from experiment_run r join research_task t on t.id=r.task_id where t.project_id=? order by r.id desc limit 10",project);
    }
    public Object result(long project,long runId) throws IOException {
        projects.getOwnedProject(1,project);
        var run=runs.get(runId);
        if(tasks.get(run.taskId()).projectId()!=project) throw new IllegalArgumentException("实验不属于当前项目");
        var report=metrics.report(runId);
        return Map.of("run",run,"metrics",report,"logs",runs.logs(runId),
                "boundary","仅报告已记录的数据；运行中指标尚不完整，不代表论文方法已验证。");
    }
}
