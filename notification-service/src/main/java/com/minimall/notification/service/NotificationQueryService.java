package com.minimall.notification.service;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.notification.domain.NotificationLog;
import com.minimall.notification.domain.NotificationLogStatus;
import com.minimall.notification.domain.NotificationType;
import com.minimall.notification.dto.AdminNotificationResponse;
import com.minimall.notification.repository.NotificationLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NotificationQueryService {

    private static final int MAX_ADMIN_NOTIFICATION_PAGE_SIZE = 100;

    private final NotificationLogRepository notificationLogRepository;

    public NotificationQueryService(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @Transactional(readOnly = true)
    public AdminNotificationResponse adminDetail(Long id) {
        NotificationLog log = notificationLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Notification log not found"));
        return AdminNotificationResponse.from(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationResponse> adminList(
            String eventId,
            String orderNo,
            NotificationLogStatus status,
            NotificationType channel,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "createdFrom must be before or equal to createdTo");
        }
        Specification<NotificationLog> specification =
                adminNotificationSpecification(eventId, orderNo, status, channel, createdFrom, createdTo);
        return PageResponse.from(notificationLogRepository.findAll(specification, boundedPageable(pageable))
                .map(AdminNotificationResponse::from));
    }

    private Pageable boundedPageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, Math.min(pageable.getPageSize(), MAX_ADMIN_NOTIFICATION_PAGE_SIZE));
        // Stable, deterministic newest-first ordering regardless of any client-supplied sort.
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private Specification<NotificationLog> adminNotificationSpecification(
            String eventId,
            String orderNo,
            NotificationLogStatus status,
            NotificationType channel,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(eventId)) {
                predicates.add(criteriaBuilder.equal(root.get("eventId"), eventId.trim()));
            }
            // orderNo maps to recipient: the consumer stores the order number there (see AdminNotificationResponse).
            if (StringUtils.hasText(orderNo)) {
                predicates.add(criteriaBuilder.equal(root.get("recipient"), orderNo.trim()));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            // channel maps to notificationType (the only categorical type today).
            if (channel != null) {
                predicates.add(criteriaBuilder.equal(root.get("notificationType"), channel));
            }
            if (createdFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
