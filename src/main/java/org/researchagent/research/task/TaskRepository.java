package org.researchagent.research.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.researchagent.exception.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.util.*;

@Repository
public class TaskRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<TaskView> mapper = (r, n) -> new TaskView(r.getLong("id"), r.getLong("project_id"),
            r.getString("title"), r.getString("request_text"), r.getString("status"), r.getString("plan_json"),
            r.getString("result_text"), r.getString("output_path"), r.getString("provider"), r.getString("error_message"),
            r.getTimestamp("created_at").toLocalDateTime(), r.getTimestamp("updated_at").toLocalDateTime());

    public TaskRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("事件序列化失败", e); }
    }

    public long create(long project, long user, String prompt, String provider) {
        var keys = new GeneratedKeyHolder();
        String plan = json(Map.of("steps", List.of("读取项目文件与需求", "通过文件工具生成科研代码", "整理使用说明与代码产物"),
                "scope", "仅生成独立任务目录中的代码，不执行程序，不覆盖原仓库"));
        jdbc.update(c -> {
            var statement = c.prepareStatement("insert into research_task(project_id,user_id,title,request_text,status,plan_json,provider) values(?,?,?,?,?,?,?)", new String[]{"id"});
            statement.setLong(1, project); statement.setLong(2, user);
            statement.setString(3, prompt.substring(0, Math.min(80, prompt.length())));
            statement.setString(4, prompt); statement.setString(5, "WAITING_APPROVAL");
            statement.setString(6, plan); statement.setString(7, provider);
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new BusinessException(ErrorCode.OPERATION_ERROR);
        long id = keys.getKey().longValue();
        event(id, "STATE", Map.of("status", "WAITING_APPROVAL", "message", "任务计划已就绪，请确认开始"));
        return id;
    }

    public TaskView get(long id) {
        List<TaskView> tasks = jdbc.query("select * from research_task where id=?", mapper, id);
        if (tasks.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "科研任务不存在");
        return tasks.getFirst();
    }

    public TaskView owned(long id, long user) {
        List<TaskView> tasks = jdbc.query("select t.* from research_task t join research_project p on p.id=t.project_id where t.id=? and t.user_id=? and p.deleted_at is null", mapper, id, user);
        if (tasks.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "科研任务不存在");
        return tasks.getFirst();
    }

    public List<TaskView> list(long project, long user) {
        return jdbc.query("select * from research_task where project_id=? and user_id=? order by id desc limit 100", mapper, project, user);
    }

    public boolean transition(long id, String from, String to) {
        int count = jdbc.update("update research_task set status=? where id=? and status=?", to, id, from);
        if (count == 1) event(id, "STATE", Map.of("status", to));
        return count == 1;
    }

    public List<TaskView> queued() { return jdbc.query("select * from research_task where status='QUEUED' order by id limit 2", mapper); }

    public void event(long id, String type, Object payload) {
        jdbc.update("insert into task_event(task_id,event_type,payload) values(?,?,?)", id, type, json(payload));
    }

    public List<TaskEvent> events(long id, long after) {
        return jdbc.query("select * from task_event where task_id=? and id>? order by id limit 100", (r,n) ->
                new TaskEvent(r.getLong("id"), r.getLong("task_id"), r.getString("event_type"), r.getString("payload"), r.getTimestamp("created_at").toLocalDateTime()), id, after);
    }

    public void succeed(long id, String result, String path) {
        if (jdbc.update("update research_task set status='SUCCEEDED', result_text=?, output_path=?, finished_at=current_timestamp where id=? and status='RUNNING'", result, path, id) == 1)
            event(id, "DONE", Map.of("status", "SUCCEEDED", "message", "代码生成完成"));
    }

    public void fail(long id, String message) {
        if (jdbc.update("update research_task set status='FAILED', error_message=?, finished_at=current_timestamp where id=? and status='RUNNING'", message, id) == 1)
            event(id, "FAILED", Map.of("status", "FAILED", "message", message));
    }

    public void cancel(long id) {
        if (jdbc.update("update research_task set status='CANCELED', finished_at=current_timestamp where id=? and status in ('WAITING_APPROVAL','QUEUED','RUNNING')", id) == 1)
            event(id, "CANCELED", Map.of("status", "CANCELED"));
        else throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务已经结束");
    }

    public void recover() {
        for (var task : jdbc.query("select * from research_task where status='RUNNING'", mapper)) {
            jdbc.update("update research_task set status='INTERRUPTED',error_message='服务重启中断了生成任务',finished_at=current_timestamp where id=? and status='RUNNING'", task.id());
            event(task.id(), "INTERRUPTED", Map.of("status", "INTERRUPTED"));
        }
    }
}
