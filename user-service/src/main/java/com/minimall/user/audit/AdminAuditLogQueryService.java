package com.minimall.user.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditLogQuery;
import com.minimall.common.core.audit.AdminAuditLogResponse;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAuditLogQueryService {

    private static final String INVALID_RANGE_MESSAGE = "createdTo must not be before createdFrom";
    private static final String INVALID_SNAPSHOT_MESSAGE = "Invalid admin audit snapshot";

    private final AdminOperationLogRepository adminOperationLogRepository;
    private final ObjectMapper objectMapper;

    public AdminAuditLogQueryService(
            AdminOperationLogRepository adminOperationLogRepository,
            ObjectMapper objectMapper) {
        this.adminOperationLogRepository = Objects.requireNonNull(
                adminOperationLogRepository,
                "adminOperationLogRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogResponse> query(AdminAuditLogQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.createdFrom() != null && query.createdTo() != null
                && query.createdTo().isBefore(query.createdFrom())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_RANGE_MESSAGE);
        }

        PageRequest pageRequest = PageRequest.of(
                query.page(),
                query.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(adminOperationLogRepository.findAll(toSpecification(query), pageRequest)
                .map(this::toResponse));
    }

    private Specification<AdminOperationLog> toSpecification(AdminAuditLogQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.adminUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("adminUserId"), query.adminUserId()));
            }
            if (query.action() != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), query.action()));
            }
            if (query.resourceType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("resourceType"), query.resourceType()));
            }
            if (StringUtils.hasText(query.resourceId())) {
                predicates.add(criteriaBuilder.equal(root.get("resourceId"), query.resourceId()));
            }
            if (StringUtils.hasText(query.requestId())) {
                predicates.add(criteriaBuilder.equal(root.get("requestId"), query.requestId()));
            }
            if (query.sourceType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceType"), query.sourceType()));
            }
            if (StringUtils.hasText(query.referenceNo())) {
                predicates.add(criteriaBuilder.equal(root.get("referenceNo"), query.referenceNo()));
            }
            if (query.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        LocalDateTime.ofInstant(query.createdFrom(), ZoneOffset.UTC)));
            }
            if (query.createdTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("createdAt"),
                        LocalDateTime.ofInstant(query.createdTo(), ZoneOffset.UTC)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AdminAuditLogResponse toResponse(AdminOperationLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAdminUserId(),
                log.getAdminUsername(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getRequestId(),
                log.getSourceType(),
                log.getReferenceNo(),
                readSnapshot(log.getBeforeSnapshot()),
                readSnapshot(log.getAfterSnapshot()),
                log.getIp(),
                log.getUserAgent(),
                log.getSummary(),
                log.getCreatedAt().atZone(ZoneOffset.UTC).toInstant());
    }

    private JsonNode readSnapshot(String snapshot) {
        if (!StringUtils.hasText(snapshot)) {
            return null;
        }
        try {
            return objectMapper.readTree(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, INVALID_SNAPSHOT_MESSAGE, exception);
        }
    }
}
