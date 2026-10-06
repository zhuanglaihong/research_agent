package org.researchagent.research.project;

/** Request payload for creating a research workspace. */
public record CreateResearchProjectRequest(
        String name,
        String description,
        String workspacePath,
        String defaultLanguage
) {
}
