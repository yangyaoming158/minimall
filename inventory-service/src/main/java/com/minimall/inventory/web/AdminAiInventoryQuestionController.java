package com.minimall.inventory.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.inventory.dto.AiInventoryAskRequest;
import com.minimall.inventory.dto.AiInventoryAskResponse;
import com.minimall.inventory.service.AiInventoryQuestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/inventory")
public class AdminAiInventoryQuestionController {

    private final AiInventoryQuestionService questionService;

    public AdminAiInventoryQuestionController(AiInventoryQuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/ask")
    public ApiResponse<AiInventoryAskResponse> ask(@Valid @RequestBody AiInventoryAskRequest request) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(questionService.answer(request));
    }
}
