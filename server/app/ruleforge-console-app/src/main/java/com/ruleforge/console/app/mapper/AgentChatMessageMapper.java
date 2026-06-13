package com.ruleforge.console.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.app.agent.model.AgentModels.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM rfa_agent_chat_message WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatMessage> findBySessionId(String sessionId);
}
