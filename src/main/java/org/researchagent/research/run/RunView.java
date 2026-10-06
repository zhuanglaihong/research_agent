package org.researchagent.research.run;

import java.time.LocalDateTime;

public record RunView(long id, long taskId, String scriptPath, String workingDirectory, String status, Integer exitCode,
        LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt) { }
