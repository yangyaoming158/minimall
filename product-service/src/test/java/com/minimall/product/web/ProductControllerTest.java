package com.minimall.product.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.product.domain.Product;
import com.minimall.product.domain.ProductStatus;
import com.minimall.product.dto.CreateProductRequest;
import com.minimall.product.dto.ProductResponse;
import com.minimall.product.dto.UpdateProductRequest;
import com.minimall.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:product_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=",
        "spring.data.redis.repositories.enabled=false"
})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        productRepository.deleteAll();
        reset(redisTemplate);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Test
    void createProductReturnsApiResponse() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                "SKU-2001",
                "Wireless Keyboard",
                "Low profile keyboard",
                "https://cdn.example.com/products/sku-2001.png",
                new BigDecimal("129.90"));

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-2001"))
                .andExpect(jsonPath("$.data.name").value("Wireless Keyboard"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/products/sku-2001.png"))
                .andExpect(jsonPath("$.data.price").value(129.90))
                .andExpect(jsonPath("$.data.status").value("ON_SHELF"));
    }

    @Test
    void duplicateProductIdReturnsConflict() throws Exception {
        productRepository.saveAndFlush(new Product("SKU-2002", "Mouse", null, new BigDecimal("39.90")));
        CreateProductRequest request = new CreateProductRequest(
                "SKU-2002",
                "Mouse 2",
                null,
                null,
                new BigDecimal("49.90"));

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("Product already exists"));
    }

    @Test
    void updateDetailAndListReturnPersistedFields() throws Exception {
        productRepository.saveAndFlush(new Product("SKU-2003", "Old Name", "Old", new BigDecimal("19.90")));
        UpdateProductRequest request = new UpdateProductRequest(
                "New Name",
                "New description",
                "https://cdn.example.com/products/sku-2003-new.png",
                new BigDecimal("29.90"));

        mockMvc.perform(put("/api/products/SKU-2003")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-2003"))
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.description").value("New description"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/products/sku-2003-new.png"))
                .andExpect(jsonPath("$.data.price").value(29.90));

        mockMvc.perform(get("/api/products/SKU-2003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-2003"))
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/products/sku-2003-new.png"))
                .andExpect(jsonPath("$.data.status").value("ON_SHELF"));

        mockMvc.perform(get("/api/products")
                        .param("status", "ON_SHELF")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-2003"))
                .andExpect(jsonPath("$.data.content[0].name").value("New Name"))
                .andExpect(jsonPath("$.data.content[0].imageUrl")
                        .value("https://cdn.example.com/products/sku-2003-new.png"));
    }

    @Test
    void missingProductReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/products/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void shelfOperationsSwitchStatusAndRejectInvalidTransition() throws Exception {
        productRepository.saveAndFlush(new Product("SKU-2004", "Lamp", null, new BigDecimal("69.00")));

        mockMvc.perform(post("/api/products/SKU-2004/off-shelf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-2004"))
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));

        mockMvc.perform(post("/api/products/SKU-2004/off-shelf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Product is already off shelf"));

        mockMvc.perform(post("/api/products/SKU-2004/on-shelf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-2004"))
                .andExpect(jsonPath("$.data.status").value("ON_SHELF"));
    }

    @Test
    void internalDetailReturnsProductStatusForOrderService() throws Exception {
        Product product = new Product("SKU-2005", "Monitor", "27 inch", new BigDecimal("899.00"));
        product.offShelf();
        productRepository.saveAndFlush(product);

        mockMvc.perform(get("/internal/products/SKU-2005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value("SKU-2005"))
                .andExpect(jsonPath("$.data.name").value("Monitor"))
                .andExpect(jsonPath("$.data.price").value(899.00))
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));
    }

    @Test
    void detailWritesCacheAndReturnsCachedValueOnHit() throws Exception {
        productRepository.saveAndFlush(new Product("SKU-2006", "Headphones", "Bluetooth", new BigDecimal("199.00")));
        ProductResponse cachedProduct = new ProductResponse(
                "SKU-2006",
                "Cached Headphones",
                "Cached description",
                "https://cdn.example.com/products/sku-2006-cached.png",
                new BigDecimal("188.00"),
                ProductStatus.OFF_SHELF,
                null,
                null);
        when(valueOperations.get("product:detail:SKU-2006"))
                .thenReturn(null, objectMapper.writeValueAsString(cachedProduct));

        mockMvc.perform(get("/api/products/SKU-2006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-2006"))
                .andExpect(jsonPath("$.data.name").value("Headphones"))
                .andExpect(jsonPath("$.data.status").value("ON_SHELF"));

        mockMvc.perform(get("/api/products/SKU-2006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productId").value("SKU-2006"))
                .andExpect(jsonPath("$.data.name").value("Cached Headphones"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/products/sku-2006-cached.png"))
                .andExpect(jsonPath("$.data.price").value(188.00))
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));

        verify(valueOperations, times(2)).get("product:detail:SKU-2006");
        verify(valueOperations).set(eq("product:detail:SKU-2006"), anyString(), eq(Duration.ofSeconds(300)));
    }

    @Test
    void updateEvictsProductDetailCacheAfterCommit() throws Exception {
        productRepository.saveAndFlush(new Product("SKU-2007", "Tablet", "Old", new BigDecimal("399.00")));
        UpdateProductRequest request = new UpdateProductRequest(
                "Tablet Pro",
                "New",
                "https://cdn.example.com/products/sku-2007.png",
                new BigDecimal("499.00"));

        mockMvc.perform(put("/api/products/SKU-2007")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Tablet Pro"));

        verify(redisTemplate).delete("product:detail:SKU-2007");
    }

    @Test
    void internalDetailReusesCachedProductDetail() throws Exception {
        productRepository.saveAndFlush(new Product("SKU-2008", "Speaker", "Portable", new BigDecimal("159.00")));
        ProductResponse cachedProduct = new ProductResponse(
                "SKU-2008",
                "Cached Speaker",
                "Cached internal detail",
                "https://cdn.example.com/products/sku-2008-cached.png",
                new BigDecimal("149.00"),
                ProductStatus.OFF_SHELF,
                null,
                null);
        when(valueOperations.get("product:detail:SKU-2008"))
                .thenReturn(objectMapper.writeValueAsString(cachedProduct));

        mockMvc.perform(get("/internal/products/SKU-2008"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value("SKU-2008"))
                .andExpect(jsonPath("$.data.name").value("Cached Speaker"))
                .andExpect(jsonPath("$.data.price").value(149.00))
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));

        verify(valueOperations).get("product:detail:SKU-2008");
    }
}
