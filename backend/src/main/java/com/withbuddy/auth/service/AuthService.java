package com.withbuddy.auth.service;

import com.withbuddy.activity.service.UserActivityLogService;
import com.withbuddy.auth.dto.request.LoginRequest;
import com.withbuddy.auth.dto.response.LoginResponse;
import com.withbuddy.auth.dto.response.LoginUserResponse;
import com.withbuddy.user.entity.User;
import com.withbuddy.auth.exception.LoginFailedException;
import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.global.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserActivityLogService userActivityLogService;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByCompany_CompanyCodeAndNameAndEmployeeNumber(
                request.getCompanyCode(),
                request.getName(),
                request.getEmployeeNumber()
        ).orElseThrow(() -> new LoginFailedException("입력하신 정보를 다시 확인해 주세요"));

        String accessToken = jwtService.generateAccessToken(user);

        userActivityLogService.saveLoginSessionStart(user.getId());

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