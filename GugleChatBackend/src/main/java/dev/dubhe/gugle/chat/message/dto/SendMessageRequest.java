package dev.dubhe.gugle.chat.message.dto;

import dev.dubhe.gugle.chat.common.enums.MessageType;

public class SendMessageRequest {
    private String content;
    private MessageType type = MessageType.TEXT;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
}
