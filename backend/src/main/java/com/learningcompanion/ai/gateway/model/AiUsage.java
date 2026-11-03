package com.learningcompanion.ai.gateway.model;

/** Số đơn vị (token) đầu vào và đầu ra nhà cung cấp tính cho một lời gọi. */
public record AiUsage(int inputUnits, int outputUnits) {

    public static final AiUsage NONE = new AiUsage(0, 0);

    public AiUsage {
        inputUnits = Math.max(0, inputUnits);
        outputUnits = Math.max(0, outputUnits);
    }
}
