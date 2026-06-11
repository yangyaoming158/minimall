package com.minimall.inventory.ai.prompt;

public enum AiPromptTemplateId {
    INVENTORY_QA("ai/prompts/inventory-qa-v2.json"),
    LOW_STOCK_ANALYSIS("ai/prompts/low-stock-analysis-v2.json"),
    HOT_PRODUCTS_ANALYSIS("ai/prompts/hot-products-analysis-v2.json"),
    REPLENISHMENT_SUGGESTION("ai/prompts/replenishment-suggestion-v2.json");

    private final String resourcePath;

    AiPromptTemplateId(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String resourcePath() {
        return resourcePath;
    }
}
