package com.airpods.assistant.model;

/** 知识库条目数据模型 */
public class KnowledgeItem {
    private int id;
    private String category;
    private String question;
    private String answer;
    private String keywords;  // 逗号分隔

    public KnowledgeItem() {}

    public KnowledgeItem(int id, String category, String question,
                         String answer, String keywords) {
        this.id = id;
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.keywords = keywords;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
}
