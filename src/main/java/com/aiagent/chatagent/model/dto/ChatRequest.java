package com.aiagent.chatagent.model.dto;

import lombok.Data;

@Data
public class ChatRequest {

    private Long sessionId;

    private String prompt;
}