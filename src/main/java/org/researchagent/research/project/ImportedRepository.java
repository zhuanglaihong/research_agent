package org.researchagent.research.project;

import java.time.LocalDateTime;

public record ImportedRepository(long id, String repositoryUrl, String repositoryName,
                                 String localPath, String commitHash, LocalDateTime importedAt) { }
