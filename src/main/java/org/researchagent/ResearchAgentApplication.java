package org.researchagent;

import org.researchagent.config.CorsConfig;
import org.researchagent.config.JsonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "org.researchagent.research",
        "org.researchagent.exception"
})
@Import({JsonConfig.class, CorsConfig.class})
public class ResearchAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResearchAgentApplication.class, args);
    }
}
