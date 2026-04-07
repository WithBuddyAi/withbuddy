package com.withbuddy.auth.service;

import com.withbuddy.auth.dto.request.LoginRequest;
import com.withbuddy.auth.dto.response.LoginResponse;
import com.withbuddy.auth.dto.response.LoginUserResponse;
import com.withbuddy.users.entity.User;
import com.withbuddy.auth.exception.LoginFailedException;
import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.global.jwt.JwtService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByCompany_CompanyCodeAndNameAndEmployeeNumber(
                request.getCompanyCode(),
                request.getName(),
                request.getEmployeeNumber()
        ).orElseThrow(() -> new LoginFailedException("회사 코드, 사번 또는 이름이 올바르지 않습니다."));

        String accessToken = jwtService.generateAccessToken(user);

        LoginUserResponse userResponse = new LoginUserResponse(
                user.getId(),
                user.getCompany().getCompanyCode(),
                user.getCompany().getName(),
                user.getEmployeeNumber(),
                user.getName(),
                user.getHireDate()
        );

        return new LoginResponse(accessToken, userResponse);
    }
}