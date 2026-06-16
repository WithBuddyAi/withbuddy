import { useState, useRef, useEffect } from "react";
import char from "../assets/Favicon_web.svg";
import withbuddy from "../assets/WithBuddy_web.svg";
import bot from "../assets/Bot_icon.svg";
import confetti from "../assets/confetti.svg";
import { useNavigate, useLocation } from "react-router-dom";
import { differenceInCalendarDays } from "date-fns";
import Tooltip from "../components/Tooltip";
import axios from "axios";
import { useUser } from "../contexts/UserContext";
import SessionModal from "../components/SessionModal";

// 서브 카피 3가지 (3.5초 간격으로 순환)
const SUB_COPIES = [
  "메일 설정, 비품 신청, 연차 기준처럼 입사 초 자주 묻는 질문을 바로 확인해보세요.",
  "처음이라 물어보기 애매했던 질문도 괜찮아요. 위드버디에게 먼저 편하게 물어보세요.",
  "회사 문서 속 필요한 정보를 위드버디가 질문에 맞게 찾아드려요.",
];

// 최초 마운트 시점의 너비로 결정 (너비 변환 시 타이핑 재시작 없음)
const FULL_TEXT_WIDE =
  "안녕하세요! 저는 위드버디예요😊 입사 초에 자주 헷갈리는 회사 생활 질문을 도와드려요.\n사소하지만 꼭 필요한 것부터 편하게 물어보세요.";
