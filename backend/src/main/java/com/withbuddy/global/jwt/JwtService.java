package com.withbuddy.global.jwt;

import com.withbuddy.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private Long accessExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = resolveKeyBytes(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] resolveKeyBytes(String secretValue) {
        try {
            return Decoders.BASE64.decode(secretValue);
        } catch (Exception ignored) {
            byte[] raw = secretValue.getBytes(StandardCharsets.UTF_8);
            if (raw.length >= 32) {
                return raw;
            }
            try {
                return MessageDigest.getInstance("SHA-256").digest(raw);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm is not available", e);
            }
        }
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("companyCode", user.getCompany().getCompanyCode())
                .claim("employeeNumber", user.getEmployeeNumber())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public String getCompanyCode(String token) {
        return getClaims(token).get("companyCode", String.class);
    }

    public String getEmployeeNumber(String token) {
        return getClaims(token).get("employeeNumber", String.class);
    }
}
