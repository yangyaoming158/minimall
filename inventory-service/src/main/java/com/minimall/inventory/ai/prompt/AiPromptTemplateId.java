package com.minimall.inventory.ai.prompt;

public enum AiPromptTemplateId {
    INVENTORY_QA("ai/prompts/inventory-qa-v1.json"),
    LOW_STOCK_ANALYSIS("ai/prompts/low-stock-analysis-v1.json"),
    HOT_PRODUCTS_ANALYSIS("ai/prompts/hot-products-analysis-v1.json"),
    REPLENISHMENT_SUGGESTION("ai/prompts/replenishment-suggestion-v1.json");

    private final String resourcePath;

    AiPromptTemplateId(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String resourcePath() {
        return resourcePath;
    }
}
