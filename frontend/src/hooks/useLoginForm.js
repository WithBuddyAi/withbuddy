import { useState, useRef } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { differenceInCalendarDays } from "date-fns";
import axiosInstance from "../api/axiosInstance";
import { useUser } from "../contexts/UserContext";
import {
  validateCompanyCode,
  validateEmployeeNumber,
  validateName,
} from "../utils/validators";

function useLoginForm({ setUser, turnstileToken, resetTurnstile }) {
  // 'redis' 에러 모달
  const [modalType, setModalType] = useState(null);

  // 로딩 스피너
  const [isLoading, setIsLoading] = useState(false);

  // 폼 필드 state
  const [companyCode, setCompanyCode] = useState("");
  const [companyCodeError, setCompanyCodeError] = useState("");
  const companyCodeRef = useRef(null);

  const [employeeNumber, setEmployeeNumber] = useState("");
  const [employeeNumberError, setEmployeeNumberError] = useState("");
  const employeeNumberRef = useRef(null);

  const [name, setName] = useState("");
  const [nameError, setNameError] = useState("");
  const nameRef = useRef(null);

  // 유저 정보 저장
  const { setHireDate, setDayOffset, setRole, setAccountStatus } = useUser();
  
  const location = useLocation();

  // 에러 메시지
  const tokenError = location.state?.tokenError;
  const [errorMessage, setErrorMessage] = useState(tokenError || "");

  const navigate = useNavigate();

  // 입력 핸들러 (validators.js 활용)
  const handleCompanyCodeChange = (e) => {
    setCompanyCode(e.target.value);
    setCompanyCodeError(validateCompanyCode(e.target.value));
  };

  const handleEmployeeNumberChange = (e) => {
    setEmployeeNumber(e.target.value);
    setEmployeeNumberError(validateEmployeeNumber(e.target.value));
  };

  const handleNameChange = (e) => {
    setName(e.target.value);
    setNameError(validateName(e.target.value));
  };

  // 폼 유효성
  const isFormValid =
    companyCode &&
    !companyCodeError &&
    employeeNumber &&
    !employeeNumberError &&
    name &&
    !nameError &&
    turnstileToken;

  // 로그인 API 호출
  const handleLogin = async () => {
    // 빈 값일때 에러 문구 표시
    if (!companyCode) setCompanyCodeError("회사코드를 입력해 주세요.");
    if (!employeeNumber) setEmployeeNumberError("사원번호를 입력해 주세요.");
    if (!name) setNameError("이름을 입력해 주세요.");

    // 빈 값이나 에러가 있으면 해당 input으로 커서 이동
    if (!companyCode || companyCodeError) {
      companyCodeRef.current?.focus();
      return;
    } else if (!employeeNumber || employeeNumberError) {
      employeeNumberRef.current?.focus();
      return;
    } else if (!name || nameError) {
      nameRef.current?.focus();
      return;
    }

    // 위젯 미인증 시
    if (!turnstileToken) {
      setErrorMessage("보안 인증을 완료한 뒤 다시 시도해 주세요.");
      return;
    }

    setIsLoading(true);
    try {
      const { data } = await axiosInstance.post("/api/v1/auth/login", {
        companyCode,
        employeeNumber,
        name,
        turnstileToken,
      });

      // 쿠키 기반 인증: 전역 상태만 업데이트
      setUser(data.user);
      setHireDate(data.user.hireDate);
      setRole(data.user.role);
      setAccountStatus(data.user.accountStatus);

      const today = new Date();
      const hireDate = new Date(data.user.hireDate);
      const dayOffset = differenceInCalendarDays(today, hireDate);
      setDayOffset(dayOffset);

      navigate("/");
    } catch (error) {
      resetTurnstile();

      const status = error.response?.status;
      const errorCode = error.response?.data?.code;

      // 각 에러별 처리
      if (status === 400 && errorCode === "CAPTCHA_VERIFICATION_FAILED") {
        setErrorMessage(
          "보안 인증에 실패했어요. 체크박스를 다시 확인해 주세요.",
        );
      } else if (status === 400) {
        error.response.data.errors?.forEach((err) => {
          if (err.field === "companyCode") setCompanyCodeError(err.message);
          else if (err.field === "employeeNumber")
            setEmployeeNumberError(err.message);
          else if (err.field === "name") setNameError(err.message);
        });
      } else if (status === 401) {
        error.response.data.errors?.forEach((err) => {
          if (["login", "auth", "token"].includes(err.field))
            setErrorMessage(err.message);
        });
      } else if (status === 429) {
        const retryAfter = Number(error.response.headers?.["retry-after"]);

        if (!Number.isFinite(retryAfter) || retryAfter <= 0) {
          setErrorMessage(
            "로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요.",
          );
          return;
        }

        const minute = Math.floor(retryAfter / 60);
        const second = retryAfter % 60;
        let retryText;
        if (minute === 0) retryText = `${second}초`;
        else if (second === 0) retryText = `${minute}분`;
        else retryText = `${minute}분 ${second}초`;

        setErrorMessage(
          `로그인 시도가 너무 많아요. ${retryText} 후에 다시 시도해 주세요.`,
        );
      } else if (status === 503 && errorCode === "CAPTCHA_UNAVAILABLE") {
        setErrorMessage(
          "보안 인증 서비스에 일시적인 문제가 발생했어요. 잠시 후 다시 시도해 주세요.",
        );
      } else if (status === 503) {
        setModalType("redis");
      } else if (status === 500) {
        setErrorMessage(
          "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.",
        );
      } else {
        setErrorMessage("인터넷 연결을 확인하고 다시 시도해 주세요.");
      }
    } finally {
      setIsLoading(false);
    }
  };

  return {
    // 모달
    modalType,
    setModalType,
    // 폼 필드
    companyCode,
    companyCodeError,
    companyCodeRef,
    handleCompanyCodeChange,
    employeeNumber,
    employeeNumberError,
    employeeNumberRef,
    handleEmployeeNumberChange,
    name,
    nameError,
    nameRef,
    handleNameChange,
    // 공통
    errorMessage,
    isLoading,
    isFormValid,
    handleLogin,
  };
}

export default useLoginForm;
