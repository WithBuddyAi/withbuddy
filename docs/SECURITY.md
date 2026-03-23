# WithBuddy 보안 설계

> 인증, 인가, 데이터 보호 및 보안 모범 사례

**최종 업데이트**: 2026-03-23  
**버전**: 1.1.2

---

## 목차

- [1. 보안 개요](#1-보안-개요)
- [2. 인증 (Authentication)](#2-인증-authentication)
- [3. 인가 (Authorization)](#3-인가-authorization)
- [4. 데이터 보안](#4-데이터-보안)
- [5. API 보안](#5-api-보안)
- [6. 네트워크 보안](#6-네트워크-보안)
- [7. 애플리케이션 보안](#7-애플리케이션-보안)
- [8. 보안 모니터링](#8-보안-모니터링)
- [9. Cloudflare/HTTPS/SSL 보안 설정](#9-cloudflarehttpsssl-보안-설정)

---

## 1. 보안 개요

### 1.1 보안 원칙

WithBuddy는 다음 보안 원칙을 따릅니다:

1. **Defense in Depth** (다층 방어)
   - 네트워크, 애플리케이션, 데이터 계층에서 다중 보안 조치

2. **Least Privilege** (최소 권한)
   - 사용자와 시스템에 필요한 최소한의 권한만 부여

3. **Zero Trust** (제로 트러스트)
   - 모든 요청을 검증, 내부 네트워크도 신뢰하지 않음

4. **Encryption Everywhere** (전방위 암호화)
   - 전송 중, 저장 시 모든 민감 데이터 암호화

### 1.2 보안 레이어

```
┌──────────────────────────────────────┐
│  1. Network Security                 │ ← VCN, Security Groups
├──────────────────────────────────────┤
│  2. Application Security             │ ← HTTPS, CORS, CSP
├──────────────────────────────────────┤
│  3. Authentication & Authorization   │ ← JWT, RBAC
├──────────────────────────────────────┤
│  4. Data Security                    │ ← Encryption, Hashing
├──────────────────────────────────────┤
│  5. Input Validation                 │ ← Sanitization, Validation
└──────────────────────────────────────┘
```

---

## 2. 인증 (Authentication)

### 2.1 JWT 기반 인증

#### 토큰 구조

**Access Token** (단기 수명):
```json
{
  "sub": "user-uuid-123",
  "companyCode": 1001,
  "companyId": 1,
  "employeeNumber": "20260001",
  "name": "김지원",
  "role": "EMPLOYEE",
  "iat": 1710000000,
  "exp": 1710007200
}
```

**Refresh Token** (장기 수명):
```json
{
  "sub": "user-uuid-123",
  "type": "refresh",
  "iat": 1710000000,
  "exp": 1710604800
}
```

#### 토큰 생성 (Backend)

```java
@Service
public class JwtService {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.access-expiration}")
    private Long accessExpiration;  // 2시간 (7200000ms)
    
    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;  // 7일 (604800000ms)
    
    public String generateAccessToken(User user) {
        return Jwts.builder()
            .setSubject(user.getId())
            .claim("companyCode", user.getCompany().getCompanyCode())
            .claim("companyId", user.getCompany().getId())
            .claim("employeeNumber", user.getEmployeeNumber())
            .claim("name", user.getName())
            .claim("role", user.getRole().name())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
            .signWith(SignatureAlgorithm.HS512, secret)
            .compact();
    }
    
    public String generateRefreshToken(User user) {
        return Jwts.builder()
            .setSubject(user.getId())
            .claim("type", "refresh")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
            .signWith(SignatureAlgorithm.HS512, secret)
            .compact();
    }
}
```

### 2.2 로그인 프로세스

```
[사용자]
   ↓ (1) 회사코드 + 사원번호 + 이름 입력
   {
     "companyCode": 1001,
     "employeeNumber": "20260001",
     "name": "김지원"
   }
[Frontend]
   ↓ (2) POST /api/v1/auth/login
[Backend]
   ↓ (3) Company 조회 (companyCode)
   ↓ (4) User 조회 (company_id, employee_number)
   ↓ (5) 이름 검증 (user.name == "김지원")
   ↓ (6) JWT 생성
   {
     "accessToken": "eyJ...",  // 2시간
     "refreshToken": "eyJ..."  // 7일
   }
[Frontend]
   ↓ (7) 저장
   - Access Token → localStorage
   - Refresh Token → httpOnly Cookie (권장)
[사용자]
```

#### 로그인 구현 (Backend)

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletResponse response
    ) {
        // 1. 인증
        User user = authService.authenticate(
            request.getCompanyCode(),
            request.getEmployeeNumber(),
            request.getName()
        );
        
        // 2. Token 생성
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        
        // 3. Refresh Token을 httpOnly Cookie에 저장
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);  // HTTPS only
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60);  // 7일
        response.addCookie(cookie);
        
        // 4. Access Token은 응답 본문에
        return ResponseEntity.ok(new LoginResponse(accessToken, user));
    }
}
```

### 2.3 Token Refresh 흐름

```
[Frontend] Access Token 만료 감지 (401 Unauthorized)
   ↓
[Frontend] POST /api/v1/auth/refresh
   ↓ Cookie: refresh_token=eyJ...
[Backend] 
   ↓ (1) Refresh Token 검증
   ↓ (2) Blacklist 확인 (로그아웃된 토큰인지)
   ↓ (3) 새 Access Token 생성
   ↓ (4) (선택) Refresh Token도 갱신 (Rotation)
[Frontend]
   ↓ Access Token 교체
   ↓ 실패한 요청 재시도
```

#### Token Refresh 구현

```java
@PostMapping("/refresh")
public ResponseEntity<TokenResponse> refresh(
    @CookieValue("refresh_token") String refreshToken
) {
    // 1. Refresh Token 검증
    if (!jwtService.validateToken(refreshToken)) {
        throw new UnauthorizedException("Invalid refresh token");
    }
    
    // 2. Blacklist 확인
    if (tokenBlacklistService.isBlacklisted(refreshToken)) {
        throw new UnauthorizedException("Token has been revoked");
    }
    
    // 3. 사용자 조회
    String userId = jwtService.getUserIdFromToken(refreshToken);
    User user = userService.findById(userId);
    
    // 4. 새 Access Token 생성
    String newAccessToken = jwtService.generateAccessToken(user);
    
    // 5. (선택) Refresh Token Rotation
    String newRefreshToken = jwtService.generateRefreshToken(user);
    tokenBlacklistService.add(refreshToken);  // 이전 토큰 무효화
    
    return ResponseEntity.ok(new TokenResponse(newAccessToken, newRefreshToken));
}
```

### 2.4 로그아웃

```java
@PostMapping("/logout")
public ResponseEntity<Void> logout(
    @RequestHeader("Authorization") String bearerToken,
    @CookieValue("refresh_token") String refreshToken
) {
    // 1. Access Token 추출
    String accessToken = bearerToken.replace("Bearer ", "");
    
    // 2. 두 토큰 모두 Blacklist 추가
    tokenBlacklistService.add(accessToken);
    tokenBlacklistService.add(refreshToken);
    
    // 3. Cookie 삭제
    Cookie cookie = new Cookie("refresh_token", null);
    cookie.setMaxAge(0);
    response.addCookie(cookie);
    
    return ResponseEntity.noContent().build();
}
```

#### Token Blacklist (Redis)

```java
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public void add(String token) {
        // Token 만료 시간까지만 저장
        long expiration = jwtService.getExpirationFromToken(token);
        long ttl = expiration - System.currentTimeMillis();
        
        redisTemplate.opsForValue().set(
            "blacklist:" + token,
            "revoked",
            ttl,
            TimeUnit.MILLISECONDS
        );
    }
    
    public boolean isBlacklisted(String token) {
        return redisTemplate.hasKey("blacklist:" + token);
    }
}
```

---

## 3. 인가 (Authorization)

### 3.1 역할 기반 접근 제어 (RBAC)

#### Role 정의

```java
public enum Role {
    EMPLOYEE,      // 일반 사원
    MENTOR,        // 멘토
    MANAGER,       // 매니저
    HR,            // 인사팀
    ADMIN          // 시스템 관리자
}
```

#### 권한 매트릭스

| API 엔드포인트 | EMPLOYEE | MENTOR | MANAGER | HR | ADMIN |
|---------------|----------|--------|---------|-------|-------|
| GET /api/v1/users/me | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/checklists | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/reports/{userId} | ❌ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/documents/templates | ❌ | ❌ | ❌ | ✅ | ✅ |
| DELETE /api/v1/users/{userId} | ❌ | ❌ | ❌ | ❌ | ✅ |

### 3.2 Spring Security 설정

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors()
            .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests()
                // Public endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Authenticated endpoints
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

### 3.3 메서드 수준 인가

```java
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    
    // 일반 사원: 자신의 리포트만
    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<?> getMyReports() {
        // ...
    }
    
    // 멘토/매니저: 타인 리포트 조회 가능
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('MENTOR', 'MANAGER', 'HR', 'ADMIN')")
    public ResponseEntity<?> getUserReports(@PathVariable String userId) {
        // ...
    }
    
    // HR만: 템플릿 생성
    @PostMapping("/templates")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<?> createTemplate(@RequestBody TemplateRequest request) {
        // ...
    }
    
    // ADMIN만: 리포트 삭제
    @DeleteMapping("/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteReport(@PathVariable String reportId) {
        // ...
    }
}
```

### 3.4 데이터 수준 접근 제어

```java
@Service
@RequiredArgsConstructor
public class RecordService {
    
    private final RecordRepository recordRepository;
    
    public Record getRecord(String recordId, String userId, Long companyId) {
        Record record = recordRepository.findById(recordId)
            .orElseThrow(() -> new NotFoundException("Record not found"));
        
        // 1. 회사 검증
        if (!record.getCompanyId().equals(companyId)) {
            throw new ForbiddenException("Access denied");
        }
        
        // 2. 소유자 검증 (일반 사원은 자신의 기록만)
        User user = userService.findById(userId);
        if (user.getRole() == Role.EMPLOYEE && !record.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied");
        }
        
        return record;
    }
}
```

---

## 4. 데이터 보안

### 4.1 암호화

#### 전송 중 암호화 (Encryption in Transit)

```yaml
# 필수 사용
Protocol: HTTPS (TLS 1.3)
Certificate: Let's Encrypt / Cloudflare SSL
Min TLS Version: 1.2
Cipher Suites: 
  - TLS_AES_128_GCM_SHA256
  - TLS_AES_256_GCM_SHA384
  - TLS_CHACHA20_POLY1305_SHA256
```

**Backend 설정**:
```yaml
# application.yml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: withbuddy
  http2:
    enabled: true
```

#### 저장 시 암호화 (Encryption at Rest)

**데이터베이스**:
```sql
-- MySQL Transparent Data Encryption (TDE)
ALTER TABLE users ENCRYPTION='Y';
ALTER TABLE records ENCRYPTION='Y';
ALTER TABLE documents ENCRYPTION='Y';
```

**Object Storage**:
```yaml
# S3 Server-Side Encryption
Encryption: AES-256
Key Management: AWS KMS
Bucket Policy:
  - Enforce encryption on all uploads
```

**민감 정보 필드 암호화**:
```java
@Entity
public class User {
    
    @Id
    private String id;
    
    @Convert(converter = StringEncryptionConverter.class)
    @Column(name = "employee_number")
    private String employeeNumber;  // 암호화 저장
    
    @Convert(converter = StringEncryptionConverter.class)
    private String phone;  // 암호화 저장
}

@Converter
public class StringEncryptionConverter implements AttributeConverter<String, String> {
    
    @Value("${encryption.secret}")
    private String secret;
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(attribute.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        // 복호화 구현
    }
}
```

### 4.2 비밀번호 보안

**MVP 메모**: 현재 로그인은 회사코드+사번+이름 기반이며, 비밀번호 인증을 도입할 경우 아래 항목을 적용한다.

#### 해싱 (BCrypt)

```java
@Service
public class PasswordService {
    
    private final BCryptPasswordEncoder encoder = 
        new BCryptPasswordEncoder(12);  // Work factor: 12
    
    public String hashPassword(String plainPassword) {
        return encoder.encode(plainPassword);
    }
    
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        return encoder.matches(plainPassword, hashedPassword);
    }
}
```

**비밀번호 정책**:
```java
@Pattern(
    regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
    message = "비밀번호는 8-20자, 영문, 숫자, 특수문자를 포함해야 합니다"
)
private String password;
```

---

## 5. API 보안

### 5.1 CORS (Cross-Origin Resource Sharing)

```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 허용 Origin
        configuration.setAllowedOrigins(Arrays.asList(
            "https://withbuddy.com",
            "https://www.withbuddy.com",
            "https://withbuddy.vercel.app"
        ));
        
        // 허용 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH"
        ));
        
        // 허용 헤더
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With"
        ));
        
        // 노출 헤더
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count"
        ));
        
        // Credentials 허용
        configuration.setAllowCredentials(true);
        
        // Preflight 캐싱 시간
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
```

### 5.2 Rate Limiting

#### 계층별 제한

| 엔드포인트 | 제한 | 목적 |
|-----------|------|------|
| `/api/v1/auth/login` | 5회/5분 | 무차별 대입 공격 방지 |
| `/api/v1/ai/**` | 30회/분 | AI API 비용 관리 |
| `/api/v1/documents/upload` | 10회/시간 | 스토리지 남용 방지 |
| 일반 API | 100회/분 | DDoS 방지 |

#### 구현 (Bucket4j + Redis)

```java
@Configuration
public class RateLimitConfig {
    
    @Bean
    public Bucket loginBucket() {
        // 5 requests per 5 minutes
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(5)));
        return Bucket4j.builder()
            .addLimit(limit)
            .build();
    }
}

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        String key = getClientKey(request);
        String bucket = redisTemplate.opsForValue().get(key);
        
        if (isRateLimited(bucket)) {
            response.setStatus(429);
            response.getWriter().write("Too many requests");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
```

### 5.3 Content Security Policy (CSP)

```java
@Configuration
public class SecurityHeadersConfig {
    
    @Bean
    public FilterRegistrationBean<CspFilter> cspFilter() {
        FilterRegistrationBean<CspFilter> registrationBean = new FilterRegistrationBean<>();
        
        registrationBean.setFilter(new CspFilter());
        registrationBean.addUrlPatterns("/*");
        
        return registrationBean;
    }
}

public class CspFilter implements Filter {
    
    @Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // CSP 헤더 설정
        httpResponse.setHeader(
            "Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "font-src 'self' data:; " +
            "connect-src 'self' https://api.withbuddy.com; " +
            "frame-ancestors 'none';"
        );
        
        // 기타 보안 헤더
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        
        chain.doFilter(request, response);
    }
}
```

---

## 6. 네트워크 보안

### 6.1 VCN 격리

```
Internet
  ↓ (HTTPS only)
Load Balancer (Public Subnet)
  ↓ (HTTP 8080)
Backend/AI Server (Private Subnet)
  ↓ (MySQL 3306)
Database (Private Subnet - 완전 격리)
```

**규칙**:
- ✅ Database는 인터넷 접근 불가
- ✅ Backend/AI는 NAT Gateway 통해서만 아웃바운드
- ✅ Load Balancer만 Public IP

### 6.2 보안 그룹 최소 권한

```yaml
MySQL Security Group:
  Inbound:
    - Port 3306 from Backend SG only
    - Port 3306 from AI Server SG only
  Outbound:
    - None (완전 차단)
```


---

## 7. 애플리케이션 보안

### 7.1 입력 검증

```java
public class UserRequest {
    
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 50, message = "이름은 2-50자여야 합니다")
    @Pattern(regexp = "^[가-힣a-zA-Z\\s]+$", message = "이름은 한글 또는 영문만 가능합니다")
    private String name;
    
    @NotBlank
    @Pattern(regexp = "^[0-9]{8}$", message = "사원번호는 8자리 숫자여야 합니다")
    private String employeeNumber;
    
    @Email(message = "유효한 이메일 주소여야 합니다")
    private String email;
}
```

### 7.2 SQL Injection 방지

```java
// ✅ GOOD: JPA Query Methods
List<User> findByCompanyIdAndEmployeeNumber(Long companyId, String employeeNumber);

// ✅ GOOD: JPQL with Parameters
@Query("SELECT u FROM User u WHERE u.company.id = :companyId AND u.employeeNumber = :empNum")
List<User> findUsers(@Param("companyId") Long companyId, @Param("empNum") String empNum);

// ❌ BAD: String concatenation
@Query("SELECT u FROM User u WHERE u.employeeNumber = '" + empNum + "'")  // NEVER DO THIS!
```

### 7.3 XSS 방지

```java
@Service
public class SanitizationService {
    
    private final Policy policy = new PolicyFactory()
        .allowElements("p", "br", "strong", "em")
        .toFactory();
    
    public String sanitize(String input) {
        return policy.sanitize(input);
    }
}

// Controller에서 사용
@PostMapping("/records")
public ResponseEntity<?> createRecord(@RequestBody RecordRequest request) {
    String sanitizedContent = sanitizationService.sanitize(request.getContent());
    // ...
}
```

---

## 8. 보안 모니터링

### 8.1 보안 이벤트 로깅

```java
@Aspect
@Component
public class SecurityAuditAspect {
    
    @AfterReturning("@annotation(PreAuthorize)")
    public void logAuthorization(JoinPoint joinPoint) {
        log.info("Authorization check passed for: {}", joinPoint.getSignature());
    }
    
    @AfterThrowing(pointcut = "@annotation(PreAuthorize)", throwing = "ex")
    public void logAuthorizationFailure(JoinPoint joinPoint, Exception ex) {
        log.warn("Authorization failed for: {}, reason: {}", 
            joinPoint.getSignature(), ex.getMessage());
    }
}
```

### 8.2 알림 규칙

```yaml
Critical Alerts:
  - 5번 이상 로그인 실패 (같은 IP)
  - 비정상적인 데이터 접근 패턴
  - SQL Injection 시도 감지
  - XSS 공격 시도 감지
  
Warning Alerts:
  - 3번 로그인 실패
  - Rate Limit 초과
  - 권한 없는 리소스 접근 시도
```

---

## 9. Cloudflare/HTTPS/SSL 보안 설정

### 9.1 Cloudflare 보안 설정 (권장)

- **SSL/TLS 모드**: `Full (strict)` 사용 (원본 서버에 유효한 인증서 필수)
- **Always Use HTTPS**: HTTP → HTTPS 강제 리다이렉트
- **HSTS**: `max-age=31536000; includeSubDomains; preload` 적용
- **Minimum TLS Version**: `TLS 1.2` 이상 (가능하면 1.3 우선)
- **WAF**: OWASP Core Ruleset 활성화
- **DDoS 방어**: L3/L4/L7 자동 보호 활성화, 대규모 공격 시 Under Attack Mode 사용
- **Rate Limiting**: 로그인/민감 API에 제한 정책 적용
- **Bot Fight Mode**: 기본 활성화 (트래픽 상황에 따라 튜닝)
- **Firewall Rules**: 국가/ASN/IP 기반 차단, 관리자 경로 접근 제한
- **Authenticated Origin Pulls**: 원본 인증 강화 (가능 시)

### 9.2 HTTPS/SSL 보안 설정 (서버/로드밸런서)

- **TLS 1.2/1.3만 허용**, 취약한 프로토콜/암호군 비활성화
- **HTTP/2 또는 HTTP/3** 활성화
- **OCSP Stapling** 활성화 (가능 시)
- **HSTS 헤더** 서버에서 중복 적용
- **80 → 443 리다이렉트** 필수
- **인증서 교체 자동화** (ACME/Cloudflare Origin Cert 등)

### 9.3 인증서 관리 원칙

- **개발/스테이징/운영 분리 인증서** 사용
- **키 접근 제한** (권한 최소화, 비밀관리 도구에 보관)
- **만료 30일 전 교체 알림** 설정 및 자동 갱신 설정
---

## 부록

### A. 보안 체크리스트

**인증/인가**:
- [ ] JWT 시크릿 키 안전하게 보관 (환경변수)
- [ ] Access Token 유효기간 짧게 (2시간)
- [ ] Refresh Token httpOnly Cookie 사용
- [ ] 로그아웃 시 Token Blacklist 추가

**데이터 보안**:
- [ ] HTTPS 적용 (TLS 1.3)
- [ ] 데이터베이스 TDE 활성화
- [ ] 민감 정보 필드 암호화 (AES-256)
- [ ] 비밀번호 BCrypt 해싱 (work factor 12)

**API 보안**:
- [ ] CORS 허용 Origin 제한
- [ ] Rate Limiting 적용
- [ ] CSP 헤더 설정
- [ ] 입력 검증 (모든 엔드포인트)

**네트워크 보안**:
- [ ] 데이터베이스 Private Subnet 격리
- [ ] 보안 그룹 최소 권한 원칙
- [ ] NAT Gateway 아웃바운드만

---

**문서 버전**: 1.1.2  
**작성일**: 2026-03-17
