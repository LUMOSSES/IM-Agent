package com.aiagent.chatagent.model.dto;

public class KnDocInfo {
    private String name;
    private long size;
    private long lastModified;
    private int qaCount;

    public KnDocInfo() {}

    public KnDocInfo(String name, long size, long lastModified, int qaCount) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.qaCount = qaCount;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public int getQaCount() { return qaCount; }
    public void setQaCount(int qaCount) { this.qaCount = qaCount; }
}
