package com.withbuddy.admin.user.service;

import com.withbuddy.admin.user.dto.request.CreateUserRequest;
import com.withbuddy.admin.user.dto.response.CreateUserResponse;
import com.withbuddy.admin.user.dto.response.UserListItemResponse;
import com.withbuddy.admin.user.dto.response.UserListResponse;
import com.withbuddy.admin.user.exception.DuplicateEmployeeNumberException;
import com.withbuddy.admin.user.repository.AdminUserRepository;
import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.company.repository.CompanyRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class AdminUserService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminUserRepository adminUserRepository;
    private final CompanyRepository companyRepository;

    public AdminUserService(
            AdminUserRepository adminUserRepository,
            CompanyRepository companyRepository
    ) {
        this.adminUserRepository = adminUserRepository;
        this.companyRepository = companyRepository;
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
            throw new DuplicateEmployeeNumberException();
        }

        return new CreateUserResponse(
                user.getId(),
                company.getCompanyCode(),
                company.getName(),
                user.getRole().name(),
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
            String teamName
    ) {
        String companyCode = requireAdminCompanyCode(principal);
        validatePageParameters(page, size);

        Page<User> userPage = adminUserRepository.searchUsers(
                companyCode,
                UserRole.USER,
                normalizeFilter(department, "department"),
                normalizeFilter(teamName, "teamName"),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return new UserListResponse(
                userPage.getContent().stream()
                        .map(this::toListItem)
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

    private UserListItemResponse toListItem(User user) {
        return new UserListItemResponse(
                user.getId(),
                user.getCompany().getCompanyCode(),
                user.getCompany().getName(),
                user.getEmployeeNumber(),
                user.getDepartment(),
                user.getTeamName(),
                user.getName(),
                user.getRole().name(),
                user.getHireDate(),
                calculateHireDay(user.getHireDate()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private long calculateHireDay(LocalDate hireDate) {
        return ChronoUnit.DAYS.between(hireDate, LocalDate.now(KST)) + 1;
    }
}
