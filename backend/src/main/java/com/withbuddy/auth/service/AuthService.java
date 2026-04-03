package com.withbuddy.auth.service;

import com.withbuddy.auth.dto.LoginRequest;
import com.withbuddy.auth.dto.LoginResponse;
import com.withbuddy.auth.entity.User;
import com.withbuddy.auth.exception.LoginFailedException;
import com.withbuddy.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByCompany_CompanyCodeAndNameAndEmployeeNumber(
                request.getCompanyCode(),
                request.getName(),
                request.getEmployeeNumber()
        ).orElseThrow(() -> new LoginFailedException("회사 코드, 사번 또는 이름이 올바르지 않습니다."));

        return new LoginResponse(
                user.getId(),
                user.getCompany().getCompanyCode(),
                user.getCompany().getName(),
                user.getEmployeeNumber(),
                user.getName(),
                user.getHireDate()
        );
    }
}