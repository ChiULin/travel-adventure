package com.example.demo.service;

import java.util.List;
import java.util.Objects;

public record ExplorationMissionDefinition(
        String missionKey,
        Long cityId,
        Long targetSceneId,
        String title,
        String description,
        List<InvestigationDefinition> investigations,
        List<Long> candidateSceneIds,
        String cultureQuestion,
        List<String> cultureOptions,
        String cultureAnswer
) {
    public ExplorationMissionDefinition {
        Objects.requireNonNull(missionKey, "missionKey");
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(targetSceneId, "targetSceneId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        investigations = List.copyOf(investigations);
        candidateSceneIds = List.copyOf(candidateSceneIds);
        Objects.requireNonNull(cultureQuestion, "cultureQuestion");
        cultureOptions = List.copyOf(cultureOptions);
        Objects.requireNonNull(cultureAnswer, "cultureAnswer");

        if (investigations.size() != ClueType.values().length
                || investigations.stream().map(InvestigationDefinition::type).distinct().count()
                != ClueType.values().length) {
            throw new IllegalArgumentException("每個探索任務必須包含三種不同的調查方式");
        }
        if (candidateSceneIds.size() != 3 || !candidateSceneIds.contains(targetSceneId)) {
            throw new IllegalArgumentException("每個探索任務必須包含目標景點與三個候選景點");
        }
        if (cultureOptions.size() < 2 || !cultureOptions.contains(cultureAnswer)) {
            throw new IllegalArgumentException("文化挑戰選項必須包含正確答案");
        }
    }
}
