package com.learningcompanion.ai.config.api.dto;

import com.learningcompanion.ai.config.application.PromptTrialSamples;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiCapability;
import java.util.List;

/** Danh sách chức năng có cấu hình, phiên bản đang phát hành của từng chức năng và các bộ nguồn mẫu để thử. */
public record AiConfigOverviewResponse(List<Feature> features, List<PromptTrialSamples.SampleSet> sampleSets) {

    public record Feature(AiFeature feature, AiCapability capability, AiConfigVersionResponse published,
                          int latestVersion, int draftCount) {
    }
}
