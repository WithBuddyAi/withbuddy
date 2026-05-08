package com.withbuddy.user.service;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.company.entity.Company;
import com.withbuddy.company.repository.CompanyRepository;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.user.dto.CreateUserRequest;
import com.withbuddy.user.dto.CreateUserResponse;
import com.withbuddy.user.entity.User;
import com.withbuddy.user.entity.UserRole;
import com.withbuddy.user.exception.DuplicateEmployeeNumberException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    public UserService(
            UserRepository userRepository,
            CompanyRepository companyRepository
    ) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public CreateUserResponse createUser(
            JwtAuthenticationPrincipal principal,
            CreateUserRequest request
    ) {
        String companyCode = principal.companyCode();

        if (companyCode == null || companyCode.isBlank()) {
            throw new UnauthorizedException("사용자 회사 정보를 확인할 수 없습니다.");
        }

        User currentUser = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));

        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "관리자 권한이 필요한 API입니다.");
        }

        if (!currentUser.getCompany().getCompanyCode().equals(companyCode)) {
            throw new UnauthorizedException("사용자 회사 정보가 일치하지 않습니다.");
        }

        String normalizedName = request.getName().trim();
        String normalizedEmployeeNumber = request.getEmployeeNumber().trim();

        Company company = companyRepository.findByCompanyCode(companyCode)
                .orElseThrow(() -> new UnauthorizedException("사용자 회사 정보를 확인할 수 없습니다."));

        if (userRepository.existsByCompany_CompanyCodeAndEmployeeNumber(companyCode, normalizedEmployeeNumber)) {
            throw new DuplicateEmployeeNumberException();
        }

        User user;
        try {
            user = userRepository.save(User.createUser(
                    company,
                    normalizedName,
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
                user.getEmployeeNumber(),
                user.getName(),
                user.getHireDate(),
                user.getCreatedAt()
        );
    }
}
