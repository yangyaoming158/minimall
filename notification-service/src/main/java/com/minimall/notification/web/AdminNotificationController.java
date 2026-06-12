package com.minimall.notification.web;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.notification.domain.NotificationLogStatus;
import com.minimall.notification.domain.NotificationType;
import com.minimall.notification.dto.AdminNotificationResponse;
import com.minimall.notification.service.NotificationQueryService;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationQueryService notificationQueryService;

    public AdminNotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminNotificationResponse>> list(
            @RequestParam(name = "eventId", required = false) String eventId,
            @RequestParam(name = "orderNo", required = false) String orderNo,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "channel", required = false) String channel,
            @RequestParam(name = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdFrom,
            @RequestParam(name = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdTo,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(notificationQueryService.adminList(
                eventId, orderNo, parseStatus(status), parseChannel(channel), createdFrom, createdTo, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminNotificationResponse> detail(@PathVariable("id") Long id) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(notificationQueryService.adminDetail(id));
    }

    private NotificationLogStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return NotificationLogStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid status", exception);
        }
    }

    private NotificationType parseChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return null;
        }
        try {
            return NotificationType.valueOf(channel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid channel", exception);
        }
    }
}
