package com.example.demo.service;

import java.util.Objects;

public record VisualCandidate(
        VisualChallengeKey stage
) {
    public VisualCandidate {
        Objects.requireNonNull(stage, "stage");
    }
}
