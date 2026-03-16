API 인증 및 토큰 가이드 (AUTH_GUIDE.md)
WithBuddy API 호출을 위한 JWT 인증 및 멀티 테넌시 식별 완벽 가이드

📋 목차
인증 방식 개요
로그인 및 토큰 발급
API 호출 시 토큰 사용법
JWT 페이로드 구조
토큰 갱신 (Refresh)
에러 처리
보안 고려사항
구현 예제
FAQ
🔑 인증 방식 개요
WithBuddy는 모든 API 요청에 대해 **JWT (JSON Web Token)**를 사용한 상태가 없는(Stateless) 인증 방식을 채택합니다. 각 토큰은 사용자의 권한뿐만 아니라 소속된 회사(Tenant) 정보를 포함하고 있습니다.

주요 특징
yaml
코드 복사
인증 방식: JWT (JSON Web Token)
토큰 타입: Bearer Token
저장 위치:
  - Access Token: localStorage / sessionStorage (프론트엔드)
  - Refresh Token: HttpOnly Cookie (보안)
만료 시간:
  - Access Token: 24시간
  - Refresh Token: 7일
멀티 테넌시: 회사 코드(companyCode) 기반 데이터 격리
인증 플로우
scss
코드 복사
┌─────────┐         ┌─────────┐         ┌─────────┐
│ Client  │         │ Backend │         │   AI    │
└────┬────┘         └────┬────┘         └────┬────┘
     │                   │                   │
     │ 1. POST /login    │                   │
     │ (회사코드+사번)    │                   │
     ├──────────────────>│                   │
     │                   │                   │
     │ 2. JWT 발급       │                   │
     │<──────────────────┤                   │
     │                   │                   │
     │ 3. API 요청       │                   │
     │ (Bearer Token)    │                   │
     ├──────────────────>│                   │
     │                   │                   │
     │                   │ 4. 토큰 검증     │
     │                   │    (회사코드 확인)│
     │                   │                   │
     │                   │ 5. AI API 호출   │
     │                   ├──────────────────>│
     │                   │                   │
     │ 6. 응답           │ 6. AI 응답        │
     │<──────────────────┤<──────────────────┤
1. 로그인 및 토큰 발급
1.1 로그인 API
사용자는 회사 코드와 사번을 통해 인증을 진행하고 Access Token을 획득합니다.

Endpoint
http
코드 복사
POST /api/v1/auth/login
Request Headers
http
코드 복사
Content-Type: application/json
Request Body
json
코드 복사
{
  "companyCode": 1001,
  "employeeNumber": "20260001",
  "name": "김지원"
}
필드	타입	필수	설명
companyCode	Number	✅	회사 고유 식별 코드
employeeNumber	String	✅	사원 번호 (사번)
name	String	✅	사용자 이름 (검증용)
Response (200 OK)
json
코드 복사
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "uuid-1234-5678-90ab-cdef",
      "name": "김지원",
      "employeeNumber": "20260001",
      "companyCode": 1001,
      "companyName": "위드버디",
      "department": "개발팀",
      "role": "USER",
      "profileImage": "https://cdn.withbuddy.com/profiles/default.png"
    }
  },
  "timestamp": "2025-01-20T10:30:00Z"
}
필드	설명
accessToken	API 요청에 사용할 JWT 토큰 (24시간 유효)
refreshToken	토큰 갱신용 (7일 유효, HttpOnly 쿠키로도 전송됨)
tokenType	토큰 타입 (항상 "Bearer")
expiresIn	Access Token 만료 시간 (초 단위)
user	로그인한 사용자 정보
Error Response (401 Unauthorized)
json
코드 복사
{
  "success": false,
  "error": {
    "code": "AUTH_001",
    "message": "유효하지 않은 회사 코드 또는 사번입니다.",
    "details": {
      "companyCode": 1001,
      "employeeNumber": "20260001"
    }
  },
  "timestamp": "2025-01-20T10:30:00Z"
}
1.2 발급된 토큰 저장
Frontend (React)
typescript
코드 실행
코드 복사
// 로그인 성공 후 토큰 저장
const handleLogin = async (credentials) => {
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials)
  });
  
  const result = await response.json();
  
  if (result.success) {
    // Access Token: localStorage에 저장 (또는 sessionStorage)
    localStorage.setItem('accessToken', result.data.accessToken);
    
    // Refresh Token은 이미 HttpOnly Cookie로 설정됨
    // (별도 저장 불필요)
    
    // 사용자 정보 저장
    localStorage.setItem('user', JSON.stringify(result.data.user));
    
    // 리다이렉트
    navigate('/dashboard');
  }
};
2. API 호출 시 토큰 사용법
2.1 표준 헤더 구성
모든 보호된 API를 호출할 때는 HTTP 헤더의 Authorization 필드에 Bearer 토큰 형식으로 포함해야 합니다.

