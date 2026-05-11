import { useState, useRef } from "react";
import char from "../assets/Favicon_web.svg";
import withbuddy from "../assets/WithBuddy_web.svg";
import { useNavigate, useLocation } from "react-router-dom";
import { differenceInCalendarDays } from "date-fns";
import Tooltip from "../components/Tooltip";
import axios from "axios";
import { useUser } from "../contexts/UserContext";
import SessionModal from "../components/SessionModal";

function Login({ setIsLoggedIn }) {
  // 'redis' 에러
  const [modalType, setModalType] = useState(null);
  // 로딩 스피너
  const [isLoading, setIsLoading] = useState(false);

  // 로그인 시 필요한 정보에 대한 State
  const [companyCode, setCompanyCode] = useState("");
  const [companyCodeError, setCompanyCodeError] = useState("");
  const companyCodeRef = useRef(null);
  const [employeeNumber, setEmployeeNumber] = useState("");
  const [employeeNumberError, setEmployeeNumberError] = useState("");
  const employeeNumberRef = useRef(null);
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState("");
  const nameRef = useRef(null);

  const { setHireDate, setDayOffset, setRole } = useUser();

  const location = useLocation();
  const tokenError = location.state?.tokenError;

  const [errorMessage, setErrorMessage] = useState(tokenError || "");

  // 로그인 시 서버에 데이터 전송 및 페이지 이동
  const BASE_URL = import.meta.env.VITE_API_BASE_URL;
  const navigate = useNavigate();

  const handleLogin = async () => {
    // 빈 값일때 에러 문구 표시
    if (!companyCode) {
      setCompanyCodeError("회사코드를 입력해 주세요.");
    }
    if (!employeeNumber) {
      setEmployeeNumberError("사원번호를 입력해 주세요.");
    }
    if (!name) {
      setNameError("이름을 입력해 주세요.");
    }
    // 빈 값이나 에러가 있으면 해당 input으로 커서 이동 (UX 고려)
    if (!companyCode || companyCodeError) {
      companyCodeRef.current.focus();
      return;
    } else if (!employeeNumber || employeeNumberError) {
      employeeNumberRef.current.focus();
      return;
    } else if (!name || nameError) {
      nameRef.current.focus();
      return;
    }
    setIsLoading(true);
    try {
      // 서버에 데이터 전송
      const { data } = await axios.post(
        `${BASE_URL}/api/v1/auth/login`,
        { companyCode, employeeNumber, name },
        { headers: { "Content-Type": "application/json" } },
      );
      localStorage.setItem("accessToken", data.accessToken);
      setIsLoggedIn(true);
      localStorage.setItem("hireDate", data.user.hireDate);
      setHireDate(data.user.hireDate);
      localStorage.setItem("name", data.user.name);
      localStorage.setItem("role", data.user.role);
      setRole(data.user.role);

      const today = new Date();
      const hireDate = new Date(data.user.hireDate);
      const dayOffset = differenceInCalendarDays(today, hireDate);
      localStorage.setItem("dayCount", dayOffset);
      setDayOffset(dayOffset);

      if (data.user.role === "USER") {
        navigate("/mybuddy");
      } else {
        navigate("/admin");
      }
    } catch (error) {
      // 400 에러인 경우
      if (error.response?.status === 400) {
        error.response.data.errors.forEach((err) => {
          if (err.field === "companyCode") {
            setCompanyCodeError(err.message);
          } else if (err.field === "employeeNumber") {
            setEmployeeNumberError(err.message);
          } else if (err.field === "name") {
            setNameError(err.message);
          }
        });
      }
      // 401 에러인 경우
      else if (error.response?.status === 401) {
        error.response.data.errors.forEach((err) => {
          if (err.field === "login") {
            setErrorMessage(err.message);
          } else if (err.field === "auth") {
            setErrorMessage(err.message);
          } else if (err.field === "token") {
            setErrorMessage(err.message);
          }
        });
      }
      // 503 에러인 경우
      else if (error.response?.status === 503) {
        setModalType("redis");
      }
      // 500 에러인 경우
      else if (error.response?.status === 500) {
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

  // 회사코드 정규식
  const handleCompanyCodeChange = (e) => {
    setCompanyCode(e.target.value);
    setCompanyCodeError("");
    if (e.target.value === "") {
      setCompanyCodeError("");
      return;
    }
    const regex = /^[A-Za-z0-9]{4,20}$/;
    if (!regex.test(e.target.value)) {
      setCompanyCodeError("영문, 숫자를 조합하여 4 ~ 20자로 입력해 주세요.");
    } else {
      setCompanyCodeError("");
    }
  };

  // 사원번호 정규식
  const handleEmployeeNumberChange = (e) => {
    setEmployeeNumber(e.target.value);
    setEmployeeNumberError("");
    if (e.target.value === "") {
      setEmployeeNumberError("");
      return;
    }
    const regex = /^[A-Za-z0-9]{4,20}$/;
    if (!regex.test(e.target.value)) {
      setEmployeeNumberError("영문, 숫자를 조합하여 4 ~ 20자로 입력해 주세요.");
    } else {
      setEmployeeNumberError("");
    }
  };

  // 이름 정규식
  const handleNameChange = (e) => {
    setName(e.target.value);
    setNameError("");
    if (e.target.value === "") {
      setNameError("");
      return;
    }
    const regex = /^[A-Za-zㄱ-힣]{2,20}$/;
    if (!regex.test(e.target.value)) {
      setNameError("한글 또는 영문 1 ~ 20자로 입력해 주세요.");
    } else {
      setNameError("");
    }
  };

  // class 정리
  const inputClass = `w-[297px]
    h-[42px]
    py-[6px]
    px-[12px]
    text-[12px]
    mt-[1px]
    mb-[1px]
    rounded-[6px]
    md:w-[430px]
    md:h-[49px]
    md:p-[12px]
    md:text-[14px]
    md:rounded-[8px]
    border-[1px]
    focus:outline-none`;

  const buttonClass = `font-semibold
    rounded-[8px]
    mt-[-4px]
    p-[12px]
    w-[297px]
    h-[42px]
    text-[12px]
    md:w-[430px]
    md:h-[49px]
    md:text-[16px]`;

  const errorClass =
    "text-[#F03E3E] text-[10px] w-[297px] md:text-[12px] md:w-[430px]";

  return (
    <div className="h-screen bg-[#FFFFFF] flex flex-col">
      {/* 로그인 화면에서는 'redis' 타입만 사용 */}
      <SessionModal
        modalType={modalType}
        setModalType={setModalType}
        handleRetry={handleLogin}
      />

      <div
        className="flex-1 flex flex-col items-center justify-center lg:flex-row lg:gap-[64px]"
        inert={modalType !== null ? true : undefined}
      >
        <div
          className="flex flex-row items-center justify-center mb-[38px] 
        md:mb-[45px] 
        lg:flex-col lg:mr-[340px] lg:items-center"
        >
          <img
            className="w-[35px] h-[35px] mr-[17px] 
          md:w-[38px] md:h-[38px] md:mr-[26px] 
          lg:w-[138px] lg:h-[120px] lg:mb-[30px] lg:mr-0"
            src={char}
            alt="위드버디 캐릭터"
          />
          <img
            className="w-[215px] h-[48px] 
          lg:w-[291px] lg:h-[69px]"
            src={withbuddy}
            alt="위드버디 로고"
          />
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleLogin();
          }}
        >
          <div className="flex flex-col items-center gap-5">
            {/* 회사코드 입력칸 */}
            <div className="flex flex-col items-center gap-[10px] rounded-[8px]">
              <div>
                <div className="flex items-center font-bold text-[14px] w-[297px] md:text-[16px] md:w-[430px]">
                  회사코드
                  <span className="text-red-500">*</span>
                  <Tooltip message="회사코드와 사원번호는 담당자에게 확인해 주세요." />
                </div>
              </div>
              <input
                className={`${inputClass} ${
                  companyCodeError
                    ? "border-[#F03E3E] focus:border-[#F03E3E]"
                    : "border-[#CED4DA] focus:border-[#339AF0]"
                }`}
                value={companyCode}
                ref={companyCodeRef}
                onChange={handleCompanyCodeChange}
                type="text"
                placeholder="회사코드를 입력해 주세요. (예: WITHBUDDY)"
              />
              {companyCodeError && (
                <div className="text-[#F03E3E] text-[10px] w-[297px] md:text-[12px] md:w-[430px]">
                  {companyCodeError}
                </div>
              )}
            </div>

            {/* 사원번호 입력칸 */}
            <div className="flex flex-col items-center gap-[10px] rounded-[8px]">
              <div>
                <div className="font-bold text-[14px] w-[297px] md:text-[16px] md:w-[430px]">
                  사원번호
                  <span className="text-red-500">*</span>
                </div>
              </div>
              <input
                className={`${inputClass} ${
                  employeeNumberError
                    ? "border-[#F03E3E] focus:border-[#F03E3E]"
                    : "border-[#CED4DA] focus:border-[#339AF0]"
                }`}
                value={employeeNumber}
                ref={employeeNumberRef}
                onChange={handleEmployeeNumberChange}
                type="text"
                placeholder="부여받은 사원번호를 입력해 주세요."
              />
              {employeeNumberError && (
                <div className="text-[#F03E3E] text-[10px] w-[297px] md:text-[12px] md:w-[430px]">
                  {employeeNumberError}
                </div>
              )}
            </div>

            {/* 이름 입력칸 */}
            <div className="flex flex-col items-center gap-[10px] rounded-[8px]">
              <div className="font-bold text-[14px] w-[297px] md:text-[16px] md:w-[430px]">
                이름
                <span className="text-red-500">*</span>
              </div>
              <input
                className={`${inputClass} ${
                  nameError
                    ? "border-[#F03E3E] focus:border-[#F03E3E]"
                    : "border-[#CED4DA] focus:border-[#339AF0]"
                }`}
                value={name}
                ref={nameRef}
                onChange={handleNameChange}
                type="text"
                placeholder="실명을 입력해 주세요."
              />
              {nameError && <div className={`${errorClass}`}>{nameError}</div>}
            </div>

            {errorMessage && (
              <div className="text-[#F03E3E] text-[10px] w-[297px] mt-[5px] md:text-[12px] md:w-[430px] md:mt-[10px]">
                {errorMessage}
              </div>
            )}

            <button
              disabled={isLoading}
              className={`${buttonClass} ${isLoading ? "opacity-50" : ""} ${
                companyCode &&
                !companyCodeError &&
                employeeNumber &&
                !employeeNumberError &&
                name &&
                !nameError
                  ? "bg-[#204867] border-[0.5px] border-[#DEE2E6] text-[#FFFFFF] hover:bg-[#183348] hover:border-[0.5px] hover:border-[#DEE2E6] disabled:text-[#F8F9FA] disabled:border[0.5px] disabled:border-[#204867] disabled:bg-[#204867]"
                  : "bg-[#F1F3F5] border-[1px] border-[#F1F3F5] text-[#868E96]"
              }`}
              type="submit"
            >
              <div className="flex items-center justify-center gap-3">
                <span>시작하기</span>
                {isLoading && (
                  <div className="border-gray-300 border-t-blue-700 rounded-full animate-spin
                  w-4 h-4 border-2 md:w-6 md:h-6 md:border-4" />
                )}
              </div>
            </button>
          </div>
        </form>
      </div>
      <footer className="text-[10px] text-[#6A7282] text-center pb-6 md:text-[12px]">
        © 2026 WithBuddy. A Builders League Project.
      </footer>
    </div>
  );
}

export default Login;
