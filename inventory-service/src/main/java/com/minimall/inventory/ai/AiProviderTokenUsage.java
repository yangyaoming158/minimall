package com.minimall.inventory.ai;

public record AiProviderTokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    public AiProviderTokenUsage {
        promptTokens = Math.max(0, promptTokens);
        completionTokens = Math.max(0, completionTokens);
        totalTokens = Math.max(0, totalTokens);
    }

    public static AiProviderTokenUsage empty() {
        return new AiProviderTokenUsage(0, 0, 0);
    }
}
