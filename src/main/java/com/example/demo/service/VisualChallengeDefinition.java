package com.example.demo.service;

import java.util.List;
import java.util.Objects;

public record VisualChallengeDefinition(
        String challengeKey,
        String focusImageUrl,
        String puzzleImageUrl,
        String focusPrompt,
        String cultureExplanation,
        List<VisualCandidate> candidates,
        VisualChallengeKey correctStage
) {
    public VisualChallengeDefinition {
        Objects.requireNonNull(challengeKey, "challengeKey");
        Objects.requireNonNull(focusImageUrl, "focusImageUrl");
        Objects.requireNonNull(puzzleImageUrl, "puzzleImageUrl");
        Objects.requireNonNull(focusPrompt, "focusPrompt");
        Objects.requireNonNull(cultureExplanation, "cultureExplanation");
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(correctStage, "correctStage");
    }

    public List<VisualChallengeKey> candidateStages() {
        return candidates.stream().map(VisualCandidate::stage).toList();
    }
}
