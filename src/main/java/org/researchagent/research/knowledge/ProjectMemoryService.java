package org.researchagent.research.knowledge;

import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.researchagent.research.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A small, inspectable long-term memory file for one local research project. */
@Service
public class ProjectMemoryService {
    private static final int MAX_LENGTH = 16_000;
    private final WorkspaceService workspaces;

    public ProjectMemoryService(WorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    private Path file(Path project) throws IOException {
        return workspaces.resolve(project, ".research_agent/memory.md");
    }

    public String read(Path project) throws IOException {
        Path memory = file(project);
        return Files.exists(memory) ? Files.readString(memory) : "";
    }

    public synchronized void replace(Path project, String content) throws IOException {
        if (content == null || content.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目记忆不能超过 16000 字符");
        }
        Path memory = file(project);
        Files.createDirectories(memory.getParent());
        workspaces.check(memory);
        Files.writeString(memory, content);
    }

    public synchronized void append(Path project, String finding) throws IOException {
        if (finding == null || finding.isBlank() || finding.length() > 1000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单条记忆不能为空且不能超过 1000 字符");
        }
        String existing = read(project);
        String updated = existing + (existing.isBlank() ? "" : "\n") + "- " + finding.strip() + "\n";
        replace(project, updated);
    }
}
