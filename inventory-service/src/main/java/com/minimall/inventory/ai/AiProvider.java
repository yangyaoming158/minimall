package com.minimall.inventory.ai;

public interface AiProvider {

    AiProviderType providerType();

    AiProviderResponse generate(AiProviderRequest request);
}
