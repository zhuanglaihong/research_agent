package org.researchagent.research.project;

import org.researchagent.common.BaseResponse;
import org.researchagent.common.ResultUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/research-projects/{projectId}/repositories")
public class RepositoryImportController {
    public record ImportRequest(String url) { }
    private final RepositoryImportService repositories;
    public RepositoryImportController(RepositoryImportService repositories) { this.repositories = repositories; }
    @GetMapping public BaseResponse<List<ImportedRepository>> list(@PathVariable long projectId) { return ResultUtils.success(repositories.list(projectId)); }
    @PostMapping public BaseResponse<ImportedRepository> importRepository(@PathVariable long projectId, @RequestBody ImportRequest request) throws IOException, InterruptedException {
        return ResultUtils.success(repositories.importGithub(projectId, request == null ? null : request.url()));
    }
}
