import Tooltip from "../Tooltip";

// 데스크탑/모바일 통합 로그인 폼
function LoginForm({
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
  turnstileContainerRef,
  isDesktop,
}) {
  // 공통 스타일
  const inputClass = (hasError) =>
    `w-[297px] h-[42px] py-[6px] px-[12px] text-[12px] rounded-[6px] md:w-[430px] md:h-[49px] md:p-[12px] md:text-[14px] md:rounded-[8px] lg:w-full lg:h-[56px] lg:px-[12px] lg:rounded-[8px] border-[1px] focus:outline-none ${hasError ? "border-[#F03E3E] focus:border-[#F03E3E]" : "border-[#CED4DA] focus:border-[#339AF0]"}`;

  const errorClass =
    "text-[#F03E3E] text-[10px] w-[297px] md:text-[12px] md:w-[430px] lg:w-auto";

  // 필드 정의(추후 비밀번호 추가 고려)
  const fields = [
    {
      label: "회사코드",
      value: companyCode,
      error: companyCodeError,
      ref: companyCodeRef,
      onChange: handleCompanyCodeChange,
      placeholder: "회사코드를 입력해 주세요. (예: WITHBUDDY)",
      showTooltip: true,
    },
    {
      label: "사원번호",
      value: employeeNumber,
      error: employeeNumberError,
      ref: employeeNumberRef,
      onChange: handleEmployeeNumberChange,
      placeholder: "사원번호를 입력해 주세요.",
    },
    {
      label: "이름",
      value: name,
      error: nameError,
      ref: nameRef,
      onChange: handleNameChange,
      placeholder: "실명을 입력해 주세요.",
    },
  ];

  // ── 개별 필드 렌더링 (공통) ──
  const renderField = (field) => (
    <div key={field.label} className="flex flex-col items-stretch gap-[8px]">
      <div className="flex items-center font-bold text-[14px] w-[297px] md:text-[16px] md:w-[430px] lg:font-semibold lg:text-[16px] lg:text-[#000000] lg:w-auto">
        <span>{field.label}</span>
        <sup className="text-[#F03E3E] text-[12px] font-semibold">*</sup>
        {field.showTooltip && (
          <Tooltip message="회사코드와 사원번호는 담당자에게 확인해 주세요." />
        )}
      </div>
      <input
        className={inputClass(field.error)}
        value={field.value}
        ref={field.ref}
        onChange={field.onChange}
        type="text"
        placeholder={field.placeholder}
      />
      <p className={`${errorClass} ${field.error ? "" : "invisible"}`}>
        {field.error || "\u00A0"}
      </p>
    </div>
  );

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        handleLogin();
      }}
    >
      {isDesktop ? (
        <div className="flex flex-col gap-[20px]">
          <div className="flex flex-col gap-[12px]">
            {fields.map(renderField)}
          </div>
          <div ref={turnstileContainerRef} />
          <p
            className={`text-[#F03E3E] text-[12px] ${errorMessage ? "" : "invisible"}`}
          >
            {errorMessage || "\u00A0"}
          </p>
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
        </div>
      ) : (
        <div className="flex flex-col items-center gap-[12px]">
          {fields.map(renderField)}
          <div ref={turnstileContainerRef} />
          <p
            className={`text-[#F03E3E] text-[10px] w-[297px] md:text-[12px] md:w-[430px] ${errorMessage ? "" : "invisible"}`}
          >
            {errorMessage || "\u00A0"}
          </p>
          <button
            disabled={isLoading}
            type="submit"
            className={`font-semibold rounded-[8px] mt-[-4px] p-[12px] w-[297px] h-[42px] text-[12px] md:w-[430px] md:h-[49px] md:text-[16px] ${isLoading ? "opacity-50" : ""} ${
              isFormValid
                ? "bg-[#204867] border-[0.5px] border-[#DEE2E6] text-[#FFFFFF] hover:bg-[#183348]"
                : "bg-[#F1F3F5] border-[1px] border-[#F1F3F5] text-[#868E96]"
            }`}
          >
            <div className="flex items-center justify-center gap-3">
              <span>시작하기</span>
              {isLoading && (
                <div className="border-[#D1D5DB] border-t-[#1D6EBC] rounded-full animate-spin w-4 h-4 border-2 md:w-6 md:h-6 md:border-4" />
              )}
            </div>
          </button>
        </div>
      )}
    </form>
  );
}

export default LoginForm;
