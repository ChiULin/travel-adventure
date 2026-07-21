package com.example.demo.service;

import java.util.Objects;

public record InvestigationDefinition(
        ClueType type,
        String actionName,
        String resultMessage,
        String clueText
) {
    public InvestigationDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(actionName, "actionName");
        Objects.requireNonNull(resultMessage, "resultMessage");
        Objects.requireNonNull(clueText, "clueText");
    }
}
