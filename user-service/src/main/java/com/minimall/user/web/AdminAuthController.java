package com.minimall.user.web;

import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.user.dto.CurrentUserResponse;
import com.minimall.user.dto.LoginRequest;
import com.minimall.user.dto.LoginResponse;
import com.minimall.user.service.UserAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final UserAuthService userAuthService;

    public AdminAuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(userAuthService.adminLogin(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        UserContext userContext = UserContextHolder.get()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Unauthorized"));
        if (!AuthRole.ADMIN.equals(userContext.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return ApiResponse.success(CurrentUserResponse.from(userContext));
    }
}
