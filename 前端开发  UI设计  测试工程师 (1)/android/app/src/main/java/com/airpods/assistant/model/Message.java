package com.airpods.assistant.model;

/** 聊天消息数据模型 */
public class Message {
    private int id;
    private String role;      // "user" 或 "ai"
    private String content;
    private String time;

    public Message(int id, String role, String content, String time) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.time = time;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public boolean isUser() { return "user".equals(role); }
    public boolean isAi() { return "ai".equals(role); }
}
