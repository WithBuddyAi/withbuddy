package com.withbuddy.admin.organization.service;

import com.withbuddy.account.user.entity.User;
import com.withbuddy.admin.organization.dto.response.OrganizationOptionDepartmentResponse;
import com.withbuddy.admin.organization.dto.response.OrganizationOptionsResponse;
import com.withbuddy.admin.organization.repository.AdminOrganizationOptionRepository;
import com.withbuddy.admin.user.repository.AdminUserRepository;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminOrganizationOptionService {

    private final AdminOrganizationOptionRepository organizationOptionRepository;
    private final AdminUserRepository adminUserRepository;

    public AdminOrganizationOptionService(
            AdminOrganizationOptionRepository organizationOptionRepository,
            AdminUserRepository adminUserRepository
    ) {
        this.organizationOptionRepository = organizationOptionRepository;
        this.adminUserRepository = adminUserRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationOptionsResponse getOrganizationOptions(JwtAuthenticationPrincipal principal) {
        String companyCode = requireAdminCompanyCode(principal);

        Map<String, Set<String>> teamNamesByDepartment = new LinkedHashMap<>();
        organizationOptionRepository.findOrganizationOptions(companyCode).forEach(option -> {
            teamNamesByDepartment
                    .computeIfAbsent(option.getDepartment(), ignored -> new LinkedHashSet<>());

            if (StringUtils.hasText(option.getTeamName())) {
                teamNamesByDepartment.get(option.getDepartment()).add(option.getTeamName());
            }
        });

        List<OrganizationOptionDepartmentResponse> departments = new ArrayList<>();
        teamNamesByDepartment.forEach((department, teamNames) ->
                departments.add(new OrganizationOptionDepartmentResponse(
                        department,
                        List.copyOf(teamNames)
                ))
        );

        return new OrganizationOptionsResponse(departments);
    }

    private String requireAdminCompanyCode(JwtAuthenticationPrincipal principal) {
        String companyCode = principal.companyCode();

        if (!StringUtils.hasText(companyCode)) {
            throw new UnauthorizedException("사용자 회사 정보를 확인할 수 없습니다.");
        }

        User currentUser = adminUserRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));

        if (!currentUser.isActiveAdmin()) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "활성 관리자 권한이 필요한 API입니다.");
        }

        if (!currentUser.getCompany().getCompanyCode().equals(companyCode)) {
            throw new UnauthorizedException("사용자 회사 정보가 일치하지 않습니다.");
        }

        return companyCode;
    }
}
