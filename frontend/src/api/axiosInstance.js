import axios from "axios";
import { getLogoutHandler, getModalHandler, getToastHandler } from "./handlers";

let isToastShowing = false;

const showToast = (message) => {
  const toastHandler = getToastHandler();
  if (isToastShowing) return;
  isToastShowing = true;
  if (toastHandler) toastHandler(message);
  setTimeout(() => {
    isToastShowing = false;
  }, 3000);
};

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
  (response) => response,
  (error) => {
    const logoutHandler = getLogoutHandler();
    const modalHandler = getModalHandler();

    // 401 에러
    if (error.response?.status === 401) {
      const code = error.response?.data?.code;
      if (code === "SESSION_EXPIRED" || code === "SESSION_REVOKED") {
        if (modalHandler)
          modalHandler(
            code === "SESSION_EXPIRED" ? "sessionExpired" : "duplicateLogin",
          );
        return Promise.reject(error);
      }
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

    // 403 권한 없음 → 토스트
    if (error.response?.status === 403) {
      showToast("접근 권한이 없어요.");
      return Promise.reject(error);
    }

    // 404 라우트 누락 (code 없는 경우)
    if (error.response?.status === 404) {
      const code = error.response?.data?.code;
      if (!code) {
        console.error("[404] 라우트 누락 - 요청 경로:", error.config?.url);
        showToast("페이지를 찾을 수 없어요.");
      }
      return Promise.reject(error);
    }

    // 503 세션 저장소 장애 → 토스트
    if (error.response?.status === 503) {
      showToast(
        "서버에 일시적인 문제가 발생했어요. 잠시 후 다시 시도해 주세요.",
      );
      return Promise.reject(error);
    }

    // 529 서버 과부하 → 개발자 확인용 로그
    if (error.response?.status === 529) {
      console.warn("[529] 서버 과부하 상태 - 요청 경로:", error.config?.url);
    }

    // 네트워크 에러 → 토스트
    if (!error.response) {
      showToast("네트워크 연결을 확인해 주세요.");
      return Promise.reject(error);
    }

    return Promise.reject(error);
  },
);

export default axiosInstance;
