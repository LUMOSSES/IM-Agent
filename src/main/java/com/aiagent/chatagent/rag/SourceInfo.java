package com.aiagent.chatagent.rag;

public class SourceInfo {
    private String fileName;
    private String snippet;
    private double score;

    public SourceInfo() {}

    public SourceInfo(String fileName, String snippet, double score) {
        this.fileName = fileName;
        this.snippet = snippet;
        this.score = score;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
