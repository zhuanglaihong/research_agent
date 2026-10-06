package org.researchagent.research.paper;

import org.researchagent.common.BaseResponse;
import org.researchagent.common.ResultUtils;
import org.researchagent.research.task.TaskView;
import org.researchagent.research.task.TaskRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/research-projects/{projectId}/paper-methods")
public class PaperToCodeController {
    public record TaskInput(String instructions) { }
    private final PaperToCodeService service;
    private final TaskRepository tasks;
    @org.springframework.beans.factory.annotation.Value("${research.ai.mode:demo}") private String mode;
    public PaperToCodeController(PaperToCodeService service, TaskRepository tasks) { this.service = service; this.tasks = tasks; }
    @PostMapping(consumes = "multipart/form-data")
    public BaseResponse<PaperToCodeService.Method> upload(@PathVariable long projectId, @RequestParam String title,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResultUtils.success(service.importPdf(projectId, title, file));
    }
    @GetMapping public BaseResponse<List<PaperToCodeService.Method>> list(@PathVariable long projectId) {
        return ResultUtils.success(service.list(projectId));
    }
    @PostMapping("/{methodId}/tasks") public BaseResponse<TaskView> task(@PathVariable long projectId, @PathVariable long methodId,
            @RequestBody(required = false) TaskInput body) {
        long id = service.createTask(projectId, methodId, body == null ? "" : body.instructions(), mode);
        return ResultUtils.success(tasks.get(id));
    }
}
