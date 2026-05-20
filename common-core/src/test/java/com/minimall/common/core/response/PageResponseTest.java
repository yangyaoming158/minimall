package com.minimall.common.core.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fromMapsSpringPageMetadata() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(
                List.of("first", "second"),
                PageRequest.of(2, 2),
                7));

        assertEquals(List.of("first", "second"), response.content());
        assertEquals(2, response.page());
        assertEquals(2, response.size());
        assertEquals(7, response.totalElements());
        assertEquals(4, response.totalPages());
    }

    @Test
    void serializesInsideApiResponse() throws Exception {
        PageResponse<String> page = new PageResponse<>(List.of("sku-1"), 0, 10, 1, 1);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(ApiResponse.success(page)));

        assertTrue(json.get("success").asBoolean());
        assertEquals(ErrorCode.SUCCESS.getCode(), json.get("code").asText());
        assertEquals("sku-1", json.get("data").get("content").get(0).asText());
        assertEquals(0, json.get("data").get("page").asInt());
        assertEquals(10, json.get("data").get("size").asInt());
        assertEquals(1, json.get("data").get("totalElements").asLong());
        assertEquals(1, json.get("data").get("totalPages").asInt());
    }
}
