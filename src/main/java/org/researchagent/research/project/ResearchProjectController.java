package org.researchagent.research.project;

import org.researchagent.common.BaseResponse;
import org.researchagent.common.ResultUtils;
import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.researchagent.research.LocalWorkspace;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/research-projects")
public class ResearchProjectController {

    private final ResearchProjectService projectService;
    public ResearchProjectController(ResearchProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public BaseResponse<ResearchProjectView> createProject(
            @RequestBody(required = false) CreateResearchProjectRequest body
    ) {
        if (body == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求内容不能为空");
        }
        return ResultUtils.success(projectService.createProject(LocalWorkspace.ID, body));
    }

    @GetMapping
    public BaseResponse<List<ResearchProjectView>> listProjects() {
        return ResultUtils.success(projectService.listOwnedProjects(LocalWorkspace.ID));
    }

    @GetMapping("/{projectId}")
    public BaseResponse<ResearchProjectView> getProject(
            @PathVariable long projectId
    ) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 无效");
        }
        return ResultUtils.success(projectService.getOwnedProject(LocalWorkspace.ID, projectId));
    }

    @DeleteMapping("/{projectId}")
    public BaseResponse<Boolean> archiveProject(
            @PathVariable long projectId
    ) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 无效");
        }
        projectService.archiveProject(LocalWorkspace.ID, projectId);
        return ResultUtils.success(true);
    }
}
