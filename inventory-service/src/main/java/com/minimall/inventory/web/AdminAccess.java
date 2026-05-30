package com.minimall.inventory.web;

import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;

final class AdminAccess {

    private AdminAccess() {
    }

    static UserContext requireAdmin() {
        UserContext userContext = UserContextHolder.get()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Unauthorized"));
        if (!AuthRole.ADMIN.equals(userContext.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return userContext;
    }
}
