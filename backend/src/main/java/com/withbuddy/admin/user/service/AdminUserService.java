package com.withbuddy.admin.user.service;

import com.withbuddy.admin.activity.entity.EventTarget;
import com.withbuddy.admin.activity.repository.UserActivityLogRepository;
import com.withbuddy.admin.user.dto.request.CreateUserRequest;
import com.withbuddy.admin.user.dto.response.CreateUserResponse;
import com.withbuddy.admin.user.dto.response.UserListItemResponse;
import com.withbuddy.admin.user.dto.response.UserListResponse;
import com.withbuddy.admin.user.exception.DuplicateEmployeeNumberException;
import com.withbuddy.admin.user.repository.AdminUserRepository;
import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.company.repository.CompanyRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.buddy.chat.entity.MessageType;
import com.withbuddy.buddy.chat.entity.SenderType;
import com.withbuddy.buddy.chat.repository.ChatMessageRepository;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String USER_COMPANY_EMPLOYEE_UNIQUE_CONSTRAINT = "uq_users_company_employee";
    private static final List<String> USER_SORT_FIELDS = List.of("name", "employeeNumber", "hireDate");
    private static final List<String> SORT_DIRECTIONS = List.of("asc", "desc");

    private final AdminUserRepository adminUserRepository;
    private final CompanyRepository companyRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserActivityLogRepository userActivityLogRepository;

    public AdminUserService(
            AdminUserRepository adminUserRepository,
            CompanyRepository companyRepository,
            ChatMessageRepository chatMessageRepository,
            UserActivityLogRepository userActivityLogRepository
    ) {
        this.adminUserRepository = adminUserRepository;
        this.companyRepository = companyRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userActivityLogRepository = userActivityLogRepository;
    }

    @Transactional
    public CreateUserResponse createUser(
            JwtAuthenticationPrincipal principal,
            CreateUserRequest request
    ) {
        String companyCode = requireAdminCompanyCode(principal);

        String normalizedName = request.getName().trim();
        String normalizedEmployeeNumber = request.getEmployeeNumber().trim();
        String normalizedDepartment = request.getDepartment().trim();
        String normalizedTeamName = request.getTeamName().trim();

        Company company = companyRepository.findByCompanyCode(companyCode)
                .orElseThrow(() -> new UnauthorizedException("사용자 회사 정보를 확인할 수 없습니다."));

        if (adminUserRepository.existsByCompany_CompanyCodeAndEmployeeNumber(companyCode, normalizedEmployeeNumber)) {
            throw new DuplicateEmployeeNumberException();
        }

        User user;
        try {
            user = adminUserRepository.save(User.createUser(
                    company,
                    normalizedName,
                    normalizedDepartment,
                    normalizedTeamName,
                    normalizedEmployeeNumber,
                    request.getHireDate()
            ));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateEmployeeNumberConstraint(e)) {
                throw new DuplicateEmployeeNumberException();
            }
            throw e;
        }

        return new CreateUserResponse(
                user.getId(),
                company.getCompanyCode(),
                company.getName(),
                user.getRole().name(),
                user.getAccountStatus().name(),
                user.getName(),
                user.getDepartment(),
                user.getTeamName(),
                user.getEmployeeNumber(),
                user.getHireDate(),
                user.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public UserListResponse getUsers(
            JwtAuthenticationPrincipal principal,
            int page,
            int size,
            String department,
            String teamName,
            String sortBy,
            String sortDirection
    ) {
        String companyCode = requireAdminCompanyCode(principal);
        validatePageParameters(page, size);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDirection = normalizeSortDirection(sortDirection);

        Page<User> userPage = adminUserRepository.searchUsers(
                companyCode,
                List.of(UserRole.USER),
                List.of(UserAccountStatus.ACTIVE),
                List.of(UserAccountStatus.READ_ONLY),
                List.of(UserAccountStatus.INACTIVE),
                normalizeFilter(department, "department"),
                normalizeFilter(teamName, "teamName"),
                normalizedSortBy,
                normalizedSortDirection,
                PageRequest.of(page, size)
        );

        Map<Long, LocalDate> lastLoginDateByUserId = resolveLastLoginDateByUserId(userPage.getContent());

        return new UserListResponse(
                userPage.getContent().stream()
                        .map(user -> toListItem(user, lastLoginDateByUserId.get(user.getId())))
                        .toList(),
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isFirst(),
                userPage.isLast()
        );
    }

    private String requireAdminCompanyCode(JwtAuthenticationPrincipal principal) {
        String companyCode = principal.companyCode();

        if (!StringUtils.hasText(companyCode)) {
            throw new UnauthorizedException("사용자 회사 정보를 확인할 수 없습니다.");
        }

        User currentUser = adminUserRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));

        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "관리자 권한이 필요한 API입니다.");
        }

        if (!currentUser.getCompany().getCompanyCode().equals(companyCode)) {
            throw new UnauthorizedException("사용자 회사 정보가 일치하지 않습니다.");
        }

        return companyCode;
    }

    private void validatePageParameters(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상의 정수여야 합니다.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상의 정수여야 합니다.");
        }
    }

    private String normalizeFilter(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "은 공백만 입력할 수 없습니다.");
        }
        return value.trim();
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null) {
            return null;
        }
        String normalizedSortBy = sortBy.trim();
        if (!StringUtils.hasText(normalizedSortBy)) {
            throw new IllegalArgumentException("sortBy는 공백만 입력할 수 없습니다.");
        }
        if (!USER_SORT_FIELDS.contains(normalizedSortBy)) {
            throw new IllegalArgumentException("sortBy는 name, employeeNumber, hireDate 중 하나여야 합니다.");
        }
        return normalizedSortBy;
    }

    private String normalizeSortDirection(String sortDirection) {
        if (sortDirection == null) {
            return "asc";
        }
        String normalizedSortDirection = sortDirection.trim().toLowerCase();
        if (!StringUtils.hasText(normalizedSortDirection)) {
            throw new IllegalArgumentException("sortDirection은 공백만 입력할 수 없습니다.");
        }
        if (!SORT_DIRECTIONS.contains(normalizedSortDirection)) {
            throw new IllegalArgumentException("sortDirection은 asc 또는 desc여야 합니다.");
        }
        return normalizedSortDirection;
    }

    private UserListItemResponse toListItem(User user, LocalDate lastLoginDate) {
        return new UserListItemResponse(
                user.getId(),
                user.getCompany().getCompanyCode(),
                user.getCompany().getName(),
                user.getEmployeeNumber(),
                formatDepartmentTeam(user.getDepartment(), user.getTeamName()),
                user.getName(),
                user.getRole().name(),
                user.getAccountStatus() == null ? null : user.getAccountStatus().name(),
                user.getHireDate(),
                calculateHireDay(user.getHireDate()),
                countUserQuestions(user.getId()),
                lastLoginDate,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private Map<Long, LocalDate> resolveLastLoginDateByUserId(List<User> users) {
        List<Long> userIds = users.stream()
                .map(User::getId)
                .toList();

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userActivityLogRepository.findLastLoginLogsByUserIdIn(userIds, EventTarget.LOGIN).stream()
                .collect(Collectors.toMap(
                        UserActivityLogRepository.LastLoginLogProjection::getUserId,
                        projection -> projection.getLastLoginAt().toLocalDate(),
                        (existing, replacement) -> existing
                ));
    }

    private String formatDepartmentTeam(String department, String teamName) {
        return department + "(" + teamName + ")";
    }

    private long countUserQuestions(Long userId) {
        return chatMessageRepository.countByUserIdAndSenderTypeAndMessageType(
                userId,
                SenderType.USER,
                MessageType.user_question
        );
    }

    private long calculateHireDay(LocalDate hireDate) {
        return ChronoUnit.DAYS.between(hireDate, LocalDate.now(KST)) + 1;
    }

    private boolean isDuplicateEmployeeNumberConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                    && isUserCompanyEmployeeConstraint(constraintViolationException.getConstraintName())) {
                return true;
            }

            if (isUserCompanyEmployeeConstraint(cause.getMessage())) {
                return true;
            }

            cause = cause.getCause();
        }
        return false;
    }

    private boolean isUserCompanyEmployeeConstraint(String value) {
        return value != null
                && value.toLowerCase().contains(USER_COMPANY_EMPLOYEE_UNIQUE_CONSTRAINT.toLowerCase());
    }
}
