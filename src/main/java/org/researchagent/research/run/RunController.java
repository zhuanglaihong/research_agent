package org.researchagent.research.run;

import org.researchagent.common.BaseResponse;
import org.researchagent.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class RunController {
    public record CreateRun(String scriptPath) { }
    private final RunService runs;
    private final ExperimentMetricsService metrics;
    public RunController(RunService runs, ExperimentMetricsService metrics) { this.runs = runs; this.metrics = metrics; }

    @GetMapping("/research/runs/capabilities")
    public BaseResponse<Map<String, Object>> capabilities() {
        return ResultUtils.success(Map.of("enabled", runs.enabled(), "language", "python", "pythonExecutable", runs.pythonExecutable(), "approvalRequired", true,
                "maxRuntimeSeconds", 300, "maxLogBytes", 1_048_576));
    }
    @PostMapping("/tasks/{taskId}/runs")
    public BaseResponse<RunView> create(@PathVariable long taskId, @RequestBody CreateRun request) throws IOException {
        return ResultUtils.success(runs.create(taskId, request == null ? null : request.scriptPath()));
    }
    @GetMapping("/tasks/{taskId}/runs")
    public BaseResponse<List<RunView>> list(@PathVariable long taskId) {
        return ResultUtils.success(runs.list(taskId));
    }
    @GetMapping("/runs/{id}")
    public BaseResponse<RunView> get(@PathVariable long id) { return ResultUtils.success(runs.get(id)); }
    @PostMapping("/runs/{id}/approve")
    public BaseResponse<RunView> approve(@PathVariable long id) { return ResultUtils.success(runs.approve(id)); }
    @PostMapping("/runs/{id}/cancel")
    public BaseResponse<RunView> cancel(@PathVariable long id) { return ResultUtils.success(runs.cancel(id)); }
    @GetMapping("/runs/{id}/logs")
    public BaseResponse<Map<String, String>> logs(@PathVariable long id) throws IOException { return ResultUtils.success(runs.logs(id)); }
    @GetMapping("/runs/{id}/metrics")
    public BaseResponse<ExperimentMetricsService.Report> metrics(@PathVariable long id) throws IOException {
        return ResultUtils.success(metrics.report(id));
    }
    @GetMapping(value="/runs/{id}/plot", produces="image/svg+xml")
    public ResponseEntity<String> plot(@PathVariable long id, @org.springframework.web.bind.annotation.RequestParam String metric) throws IOException {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .contentType(MediaType.parseMediaType("image/svg+xml")).body(metrics.svg(id, metric));
    }
}
