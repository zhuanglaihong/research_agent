package org.researchagent.research;
import org.researchagent.common.*;
import org.researchagent.research.agent.ResearchAgentEngine;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class ResearchHealthController {
    private final ResearchAgentEngine engine;
    public ResearchHealthController(ResearchAgentEngine engine) { this.engine=engine; }
    @GetMapping("/research/health") public BaseResponse<Map<String,Object>> health() {
        return ResultUtils.success(Map.of("name","research_agent","aiMode",engine.mode(),"codeGeneration",true,
                "experimentExecution",false,"paperSubscription",false,"deployment","single-replica"));
    }
}
