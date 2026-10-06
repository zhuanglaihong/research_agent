package org.researchagent.research.project;

import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.researchagent.research.workspace.WorkspaceService;

@Service
public class ResearchProjectService {

    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("[a-zA-Z0-9._+#-]{1,32}");
    private static final RowMapper<ResearchProjectView> PROJECT_ROW_MAPPER = (rs, rowNum) ->
            new ResearchProjectView(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("workspace_path"),
                    rs.getString("default_language"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("updated_at").toLocalDateTime()
            );

    private final JdbcTemplate jdbcTemplate;
    private final WorkspaceService workspaceService;

    public ResearchProjectService(JdbcTemplate jdbcTemplate, WorkspaceService workspaceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.workspaceService = workspaceService;
    }

    public List<ResearchProjectView> listOwnedProjects(long userId) {
        return jdbcTemplate.query("""
                select id, name, description, workspace_path, default_language, status, created_at, updated_at
                from research_project
                where user_id = ? and deleted_at is null
                order by updated_at desc, id desc
                limit 100
                """, PROJECT_ROW_MAPPER, userId);
    }

    public ResearchProjectView getOwnedProject(long userId, long projectId) {
        List<ResearchProjectView> rows = jdbcTemplate.query("""
                select id, name, description, workspace_path, default_language, status, created_at, updated_at
                from research_project
                where id = ? and user_id = ? and deleted_at is null
                """, PROJECT_ROW_MAPPER, projectId, userId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "科研项目不存在");
        }
        return rows.getFirst();
    }

    @Transactional
    public ResearchProjectView createProject(long userId, CreateResearchProjectRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        String workspacePath = request.workspacePath() == null ? "" : request.workspacePath().trim();
        String language = request.defaultLanguage() == null || request.defaultLanguage().isBlank()
                ? "python"
                : request.defaultLanguage().trim().toLowerCase(Locale.ROOT);
        if (name.isBlank() || name.length() > 160) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目名称不能为空且不能超过 160 个字符");
        }
        if (workspacePath.length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工作区路径不能超过 1024 个字符");
        }
        if (!LANGUAGE_PATTERN.matcher(language).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "默认语言格式无效");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String boundPath = workspaceService.bind(workspacePath, userId).toString();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into research_project (user_id, name, description, workspace_path, default_language)
                    values (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, userId);
            statement.setString(2, name);
            statement.setString(3, request.description());
            statement.setString(4, boundPath);
            statement.setString(5, language);
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建科研项目失败");
        }
        return getOwnedProject(userId, generatedId.longValue());
    }

    @Transactional
    public void archiveProject(long userId, long projectId) {
        Integer active = jdbcTemplate.queryForObject("select count(*) from research_task where project_id = ? and user_id = ? and status in ('QUEUED','RUNNING')", Integer.class, projectId, userId);
        if (active != null && active > 0) throw new BusinessException(ErrorCode.OPERATION_ERROR, "请先取消运行中的科研任务");
        int changed = jdbcTemplate.update("""
                update research_project
                set status = 'ARCHIVED', deleted_at = current_timestamp
                where id = ? and user_id = ? and deleted_at is null
                """, projectId, userId);
        if (changed == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "科研项目不存在");
        }
    }
}
