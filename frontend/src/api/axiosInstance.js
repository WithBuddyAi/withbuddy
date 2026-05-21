import axios from "axios";
import { getLogoutHandler, getModalHandler } from "./handlers";

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

axiosInstance.interceptors.request.use((config) => {
  const accessToken = localStorage.getItem("accessToken");

  if (accessToken) {
    config.headers["Authorization"] = `Bearer ${accessToken}`;
  }

  return config;
});

axiosInstance.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    const logoutHandler = getLogoutHandler();
    const modalHandler = getModalHandler();

    if (error.response?.status === 401) {
      const code = error.response?.data?.code;

      // 세션 모달을 띄워야 하는 경우
      if (code === "SESSION_EXPIRED" || code === "SESSION_REVOKED") {
        if (modalHandler)
          modalHandler(
            code === "SESSION_EXPIRED" ? "sessionExpired" : "duplicateLogin",
          );
        return Promise.reject(error);
      }

      // 바로 로그인 페이지로 이동하는 경우
      if (
        code === "TOKEN_MISSING" ||
        code === "INVALID_TOKEN" ||
        code === "USER_NOT_FOUND"
      ) {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("dayCount");
        localStorage.removeItem("hireDate");
        localStorage.removeItem("name");
        localStorage.removeItem("role");
        if (logoutHandler) logoutHandler();
        return Promise.reject(error);
      }
    }

    // 권한 없음 → 로그아웃 처리
    if (error.response?.status === 403) {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("dayCount");
      localStorage.removeItem("hireDate");
      localStorage.removeItem("name");
      localStorage.removeItem("role");
      if (logoutHandler) logoutHandler();
      return Promise.reject(error);
    }

    if (error.response?.status === 529) {
      console.warn("[529] 서버 과부하 상태 - 요청 경로:", error.config?.url);
    }
    
    return Promise.reject(error);
  },
);

export default axiosInstance;