const FULL_TEXT_NARROW =
  "안녕하세요! 저는 위드버디예요😊\n입사 초에 자주 헷갈리는 회사 생활 질문을 도와드려요.\n사소하지만 꼭 필요한 것부터 편하게 물어보세요.";

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

  const { setHireDate, setDayOffset, setRole, setAccountStatus } = useUser();
  const location = useLocation();
  const tokenError = location.state?.tokenError;
  const [errorMessage, setErrorMessage] = useState(tokenError || "");

  // 서브 카피 순환
  const [subCopyIndex, setSubCopyIndex] = useState(0);
  const [isSubCopyVisible, setIsSubCopyVisible] = useState(true);

  useEffect(() => {
    const interval = setInterval(() => {
      setIsSubCopyVisible(false);
      setTimeout(() => {
        setSubCopyIndex((prev) => (prev + 1) % SUB_COPIES.length);
        setIsSubCopyVisible(true);
      }, 1000);
    }, 3500);
    return () => clearInterval(interval);
  }, []);

  // 말풍선 타이핑 효과
  const [displayedText, setDisplayedText] = useState("");
  const fullText = useRef(
    window.innerWidth >= 1600 ? FULL_TEXT_WIDE : FULL_TEXT_NARROW,
  );
  const chars = useRef([...fullText.current]);
  const isTypingDone = useRef(false);

  // 타이핑 효과 (최초 1회)
  useEffect(() => {
    setDisplayedText("");
    let i = 0;
    const timer = setInterval(() => {
      setDisplayedText(chars.current.slice(0, i + 1).join(""));
      i++;
      if (i >= chars.current.length) {
        clearInterval(timer);
        isTypingDone.current = true;
      }
    }, 80);
    return () => clearInterval(timer);
  }, []);

  // 타이핑 끝난 후 너비 변경 시 텍스트만 교체 (타이핑 재시작 없음)
  useEffect(() => {
    const handleResize = () => {
      if (!isTypingDone.current) return;
      const next =
        window.innerWidth >= 1600 ? FULL_TEXT_WIDE : FULL_TEXT_NARROW;
      setDisplayedText(next);
    };
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // 로그인 시 서버에 데이터 전송 및 페이지 이동
  const BASE_URL = import.meta.env.VITE_API_BASE_URL;
  const navigate = useNavigate();

  const handleLogin = async () => {
    // 빈 값일때 에러 문구 표시
    if (!companyCode) setCompanyCodeError("회사코드를 입력해 주세요.");
    if (!employeeNumber) setEmployeeNumberError("사원번호를 입력해 주세요.");
    if (!name) setNameError("이름을 입력해 주세요.");
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
      localStorage.setItem("department", data.user.department);
      localStorage.setItem("teamName", data.user.teamName);
      localStorage.setItem("role", data.user.role);
      localStorage.setItem("accountStatus", data.user.accountStatus);
      setRole(data.user.role);
      setAccountStatus(data.user.accountStatus);
      const today = new Date();
      const hireDate = new Date(data.user.hireDate);
      const dayOffset = differenceInCalendarDays(today, hireDate);
      localStorage.setItem("dayCount", dayOffset);
      setDayOffset(dayOffset);
      if (data.user.role === "USER" && data.user.accountStatus === "ACTIVE")
        navigate("/mybuddy");
      else if (
        data.user.role === "USER" &&
        data.user.accountStatus === "READ_ONLY"
      )
        navigate("/mybuddy");
      else if (
        data.user.role === "USER" &&
        data.user.accountStatus === "INACTIVE"
      )
        navigate("/inactive");
      else if (data.user.role === "SERVICE_ADMIN") navigate("/mybuddy");
      else if (
        data.user.role === "ADMIN" &&
        data.user.accountStatus === "ACTIVE"
      )
        navigate("/admin");
    } catch (error) {
      // 400 에러인 경우
      if (error.response?.status === 400) {
        error.response.data.errors.forEach((err) => {
          if (err.field === "companyCode") setCompanyCodeError(err.message);
          else if (err.field === "employeeNumber")
            setEmployeeNumberError(err.message);
          else if (err.field === "name") setNameError(err.message);
        });
        // 401 에러인 경우
      } else if (error.response?.status === 401) {
        error.response.data.errors.forEach((err) => {
          if (["login", "auth", "token"].includes(err.field))
            setErrorMessage(err.message);
        });
        // 503 에러인 경우
      } else if (error.response?.status === 503) {
        setModalType("redis");
        // 500 에러인 경우
      } else if (error.response?.status === 500) {
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
    if (!e.target.value) return;
    if (!/^[A-Za-z0-9]{4,20}$/.test(e.target.value))
      setCompanyCodeError("영문, 숫자를 조합하여 4 ~ 20자로 입력해 주세요.");
  };

  // 사원번호 정규식
  const handleEmployeeNumberChange = (e) => {
    setEmployeeNumber(e.target.value);
    setEmployeeNumberError("");
    if (!e.target.value) return;
    if (!/^[A-Za-z0-9]{4,20}$/.test(e.target.value))
      setEmployeeNumberError("영문, 숫자를 조합하여 4 ~ 20자로 입력해 주세요.");
  };

  // 이름 정규식
  const handleNameChange = (e) => {
    setName(e.target.value);
    setNameError("");
    if (!e.target.value) return;
    if (!/^[A-Za-zㄱ-힣]{2,20}$/.test(e.target.value))
      setNameError("한글 또는 영문 2 ~ 20자로 입력해 주세요.");
  };

  const isFormValid =
    companyCode &&
    !companyCodeError &&
    employeeNumber &&
    !employeeNumberError &&
    name &&
    !nameError;

  return (
    <div className="min-h-screen bg-[#FFFFFF] flex flex-col">
      {/* 로그인 화면에서는 'redis' 타입만 사용 */}
      <SessionModal
        modalType={modalType}
        setModalType={setModalType}
        handleRetry={handleLogin}
      />

      {/* 데스크탑 전체 배경 */}
      <div className="hidden lg:block fixed inset-0 pointer-events-none z-0">
        {/* 우측 연하늘색 그라디언트 */}
        <div
          className="absolute top-0 right-0 h-full w-full"
          style={{
            background:
              "linear-gradient(to left, #EAF5FF 0%, #EFF8FF 20%, #F3FAFF 50%, #F9FCFF 75%, #FFFFFF 100%)",
          }}
        />
        {/* 점 패턴 전체 배경 */}
        <svg className="absolute inset-0 opacity-20 h-full w-1/2">
          <defs>
            <pattern
              id="dots"
              x="0"
              y="0"
              width="24"
              height="24"
              patternUnits="userSpaceOnUse"
            >
              <circle cx="2" cy="2" r="1.6" fill="#4791CA99" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#dots)" />
        </svg>
      </div>

      <div
        className="flex-1 flex flex-col items-center justify-center lg:flex-row lg:items-center relative z-10"
        inert={modalType !== null ? true : undefined}
      >
        {/* ── 좌측 소개 섹션 (lg 이상) ── */}
        <div
          className="hidden lg:flex lg:flex-col lg:justify-center lg:items-start pr-[12px]"
          style={{
            flex: 1,
            minWidth: 0,
            paddingLeft: "clamp(50px, 10vw, 200px)",
          }}
        >
          <div style={{ width: "110%" }}>
            {/* 메인 카피 (고정) */}
            <h1
              className="font-bold text-[#204867] mb-[24px]"
              style={{ fontSize: "clamp(24px, 3vw, 45px)", lineHeight: "1.4" }}
            >
              처음이라 묻기 어려웠던 질문,
              <br />
              위드버디에게 먼저 물어보세요.
            </h1>

            {/* 서브 카피 (순환) */}
            <div
              className="overflow-hidden mb-[30px]"
              style={{
                height: "clamp(40px, 5vw, 64px)",
                maxWidth: "100%",
              }}
            >
              <p
                className="text-[#204867] transition-all duration-[1000ms]"
                style={{
                  fontSize: "clamp(14px, 1.5vw, 22px)",
                  lineHeight: "1.6",
                  opacity: isSubCopyVisible ? 0.88 : 0,
                  transform: isSubCopyVisible
                    ? "translateY(0)"
                    : "translateY(-12px)",
                }}
              >
                {SUB_COPIES[subCopyIndex]}
              </p>
            </div>

            {/* 챗 목업 */}
            <div className="flex items-start gap-[20px] relative">
              {/* 버디 아이콘 */}
              <div
                className="relative shrink-0 flex items-center justify-center overflow-visible"
                style={{
                  width: "clamp(36px, 4vw, 52px)",
                  height: "clamp(36px, 4vw, 52px)",
                }}
              >
                <img
                  src={bot}
                  alt="위드버디 캐릭터"
                  style={{ width: "clamp(28px, 3vw, 41px)", height: "auto" }}
                />
                <img
                  src={confetti}
                  alt=""
                  className="absolute top-[-10px] right-[-25px]"
                  style={{ width: "clamp(20px, 2.5vw, 31px)", height: "auto" }}
                />
              </div>

              <div className="flex flex-col gap-[10px] pt-[15px]">
                {/* 말풍선 */}
                <div
                  className="border border-[#E9ECEF] shadow-[0px_2.59px_3.886px_rgba(0,0,0,0.06)] bg-[#FFFFFF] w-fit"
                  style={{
                    fontSize: "clamp(14px, 1.1vw, 16px)",
                    borderRadius:
                      "clamp(8px, 1vw, 10px) clamp(20px, 2.5vw, 31px) clamp(20px, 2.5vw, 31px) clamp(20px, 2.5vw, 31px)",
                    padding: "clamp(12px, 2vw, 20px)",
                    display: "inline-block",
                  }}
                >
                  <p
                    className="text-[#000000]"
                    style={{ lineHeight: "1.8", whiteSpace: "pre-line" }}
                  >
                    {displayedText}
                  </p>
                </div>

                {/* 퀵탭 버튼 */}
                <div className="flex gap-[10px] items-center flex-wrap">
                  {[
                    "💻 이메일·계정 세팅",
                    "📦 비품 신청하기",
                    "📅 연차 언제부터?",
                  ].map((tap) => (
                    <div
                      key={tap}
                      className="bg-[#FFFFFF] rounded-full flex items-center shadow-[0px_2.59px_2.59px_rgba(0,0,0,0.12)] text-[#495057] cursor-default whitespace-nowrap"
                      style={{
                        fontSize: "clamp(11px, 1.1vw, 14px)",
                        padding: "0 clamp(12px, 1.5vw, 20px)",
                        height: "clamp(32px, 3.5vw, 41px)",
                      }}
                    >
                      {tap}
                    </div>
                  ))}
                </div>

                <p
                  className="text-[#868E96]"
                  style={{ fontSize: "clamp(11px, 1.1vw, 15px)" }}
                >
                  오전 11:37
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* ── 모바일/태블릿: 기존 레이아웃 그대로 ── */}
        <div className="flex flex-col items-center justify-center lg:hidden">
          <div className="flex flex-row items-center justify-center mb-[38px] md:mb-[45px]">
            <img
              className="w-[35px] h-[35px] mr-[17px] md:w-[38px] md:h-[38px] md:mr-[26px]"
              src={char}
              alt="위드버디 캐릭터"
            />
            <img
              className="w-[215px] h-[48px]"
              src={withbuddy}
              alt="위드버디 로고"
            />
          </div>
          <MobileForm
            companyCode={companyCode}
            companyCodeError={companyCodeError}
            companyCodeRef={companyCodeRef}
            handleCompanyCodeChange={handleCompanyCodeChange}
            employeeNumber={employeeNumber}
            employeeNumberError={employeeNumberError}
            employeeNumberRef={employeeNumberRef}
            handleEmployeeNumberChange={handleEmployeeNumberChange}
            name={name}
            nameError={nameError}
            nameRef={nameRef}
            handleNameChange={handleNameChange}
            errorMessage={errorMessage}
            isLoading={isLoading}
            isFormValid={isFormValid}
            handleLogin={handleLogin}
          />
        </div>

        {/* 데스크탑: 우측 로그인 카드 */}
        <div
          className="hidden lg:flex lg:items-center lg:justify-center lg:shrink-0 lg:w-1/2"
          style={{
            padding: "40px clamp(16px, 2.5vw, 48px)",
          }}
        >
          <div
            className="bg-[#FFFFFFCC] border border-[#33679B4D] rounded-[28px] shadow-[0px_4px_8px_0px_#00000029] flex flex-col gap-[32px]"
            style={{
              width: "clamp(380px, 90%, 526px)",
              padding: "clamp(24px, 3.5vw, 48px)",
            }}
          >
            {/* 로고 */}
            <div className="flex items-center gap-[12px]">
              <img
                className="w-[29px] h-[29px]"
                src={char}
                alt="위드버디 캐릭터"
              />
              <span
                className="text-[#204867] font-extrabold leading-normal"
                style={{ fontSize: "clamp(22px, 2.5vw, 32px)" }}
              >
                WithBuddy
              </span>
            </div>

            {/* 입력 폼 */}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleLogin();
              }}
              className="flex flex-col gap-[24px]"
            >
              <div className="flex flex-col gap-[16px]">
                {/* 회사코드 입력칸 */}
                <div className="flex flex-col gap-[6px]">
                  <div className="flex items-center gap-[2px] h-[32px]">
                    <span className="font-semibold text-[16px] text-[#000000]">
                      회사코드
                    </span>
                    <span className="text-[#F03E3E] text-[12px] font-semibold">
                      *
                    </span>
                    <Tooltip message="회사코드와 사원번호는 담당자에게 확인해 주세요." />
                  </div>
                  <input
                    className={`w-full h-[56px] border rounded-[8px] px-[12px] text-[14px] focus:outline-none ${
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
                    <p className="text-[#F03E3E] text-[12px]">
                      {companyCodeError}
                    </p>
                  )}
                </div>

                {/* 사원번호 입력칸 */}
                <div className="flex flex-col gap-[6px]">
                  <div className="flex items-center gap-[2px] h-[32px]">
                    <span className="font-semibold text-[16px] text-[#000000]">
                      사원번호
                    </span>
                    <span className="text-[#F03E3E] text-[12px] font-semibold">
                      *
                    </span>
                  </div>
                  <input
                    className={`w-full h-[56px] border rounded-[8px] px-[12px] text-[14px] focus:outline-none ${
                      employeeNumberError
                        ? "border-[#F03E3E] focus:border-[#F03E3E]"
                        : "border-[#CED4DA] focus:border-[#339AF0]"
                    }`}
                    value={employeeNumber}
                    ref={employeeNumberRef}
                    onChange={handleEmployeeNumberChange}
                    type="text"
                    placeholder="사원번호를 입력해 주세요."
                  />
                  {employeeNumberError && (
                    <p className="text-[#F03E3E] text-[12px]">
                      {employeeNumberError}
                    </p>
                  )}
                </div>

                {/* 이름 입력칸 */}
                <div className="flex flex-col gap-[6px]">
                  <div className="flex items-center gap-[2px] h-[32px]">
                    <span className="font-semibold text-[16px] text-[#000000]">
                      이름
                    </span>
                    <span className="text-[#F03E3E] text-[12px] font-semibold">
                      *
                    </span>
                  </div>
                  <input
                    className={`w-full h-[56px] border rounded-[8px] px-[12px] text-[14px] focus:outline-none ${
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
                  {nameError && (
                    <p className="text-[#F03E3E] text-[12px]">{nameError}</p>
                  )}
                </div>
              </div>

              {errorMessage && (
                <p className="text-[#F03E3E] text-[12px]">{errorMessage}</p>
              )}

              <button
                disabled={isLoading}
                type="submit"
                className={`w-full h-[56px] rounded-[8px] text-[16px] font-semibold flex items-center justify-center gap-3 transition-colors ${isLoading ? "opacity-50" : ""} ${
                  isFormValid
                    ? "bg-[#204867] text-[#FFFFFF] hover:bg-[#183348]"
                    : "bg-[#F1F3F5] text-[#868E96] cursor-not-allowed"
                }`}
              >
                <span>시작하기</span>
                {isLoading && (
                  <div className="border-[#D1D5DB] border-t-[#1D6EBC] rounded-full animate-spin w-5 h-5 border-2" />
                )}
              </button>
            </form>

            <p className="text-[12px] text-[#868E96] text-center">
              © 2026 WithBuddy. A Builders League Project.
            </p>
          </div>
        </div>
      </div>

      {/* 모바일 footer */}
      <footer className="text-[10px] text-[#6A7282] text-center pb-6 md:text-[12px] lg:hidden">
        © 2026 WithBuddy. A Builders League Project.
      </footer>
    </div>
  );
}

// 모바일/태블릿용 폼 (기존 스타일 유지)
function MobileForm({
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
  errorMessage,
  isLoading,
  isFormValid,
  handleLogin,
}) {
  // class 정리
  const inputClass = `w-[297px] h-[42px] py-[6px] px-[12px] text-[12px] mt-[1px] mb-[1px] rounded-[6px]
    md:w-[430px] md:h-[49px] md:p-[12px] md:text-[14px] md:rounded-[8px]
    border-[1px] focus:outline-none`;
  const buttonClass = `font-semibold rounded-[8px] mt-[-4px] p-[12px]
    w-[297px] h-[42px] text-[12px] md:w-[430px] md:h-[49px] md:text-[16px]`;
  const errorClass =
    "text-[#F03E3E] text-[10px] w-[297px] md:text-[12px] md:w-[430px]";

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        handleLogin();
      }}
    >
      <div className="flex flex-col items-center gap-5">
        {/* 회사코드 입력칸 */}
        <div className="flex flex-col items-center gap-[10px] rounded-[8px]">
          <div className="flex items-center font-bold text-[14px] w-[297px] md:text-[16px] md:w-[430px]">
            회사코드<span className="text-[#EF4444]">*</span>
            <Tooltip message="회사코드와 사원번호는 담당자에게 확인해 주세요." />
          </div>
          <input
            className={`${inputClass} ${companyCodeError ? "border-[#F03E3E] focus:border-[#F03E3E]" : "border-[#CED4DA] focus:border-[#339AF0]"}`}
            value={companyCode}
            ref={companyCodeRef}
            onChange={handleCompanyCodeChange}
            type="text"
            placeholder="회사코드를 입력해 주세요. (예: WITHBUDDY)"
          />
          {companyCodeError && (
            <div className={errorClass}>{companyCodeError}</div>
          )}
        </div>
        {/* 사원번호 입력칸 */}
        <div className="flex flex-col items-center gap-[10px] rounded-[8px]">
          <div className="font-bold text-[14px] w-[297px] md:text-[16px] md:w-[430px]">
            사원번호<span className="text-[#EF4444]">*</span>
          </div>
          <input
            className={`${inputClass} ${employeeNumberError ? "border-[#F03E3E] focus:border-[#F03E3E]" : "border-[#CED4DA] focus:border-[#339AF0]"}`}
            value={employeeNumber}
            ref={employeeNumberRef}
            onChange={handleEmployeeNumberChange}
            type="text"
            placeholder="부여받은 사원번호를 입력해 주세요."
          />
          {employeeNumberError && (
            <div className={errorClass}>{employeeNumberError}</div>
          )}
        </div>
        {/* 이름 입력칸 */}
        <div className="flex flex-col items-center gap-[10px] rounded-[8px]">
          <div className="font-bold text-[14px] w-[297px] md:text-[16px] md:w-[430px]">
            이름<span className="text-[#EF4444]">*</span>
          </div>
          <input
            className={`${inputClass} ${nameError ? "border-[#F03E3E] focus:border-[#F03E3E]" : "border-[#CED4DA] focus:border-[#339AF0]"}`}
            value={name}
            ref={nameRef}
            onChange={handleNameChange}
            type="text"
            placeholder="실명을 입력해 주세요."
          />
          {nameError && <div className={errorClass}>{nameError}</div>}
        </div>
        {errorMessage && (
          <div className="text-[#F03E3E] text-[10px] w-[297px] mt-[5px] md:text-[12px] md:w-[430px] md:mt-[10px]">
            {errorMessage}
          </div>
        )}
        <button
          disabled={isLoading}
          className={`${buttonClass} ${isLoading ? "opacity-50" : ""} ${
            isFormValid
              ? "bg-[#204867] border-[0.5px] border-[#DEE2E6] text-[#FFFFFF] hover:bg-[#183348]"
              : "bg-[#F1F3F5] border-[1px] border-[#F1F3F5] text-[#868E96]"
          }`}
          type="submit"
        >
          <div className="flex items-center justify-center gap-3">
            <span>시작하기</span>
            {isLoading && (
              <div className="border-[#D1D5DB] border-t-[#1D6EBC] rounded-full animate-spin w-4 h-4 border-2 md:w-6 md:h-6 md:border-4" />
            )}
          </div>
        </button>
      </div>
    </form>
  );
}

export default Login;
