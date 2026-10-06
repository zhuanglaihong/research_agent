package org.researchagent.research.workspace;

import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class WorkspaceService {
    private final Path root;

    public WorkspaceService(@Value("${research.workspace-root:./data/workspaces}") String directory) throws IOException {
        Files.createDirectories(Path.of(directory));
        root = Path.of(directory).toRealPath();
    }

    public Path bind(String requested, long userId) {
        try {
            Path userRoot = root.resolve(Long.toString(userId));
            Path path = requested == null || requested.isBlank()
                    ? userRoot.resolve("project-" + UUID.randomUUID())
                    : Path.of(requested).toAbsolutePath().normalize();
            if (!path.startsWith(userRoot)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "工作区必须位于当前用户的工作区目录内");
            check(path);
            Files.createDirectories(path);
            return path.toRealPath();
        } catch (IOException | InvalidPathException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法创建工作区目录");
        }
    }

    public Path resolve(Path base, String relative) throws IOException {
        Path input = Path.of(relative);
        if (input.isAbsolute()) throw new BusinessException(ErrorCode.PARAMS_ERROR, "只允许工作区内的相对路径");
        Path path = base.resolve(input).normalize();
        if (!path.startsWith(base.normalize())) throw new BusinessException(ErrorCode.PARAMS_ERROR, "路径超出项目目录");
        check(path);
        return path;
    }

    public void check(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "工作区必须位于配置的科研工作区根目录内");
        Path current = normalized;
        while (current != null && current.startsWith(root)) {
            if (Files.isSymbolicLink(current)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "工作区不允许符号链接");
            current = current.getParent();
        }
    }
}
