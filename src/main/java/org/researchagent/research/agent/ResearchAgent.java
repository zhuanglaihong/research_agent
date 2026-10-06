package org.researchagent.research.agent;
import dev.langchain4j.service.SystemMessage;
public interface ResearchAgent {
    @SystemMessage(fromResource = "prompt/research-agent-system-prompt.txt")
    String work(String request);
}
