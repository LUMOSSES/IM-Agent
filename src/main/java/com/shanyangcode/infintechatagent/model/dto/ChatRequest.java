package com.shanyangcode.infintechatagent.model.dto;

import lombok.Data;

@Data
public class ChatRequest {

    private Long sessionId;

    private String prompt;
}