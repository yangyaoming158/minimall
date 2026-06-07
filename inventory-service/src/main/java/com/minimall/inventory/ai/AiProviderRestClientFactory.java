package com.minimall.inventory.ai;

import com.minimall.inventory.config.AiProviderProperties;
import java.util.Objects;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class AiProviderRestClientFactory {

    public RestClient create(AiProviderProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.requestTimeout());
        requestFactory.setReadTimeout(properties.requestTimeout());
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory);
        if (properties.hasBaseUrl()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        return builder.build();
    }
}
