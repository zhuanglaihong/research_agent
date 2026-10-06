package org.researchagent.research.project;

import java.time.LocalDateTime;

/** Public project fields returned to the owning user. */
public record ResearchProjectView(
        Long id,
        String name,
        String description,
        String workspacePath,
        String defaultLanguage,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
