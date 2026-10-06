package org.researchagent.research.knowledge;

import org.researchagent.common.BaseResponse;
import org.researchagent.common.ResultUtils;
import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.researchagent.research.LocalWorkspace;
import org.researchagent.research.project.ResearchProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/research-projects/{projectId}")
public class KnowledgeController {
    public record MemoryUpdate(String content) { }
    public record KnowledgeInput(String source, String content) { }

    private final ResearchProjectService projects;
    private final ProjectMemoryService memory;
    private final KnowledgeService knowledge;

    public KnowledgeController(ResearchProjectService projects, ProjectMemoryService memory, KnowledgeService knowledge) {
        this.projects = projects;
        this.memory = memory;
        this.knowledge = knowledge;
    }

    private Path project(long projectId) {
        return Path.of(projects.getOwnedProject(LocalWorkspace.ID, projectId).workspacePath());
    }

    @GetMapping("/memory")
    public BaseResponse<String> memory(@PathVariable long projectId) throws IOException {
        return ResultUtils.success(memory.read(project(projectId)));
    }

    @PutMapping("/memory")
    public BaseResponse<String> updateMemory(@PathVariable long projectId, @RequestBody MemoryUpdate body) throws IOException {
        if (body == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入项目记忆");
        Path workspace = project(projectId);
        memory.replace(workspace, body.content());
        return ResultUtils.success(memory.read(workspace));
    }

    @GetMapping("/knowledge")
    public BaseResponse<List<KnowledgeService.Document>> documents(@PathVariable long projectId) {
        project(projectId);
        return ResultUtils.success(knowledge.list(projectId));
    }

    @PostMapping("/knowledge")
    public BaseResponse<KnowledgeService.Document> add(@PathVariable long projectId, @RequestBody KnowledgeInput body) {
        project(projectId);
        if (body == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入笔记内容");
        return ResultUtils.success(knowledge.add(projectId, body.source(), body.content()));
    }

    @GetMapping("/knowledge/search")
    public BaseResponse<List<KnowledgeService.Hit>> search(@PathVariable long projectId,
            @RequestParam String query) {
        project(projectId);
        return ResultUtils.success(knowledge.search(projectId, query, 5));
    }
}