http
코드 복사
GET /api/v1/records/daily HTTP/1.1
Host: api.withbuddy.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
2.2 cURL 예제
bash
코드 복사
# 일일 기록 조회
curl -X GET "https://api.withbuddy.com/api/v1/records/daily?date=2025-01-20" \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
     -H "Content-Type: application/json"

# 질문 생성
curl -X POST "https://api.withbuddy.com/api/v1/conversations" \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
     -H "Content-Type: application/json" \
     -d '{
       "question": "연차는 어떻게 신청하나요?",
       "category": "복지"
     }'
2.3 JavaScript (Fetch API)
javascript
코드 실행
코드 복사
// GET 요청
fetch('https://api.withbuddy.com/api/v1/records/daily', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`,
    'Content-Type': 'application/json'
  }
})
.then(response => response.json())
.then(data => console.log(data));

// POST 요청
fetch('https://api.withbuddy.com/api/v1/conversations', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    question: '연차는 어떻게 신청하나요?',
    category: '복지'
  })
})
.then(response => response.json())
.then(data => console.log(data));
2.4 Axios Interceptor (권장)
typescript
코드 실행
코드 복사
// api/axios.config.ts
import axios from 'axios';

const apiClient = axios.create({
  baseURL: 'https://api.withbuddy.com',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Request Interceptor: 자동으로 토큰 추가
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: 401 에러 시 자동 토큰 갱신
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    
    // 401 에러이고, 재시도하지 않은 요청인 경우
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        // 토큰 갱신
        const { data } = await axios.post('/api/v1/auth/refresh', {}, {
          withCredentials: true // Refresh Token 쿠키 포함
        });
        
        // 새 토큰 저장
        localStorage.setItem('accessToken', data.data.accessToken);
        
        // 원래 요청 재시도
        originalRequest.headers.Authorization = `Bearer ${data.data.accessToken}`;
        return apiClient(originalRequest);
        
      } catch (refreshError) {
        // 토큰 갱신 실패 → 로그인 페이지로
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);

export default apiClient;
3. JWT 페이로드 구조 (Claims)
WithBuddy의 JWT는 멀티 테넌시 필터링을 위해 다음 정보를 내포합니다.

3.1 표준 Claims (RFC 7519)
json
코드 복사
{
  "iss": "withbuddy-auth-service",
  "sub": "uuid-1234-5678-90ab-cdef",
  "aud": "withbuddy-api",
  "exp": 1737456000,
  "iat": 1737369600,
  "nbf": 1737369600
}
Claim	설명	예시
iss	Issuer (발급자)	"withbuddy-auth-service"
sub	Subject (사용자 식별자)	"uuid-1234-5678-90ab-cdef"
aud	Audience (대상)	"withbuddy-api"
exp	Expiration Time (만료 시간)	1737456000 (Unix timestamp)
iat	Issued At (발급 시간)	1737369600
nbf	Not Before (유효 시작 시간)	1737369600
3.2 커스텀 Claims (WithBuddy)
json
코드 복사
{
  "companyCode": 1001,
  "employeeNumber": "20260001",
  "name": "김지원",
  "department": "개발팀",
  "role": "USER",
  "permissions": ["read:documents", "write:records", "read:faqs"]
}
Claim	설명	용도
companyCode	회사 고유 코드 (Tenant ID)	데이터 격리 및 필터링
employeeNumber	사원 번호	사용자 식별
name	사용자 이름	로깅, UI 표시
department	소속 부서	부서별 권한 제어
role	사용자 역할	API 접근 제어 (RBAC)
permissions	세부 권한 목록	기능별 접근 제어
3.3 역할(Role) 정의
yaml
코드 복사
ADMIN:
  설명: 관리자 (전체 권한)
  권한:
    - 모든 사용자 데이터 조회
    - 회사 설정 변경
    - 사용자 관리
    - 통계 및 리포트 조회

MENTOR:
  설명: 멘토/사수 (팀 관리)
  권한:
    - 담당 신입사원 데이터 조회
    - 주간 리포트 확인
    - 피드백 작성
    - 미등록 질문 답변

USER:
  설명: 일반 사용자 (신입사원)
  권한:
    - 본인 데이터 CRUD
    - Q&A 이용
    - 체크리스트 관리
    - 기록 작성
3.4 JWT 디코딩 예제
JavaScript (jwt-decode 라이브러리)
typescript
코드 실행
코드 복사
import jwtDecode from 'jwt-decode';

interface JwtPayload {
  sub: string;
  companyCode: number;
  employeeNumber: string;
  name: string;
  department: string;
  role: 'ADMIN' | 'MENTOR' | 'USER';
  permissions: string[];
  exp: number;
}

const token = localStorage.getItem('accessToken');
const decoded = jwtDecode<JwtPayload>(token);

console.log('사용자 ID:', decoded.sub);
console.log('회사 코드:', decoded.companyCode);
console.log('권한:', decoded.role);
console.log('만료 시간:', new Date(decoded.exp * 1000));

// 토큰 만료 확인
const isExpired = decoded.exp * 1000 < Date.now();
if (isExpired) {
  console.log('토큰이 만료되었습니다.');
}
Java (Spring Boot)
java
코드 실행
코드 복사
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

public Claims parseToken(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(token)
        .getBody();
}

// 사용 예시
Claims claims = parseToken(token);
String userId = claims.getSubject();
Integer companyCode = claims.get("companyCode", Integer.class);
String role = claims.get("role", String.class);
4. 토큰 갱신 (Refresh)
Access Token이 만료되어 401 Unauthorized 에러가 발생하면 Refresh Token을 통해 갱신 프로세스를 수행합니다.

4.1 토큰 갱신 API
Endpoint
http
코드 복사
POST /api/v1/auth/refresh
Request Headers
http
코드 복사
Content-Type: application/json
Cookie: refreshToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
중요: Refresh Token은 HttpOnly Cookie로 자동 전송되므로 별도로 헤더에 포함하지 않습니다.

Request Body
json
코드 복사
{}
(빈 객체 또는 Body 없음)

Response (200 OK)
json
코드 복사
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400
  },
  "timestamp": "2025-01-20T10:30:00Z"
}
Error Response (401 Unauthorized)
json
코드 복사
{
  "success": false,
  "error": {
    "code": "AUTH_002",
    "message": "Refresh Token이 만료되었습니다. 다시 로그인해주세요.",
    "details": null
  },
  "timestamp": "2025-01-20T10:30:00Z"
}
4.2 자동 갱신 로직 (React)
typescript
코드 실행
코드 복사
// hooks/useAuth.ts
import { useEffect } from 'react';
import apiClient from '../api/axios.config';

export const useAuth = () => {
  useEffect(() => {
    // 토큰 만료 5분 전에 자동 갱신
    const checkTokenExpiry = () => {
      const token = localStorage.getItem('accessToken');
      if (!token) return;
      
      const decoded = jwtDecode<JwtPayload>(token);
      const expiryTime = decoded.exp * 1000;
      const now = Date.now();
      const fiveMinutes = 5 * 60 * 1000;
      
      // 만료 5분 전이면 갱신
      if (expiryTime - now < fiveMinutes) {
        refreshToken();
      }
    };
    
    const refreshToken = async () => {
      try {
        const { data } = await apiClient.post('/api/v1/auth/refresh', {}, {
          withCredentials: true // Cookie 포함
        });
        
        localStorage.setItem('accessToken', data.data.accessToken);
        console.log('토큰 자동 갱신 완료');
        
      } catch (error) {
        console.error('토큰 갱신 실패:', error);
        // 로그아웃 처리
        localStorage.clear();
        window.location.href = '/login';
      }
    };
    
    // 1분마다 토큰 만료 확인
    const interval = setInterval(checkTokenExpiry, 60000);
    
    return () => clearInterval(interval);
  }, []);
};
4.3 토큰 갱신 플로우
scss
코드 복사
┌─────────┐         ┌─────────┐
│ Client  │         │ Backend │
└────┬────┘         └────┬────┘
     │                   │
     │ 1. API 요청       │
     │ (만료된 Access Token)
     ├──────────────────>│
     │                   │
     │ 2. 401 Unauthorized
     │<──────────────────┤
     │                   │
     │ 3. POST /refresh  │
     │ (Refresh Token)   │
     ├──────────────────>│
     │                   │
     │ 4. Refresh Token 검증
     │                   │
     │ 5. 새 Access Token
     │<──────────────────┤
     │                   │
     │ 6. 원래 API 재요청
     │ (새 Access Token) │
     ├──────────────────>│
     │                   │
     │ 7. 정상 응답      │
     │<──────────────────┤
5. 에러 처리
5.1 인증 관련 에러 코드
코드	HTTP 상태	설명	해결 방법
AUTH_001	401	유효하지 않은 인증 정보	로그인 정보 확인
AUTH_002	401	Refresh Token 만료	재로그인 필요
AUTH_003	401	토큰 형식 오류	Bearer 형식 확인
AUTH_004	401	토큰 서명 검증 실패	토큰 재발급
AUTH_005	403	권한 부족	필요 권한 확인
AUTH_006	403	회사 코드 불일치	멀티 테넌시 오류
AUTH_007	429	로그인 시도 횟수 초과	15분 후 재시도
5.2 에러 응답 구조
json
코드 복사
{
  "success": false,
  "error": {
    "code": "AUTH_001",
    "message": "유효하지 않은 회사 코드 또는 사번입니다.",
    "details": {
      "companyCode": 1001,
      "employeeNumber": "20260001",
      "attemptCount": 3,
      "maxAttempts": 5
    }
  },
  "timestamp": "2025-01-20T10:30:00Z"
}
5.3 Frontend 에러 처리 예제
typescript
코드 실행
코드 복사
// utils/errorHandler.ts
export const handleAuthError = (error: any) => {
  const errorCode = error.response?.data?.error?.code;
  
  switch (errorCode) {
    case 'AUTH_001':
      alert('로그인 정보가 올바르지 않습니다.');
      break;
      
    case 'AUTH_002':
      alert('세션이 만료되었습니다. 다시 로그인해주세요.');
      localStorage.clear();
      window.location.href = '/login';
      break;
      
    case 'AUTH_003':
    case 'AUTH_004':
      alert('인증 오류가 발생했습니다. 다시 로그인해주세요.');
      localStorage.clear();
      window.location.href = '/login';
      break;
      
    case 'AUTH_005':
      alert('이 기능을 사용할 권한이 없습니다.');
      break;
      
    case 'AUTH_006':
      alert('회사 정보가 일치하지 않습니다.');
      break;
      
    case 'AUTH_007':
      const retryAfter = error.response?.headers['retry-after'] || 900;
      alert(`로그인 시도 횟수를 초과했습니다. ${Math.floor(retryAfter / 60)}분 후에 다시 시도해주세요.`);
      break;
      
    default:
      alert('인증 오류가 발생했습니다.');
  }
};
6. 보안 고려사항
6.1 토큰 저장
✅ 권장 사항
yaml
코드 복사
Access Token:
  저장 위치: localStorage 또는 sessionStorage
  이유: JavaScript에서 접근 필요 (API 호출)
  주의: XSS 공격 방지 필수

Refresh Token:
  저장 위치: HttpOnly Cookie (서버 설정)
  이유: JavaScript 접근 불가 (보안)
  설정:
    - HttpOnly: true
    - Secure: true (HTTPS)
    - SameSite: Strict
    - Path: /api/v1/auth
❌ 피해야 할 사항
diff
코드 복사
- Refresh Token을 localStorage에 저장
- 토큰을 URL 파라미터로 전송
- 토큰을 로그에 출력
- 토큰을 Git에 커밋
6.2 XSS (Cross-Site Scripting) 방어
typescript
코드 실행
코드 복사
// ❌ 나쁜 예: innerHTML 사용
element.innerHTML = userInput; // XSS 취약

// ✅ 좋은 예: textContent 사용
element.textContent = userInput;

// ✅ React는 기본적으로 XSS 방어
<div>{userInput}</div> // 자동 이스케이프
6.3 CSRF (Cross-Site Request Forgery) 방어
yaml
코드 복사
방어 메커니즘:
  1. SameSite Cookie 설정 (Strict)
  2. Refresh Token만 Cookie 사용
  3. Access Token은 Header로만 전송

Cookie 설정:
  Set-Cookie: refreshToken=xxx; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth
6.4 토큰 만료 전략
yaml
코드 복사
Access Token:
  만료 시간: 24시간
  이유: 사용 편의성과 보안 균형
  
Refresh Token:
  만료 시간: 7일
  이유: 주 단위 재로그인 (보안 강화)
  
자동 로그아웃:
  - 7일 동안 미사용 시 Refresh Token 만료
  - 재로그인 필요
6.5 API Rate Limiting
yaml
코드 복사
로그인 API:
  제한: 5회 / 15분 (IP 기준)
  초과 시: 429 Too Many Requests
  
일반 API:
  제한: 100회 / 1분 (사용자 기준)
  초과 시: 429 Too Many Requests

Header 정보:
  X-RateLimit-Limit: 100
  X-RateLimit-Remaining: 87
  X-RateLimit-Reset: 1737369600
7. 구현 예제
7.1 완전한 인증 플로우 (React + TypeScript)
typescript
코드 실행
코드 복사
// auth/AuthContext.tsx
import React, { createContext, useState, useContext, useEffect } from 'react';
import apiClient from '../api/axios.config';
import jwtDecode from 'jwt-decode';

interface User {
  id: string;
  name: string;
  employeeNumber: string;
  companyCode: number;
  role: string;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // 초기 로드 시 토큰 확인
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      try {
        const decoded = jwtDecode<any>(token);
        
        // 토큰 만료 확인
        if (decoded.exp * 1000 > Date.now()) {
          setUser({
            id: decoded.sub,
            name: decoded.name,
            employeeNumber: decoded.employeeNumber,
            companyCode: decoded.companyCode,
            role: decoded.role
          });
        } else {
          // 만료된 토큰 제거
          localStorage.removeItem('accessToken');
        }
      } catch (error) {
        console.error('토큰 파싱 오류:', error);
        localStorage.removeItem('accessToken');
      }
    }
    setIsLoading(false);
  }, []);

  // 로그인
  const login = async (credentials: LoginCredentials) => {
    try {
      const { data } = await apiClient.post('/api/v1/auth/login', credentials);
      
      // 토큰 저장
      localStorage.setItem('accessToken', data.data.accessToken);
      
      // 사용자 정보 설정
      setUser(data.data.user);
      
    } catch (error) {
      throw error;
    }
  };

  // 로그아웃
  const logout = () => {
    localStorage.removeItem('accessToken');
    setUser(null);
    
    // 로그아웃 API 호출 (Refresh Token 무효화)
    apiClient.post('/api/v1/auth/logout', {}, {
      withCredentials: true
    }).catch(console.error);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};
7.2 로그인 페이지
typescript
코드 실행
코드 복사
// pages/Login.tsx
import React, { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useNavigate } from 'react-router-dom';

export const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  
  const [formData, setFormData] = useState({
    companyCode: '',
    employeeNumber: '',
    name: ''
  });
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      await login({
        companyCode: Number(formData.companyCode),
        employeeNumber: formData.employeeNumber,
        name: formData.name
      });
      
      navigate('/dashboard');
      
    } catch (err: any) {
      const errorMessage = err.response?.data?.error?.message || '로그인에 실패했습니다.';
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-container">
      <h1>WithBuddy 로그인</h1>
      
      <form onSubmit={handleSubmit}>
        <div>
          <label>회사 코드</label>
          <input
            type="number"
            value={formData.companyCode}
            onChange={(e) => setFormData({ ...formData, companyCode: e.target.value })}
            placeholder="1001"
            required
          />
        </div>

        <div>
          <label>사번</label>
          <input
            type="text"
            value={formData.employeeNumber}
            onChange={(e) => setFormData({ ...formData, employeeNumber: e.target.value })}
            placeholder="20260001"
            required
          />
        </div>

        <div>
          <label>이름</label>
          <input
            type="text"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder="홍길동"
            required
          />
        </div>

        {error && <div className="error-message">{error}</div>}

        <button type="submit" disabled={isLoading}>
          {isLoading ? '로그인 중...' : '로그인'}
        </button>
      </form>
    </div>
  );
};
7.3 보호된 라우트
typescript
코드 실행
코드 복사
// components/ProtectedRoute.tsx
import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  requiredRole
}) => {
  const { isAuthenticated, user, isLoading } = useAuth();

  if (isLoading) {
    return <div>로딩 중...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // 역할 확인
  if (requiredRole && user?.role !== requiredRole) {
    return <Navigate to="/unauthorized" replace />;
  }

  return <>{children}</>;
};

// App.tsx에서 사용
<Routes>
  <Route path="/login" element={<LoginPage />} />
  
  <Route
    path="/dashboard"
    element={
      <ProtectedRoute>
        <Dashboard />
      </ProtectedRoute>
    }
  />
  
  <Route
    path="/admin"
    element={
      <ProtectedRoute requiredRole="ADMIN">
        <AdminPanel />
      </ProtectedRoute>
    }
  />
</Routes>
7.4 Backend JWT 검증 (Spring Boot)
java
코드 실행
코드 복사
// security/JwtAuthenticationFilter.java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            // 1. Authorization 헤더에서 토큰 추출
            String token = extractToken(request);
            
            if (token != null && jwtProvider.validateToken(token)) {
                // 2. 토큰에서 사용자 정보 추출
                Claims claims = jwtProvider.parseToken(token);
                
                // 3. Authentication 객체 생성
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    getAuthorities(claims)
                );
                
                // 4. SecurityContext에 설정
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                // 5. 요청 속성에 회사 코드 저장 (멀티 테넌시)
                request.setAttribute("companyCode", claims.get("companyCode", Integer.class));
            }
            
        } catch (Exception e) {
            logger.error("JWT 인증 실패", e);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private Collection<? extends GrantedAuthority> getAuthorities(Claims claims) {
        String role = claims.get("role", String.class);
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
8. FAQ
Q1. Access Token과 Refresh Token의 차이는?
diff
코드 복사
Access Token:
- 용도: API 요청 시 사용
- 만료 시간: 짧음 (24시간)
- 저장 위치: localStorage
- 노출 위험: 상대적으로 높음 (JavaScript 접근 가능)

Refresh Token:
- 용도: Access Token 갱신
- 만료 시간: 길음 (7일)
- 저장 위치: HttpOnly Cookie
- 노출 위험: 낮음 (JavaScript 접근 불가)
Q2. 토큰이 만료되면 어떻게 되나요?
markdown
코드 복사
1. API 요청 시 401 Unauthorized 에러 발생
2. Frontend가 자동으로 /refresh 엔드포인트 호출
3. 새 Access Token 발급 및 저장
4. 원래 요청 재시도
5. Refresh Token도 만료 시 → 재로그인 필요
Q3. 여러 탭에서 동시에 사용하면?
diff
코드 복사
localStorage는 같은 도메인에서 공유됨:
- 한 탭에서 로그인 → 다른 탭에서도 인증됨
- 한 탭에서 로그아웃 → 다른 탭에서도 로그아웃

권장 사항:
- storage 이벤트 리스닝으로 동기화
- 한 탭에서 토큰 변경 시 다른 탭도 업데이트
typescript
코드 실행
코드 복사
// 탭 간 동기화
useEffect(() => {
  const handleStorageChange = (e: StorageEvent) => {
    if (e.key === 'accessToken') {
      if (e.newValue === null) {
        // 다른 탭에서 로그아웃
        setUser(null);
        navigate('/login');
      }
    }
  };
  
  window.addEventListener('storage', handleStorageChange);
  return () => window.removeEventListener('storage', handleStorageChange);
}, []);
Q4. 멀티 테넌시는 어떻게 작동하나요?
markdown
코드 복사
1. 로그인 시 회사 코드(companyCode) 포함
2. JWT 토큰에 회사 코드 저장
3. 모든 API 요청 시 토큰에서 회사 코드 추출
4. 쿼리에 자동으로 WHERE company_code = ? 조건 추가
5. 다른 회사 데이터는 절대 조회 불가
java
코드 실행
코드 복사
// Backend 자동 필터링
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
@GetMapping("/records/daily")
public ResponseEntity<List<Record>> getDailyRecords(
    @RequestAttribute Integer companyCode // 필터에서 자동 주입
) {
    // companyCode가 자동으로 필터링됨
    return recordService.findByCompanyCode(companyCode);
}
Q5. Postman에서 어떻게 테스트하나요?
css
코드 복사
1. 로그인 요청:
   POST https://api.withbuddy.com/api/v1/auth/login
   Body: { "companyCode": 1001, "employeeNumber": "20260001", "name": "김지원" }

2. 응답에서 accessToken 복사

3. 환경 변수 설정:
   - Variable: accessToken
   - Value: (복사한 토큰)

4. 다른 API 요청 시:
   Authorization: Bearer {{accessToken}}

5. 자동 갱신 스크립트:
   Tests 탭에서 토큰 자동 저장:
   ```javascript
   pm.environment.set("accessToken", pm.response.json().data.accessToken);
shell
코드 복사

### Q6. 개발 환경에서 HTTPS 없이 테스트 가능한가요?

가능합니다:

개발 환경 (localhost):

HttpOnly: true
Secure: false ← HTTP 허용
SameSite: Lax
프로덕션 환경:

HttpOnly: true
Secure: true ← HTTPS 필수
SameSite: Strict
yaml
코드 복사

---

## 📚 관련 문서

- [API 명세서](./API.md) - 전체 API 엔드포인트 목록
- [에러 코드 가이드](./ERROR_CODES.md) - 상세 에러 코드 설명
- [멀티 테넌시 아키텍처](../architecture/MULTI_TENANCY.md) - 회사별 데이터 격리 방식
- [보안 가이드](../guides/SECURITY.md) - 보안 베스트 프랙티스

---

## 📞 문의

인증 관련 문제가 발생하면:
1. 이 문서의 FAQ 확인
2. [GitHub Issues](https://github.com/WithBuddyAi/withbuddy/issues) 검색
3. 새 Issue 생성 (라벨: `authentication`)
4. Slack `#backend` 채널 문의

---

**최종 업데이트**: 2025-01-20  
**작성자**: WithBuddy Backend Team  
**버전**: 1.0.0