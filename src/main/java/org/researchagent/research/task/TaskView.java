package org.researchagent.research.task;

import java.time.LocalDateTime;

public record TaskView(long id, long projectId, String title, String requestText, String status,
                       String planJson, String resultText, String outputPath, String provider,
                       String errorMessage, LocalDateTime createdAt, LocalDateTime updatedAt, long conversationId) { }
