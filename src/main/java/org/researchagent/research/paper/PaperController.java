package org.researchagent.research.paper;

import org.researchagent.common.BaseResponse;
import org.researchagent.common.ResultUtils;
import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/research-projects/{projectId}/papers")
public class PaperController {
    public record SubscriptionInput(String topic, boolean enabled) { }
    private final PaperService papers;
    public PaperController(PaperService papers) { this.papers = papers; }
    @GetMapping("/subscription")
    public BaseResponse<PaperService.Subscription> subscription(@PathVariable long projectId) {
        return ResultUtils.success(papers.subscription(projectId));
    }
    @PutMapping("/subscription")
    public BaseResponse<PaperService.Subscription> save(@PathVariable long projectId, @RequestBody SubscriptionInput body) {
        if (body == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入论文主题");
        return ResultUtils.success(papers.save(projectId, body.topic(), body.enabled()));
    }
    @GetMapping
    public BaseResponse<List<PaperService.Paper>> list(@PathVariable long projectId) {
        return ResultUtils.success(papers.list(projectId));
    }
    @PostMapping("/sync")
    public BaseResponse<Map<String, Integer>> sync(@PathVariable long projectId) {
        return ResultUtils.success(Map.of("added", papers.sync(projectId)));
    }
}
