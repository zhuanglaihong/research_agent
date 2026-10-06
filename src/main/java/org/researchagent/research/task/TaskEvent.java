package org.researchagent.research.task;
import java.time.LocalDateTime;
public record TaskEvent(long id, long taskId, String type, String payload, LocalDateTime timestamp) { }
