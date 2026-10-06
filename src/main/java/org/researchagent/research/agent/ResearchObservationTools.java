package org.researchagent.research.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.researchagent.research.task.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Project is bound by the application, never selected by the model. */
public class ResearchObservationTools {
    private final long projectId,taskId;
    private final ResearchObservationService service;
    private final TaskRepository tasks;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    public ResearchObservationTools(long projectId,long taskId,ResearchObservationService service,TaskRepository tasks) {
        this.projectId=projectId;this.taskId=taskId;this.service=service;this.tasks=tasks;
    }
    private String result(String name,Object value) throws Exception {
        if(!tasks.get(taskId).status().equals("RUNNING")) throw new IllegalStateException("任务已经结束");
        tasks.event(taskId,"TOOL",Map.of("name",name));
        return json.writeValueAsString(value);
    }
    @Tool("查看当前科研项目已收集的最新十篇论文，含标题、原始摘要和来源链接")
    public String listCollectedPapers() throws Exception { return result("listCollectedPapers",service.papers(projectId)); }
    @Tool("查看当前科研项目最近十次实验的运行 ID、脚本、状态与退出码")
    public String listProjectExperiments() throws Exception { return result("listProjectExperiments",service.experiments(projectId)); }
    @Tool("读取当前项目指定实验的日志与实际数值指标，分析失败或结果时必须先使用此工具")
    public String inspectExperiment(@P("实验运行 ID") long runId) throws Exception { return result("inspectExperiment",service.result(projectId,runId)); }
}
