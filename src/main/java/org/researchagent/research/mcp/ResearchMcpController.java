package org.researchagent.research.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.researchagent.research.agent.ResearchObservationService;
import org.researchagent.research.project.ResearchProjectService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Stateless, read-only MCP 2025-03-26 Streamable HTTP subset, JSON responses only. */
@RestController
@RequestMapping("/mcp/projects/{projectId}")
public class ResearchMcpController {
    private static final String VERSION="2025-03-26";
    private final ResearchObservationService service;
    private final ResearchProjectService projects;
    private final ObjectMapper json;
    private final Set<String> origins;
    public ResearchMcpController(ResearchObservationService service,ResearchProjectService projects,ObjectMapper json,
            @Value("${research.mcp.allowed-origins:http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:5174,http://localhost:5174}") String origins) {
        this.service=service;this.projects=projects;this.json=json;
        this.origins=new HashSet<>(Arrays.asList(origins.split(",")));
    }
    private boolean allowed(HttpServletRequest request) {
        String origin=request.getHeader("Origin");
        return origin==null||origins.contains(origin);
    }
    @GetMapping public ResponseEntity<?> get(HttpServletRequest request) {
        return ResponseEntity.status(allowed(request)?405:403).build();
    }
    @PostMapping(consumes="application/json",produces="application/json")
    public ResponseEntity<?> post(@PathVariable long projectId,@RequestBody JsonNode input,HttpServletRequest request) {
        if(!allowed(request)) return ResponseEntity.status(403).build();
        projects.getOwnedProject(1,projectId);
        if(input.toString().length()>65536) return ResponseEntity.status(413).build();
        Object id=input.has("id")?json.convertValue(input.get("id"),Object.class):null;
        if(!input.isObject()||!"2.0".equals(input.path("jsonrpc").asText())||!input.path("method").isTextual()
                ||(input.has("id")&&!input.get("id").isTextual()&&!input.get("id").isNumber()))
            return ResponseEntity.ok(error(null,-32600,"Invalid Request"));
        String method=input.path("method").asText();
        if(!"initialize".equals(method) && !VERSION.equals(request.getHeader("MCP-Protocol-Version")))
            return ResponseEntity.badRequest().build();
        if(!input.has("id")) {
            return ResponseEntity.status(method.startsWith("notifications/")?202:400).build();
        }
        Object result;
        switch(method) {
            case "initialize" -> result=Map.of("protocolVersion",VERSION,"capabilities",Map.of("tools",Map.of("listChanged",false)),
                    "serverInfo",Map.of("name","research_agent","version","0.1.0"),
                    "instructions","Read-only tools bound to one local research project. No code execution is exposed.");
            case "ping" -> result=Map.of();
            case "tools/list" -> result=Map.of("tools",List.of(
                    tool("search_research_notes","检索当前项目的论文与笔记，返回来源和块编号",Map.of("query",Map.of("type","string","minLength",1,"maxLength",2000)),List.of("query")),
                    tool("list_collected_papers","列出当前项目最新十篇已收集论文",Map.of(),List.of()),
                    tool("list_experiments","查看当前项目最近十次实验",Map.of(),List.of()),
                    tool("inspect_experiment","读取当前项目实验日志与实际指标",Map.of("runId",Map.of("type","integer","minimum",1)),List.of("runId"))));
            case "tools/call" -> {
                JsonNode params=input.path("params"),args=params.path("arguments");
                if(!params.path("name").isTextual()||(!args.isMissingNode()&&!args.isObject()))
                    return ResponseEntity.ok(error(id,-32602,"Invalid tool parameters"));
                String name=params.path("name").asText();
                if(!Set.of("search_research_notes","list_collected_papers","list_experiments","inspect_experiment").contains(name))
                    return ResponseEntity.ok(error(id,-32602,"Unknown tool"));
                try {
                    Object value=switch(name) {
                        case "search_research_notes" -> {
                            if(!args.path("query").isTextual()) throw new IllegalArgumentException();
                            yield service.search(projectId,args.get("query").asText());
                        }
                        case "list_collected_papers" -> service.papers(projectId);
                        case "list_experiments" -> service.experiments(projectId);
                        default -> {
                            if(!args.path("runId").isIntegralNumber()||args.get("runId").asLong()<=0) throw new IllegalArgumentException();
                            yield service.result(projectId,args.get("runId").asLong());
                        }
                    };
                    result=content(json.writeValueAsString(value),false);
                } catch(Exception e) { result=content("工具执行失败：请检查参数、项目归属和实验记录。",true); }
            }
            default -> { return ResponseEntity.ok(error(id,-32601,"Method not found")); }
        }
        return ResponseEntity.ok(Map.of("jsonrpc","2.0","id",id,"result",result));
    }
    private static Object content(String text,boolean failed) {
        return Map.of("content",List.of(Map.of("type","text","text",text)),"isError",failed);
    }
    private static Object tool(String name,String description,Map<String,Object> properties,List<String> required) {
        return Map.of("name",name,"description",description,
                "inputSchema",Map.of("type","object","properties",properties,"required",required,"additionalProperties",false),
                "annotations",Map.of("readOnlyHint",true,"destructiveHint",false));
    }
    private static Object error(Object id,int code,String message) {
        var response=new LinkedHashMap<String,Object>();response.put("jsonrpc","2.0");response.put("id",id);
        response.put("error",Map.of("code",code,"message",message));return response;
    }
}
