package org.researchagent.research.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

/** Stores each task's complete bounded message window, including tool exchanges. */
public class JdbcChatMemoryStore implements ChatMemoryStore {
    private final JdbcTemplate jdbc;
    public JdbcChatMemoryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<ChatMessage> getMessages(Object memoryId) {
        var rows = jdbc.query("select messages_json from agent_chat_memory where memory_id=?",
                (rs, row) -> rs.getString(1), memoryId.toString());
        return rows.isEmpty() ? List.of() : ChatMessageDeserializer.messagesFromJson(rows.getFirst());
    }

    @Override public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        jdbc.update("merge into agent_chat_memory(memory_id,messages_json,updated_at) key(memory_id) values(?,?,current_timestamp)",
                memoryId.toString(), ChatMessageSerializer.messagesToJson(messages));
    }

    @Override public void deleteMessages(Object memoryId) {
        jdbc.update("delete from agent_chat_memory where memory_id=?", memoryId.toString());
    }
}
