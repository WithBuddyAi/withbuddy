import { ChevronRight, Calendar } from "lucide-react";
import DatePicker from "react-datepicker";
import { useState, useRef } from "react";
import { format } from "date-fns";
import axiosInstance from "../../api/axiosInstance";

function AdminCreateView({ handleViewChange, onSuccess }) {
  // 계정 생성 시 필요한 정보에 대한 State
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState("");
  const [employeeNumber, setEmployeeNumber] = useState("");
  const [employeeNumberError, setEmployeeNumberError] = useState("");
  const [hireDate, setHireDate] = useState(null);
  const [hireDateError, setHireDateError] = useState("");
  const [hireDateInput, setHireDateInput] = useState("");

  // 로딩 스피너
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  // DatePicker ref (아이콘 클릭 시 달력 열기)
  const datePickerRef = useRef(null);

  // 이름 정규식
  const handleNameChange = (e) => {
    setName(e.target.value);
    setNameError("");
    if (e.target.value === "") {
      setNameError("");
      return;
    }
    const regex = /^[A-Za-zㄱ-힣]{1,20}$/;
    if (!regex.test(e.target.value)) {
      setNameError("한글 또는 영문 1 ~ 20자로 입력해 주세요.");
    } else {
      setNameError("");
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

  // 입사일 (달력에서 날짜 선택 시)
  const handleHireDateChange = (date) => {
    setHireDate(date);
    setHireDateInput(date ? format(date, "yyyy-MM-dd") : "");
    setHireDateError("");
    if (!date) setHireDateError("입사일은 필수입니다.");
  };

  // 입사일 직접 입력 시 숫자만 허용 + 자동 하이픈 삽입
  const handleRawChange = (e) => {
    let value = e.target.value.replace(/[^\d]/g, "");
    if (value.length >= 5) value = value.slice(0, 4) + "-" + value.slice(4);
    if (value.length >= 8) value = value.slice(0, 7) + "-" + value.slice(7);
    value = value.slice(0, 10);
    setHireDateInput(value);

    if (value.length === 10) {
      const date = new Date(value);
      if (!isNaN(date)) {
        setHireDate(date);
        setHireDateError("");
      }
    } else {
      setHireDate(null);
    }
  };

  // 계정 생성 시 서버에 데이터 전송
  const handleSubmit = async () => {
    // 빈 값일때 에러 문구 표시
    if (!name) setNameError("이름은 필수입니다.");
    if (!employeeNumber) setEmployeeNumberError("사원번호는 필수입니다.");
    if (!hireDate) setHireDateError("입사일은 필수입니다.");
    if (!name || !employeeNumber || !hireDate) return;
    if (nameError || employeeNumberError || hireDateError) return;

    setIsLoading(true);
    setErrorMessage("");

    try {
      await axiosInstance.post("/api/v1/admin/users", {
        name,
        employeeNumber,
        hireDate: format(hireDate, "yyyy-MM-dd"),
      });
      onSuccess(`${name} 계정이 생성되었어요.`);
    } catch (error) {
      // 400 에러인 경우
      if (error.response?.status === 400) {
        error.response.data.errors.forEach((err) => {
          if (err.field === "name") setNameError(err.message);
          else if (err.field === "employeeNumber")
            setEmployeeNumberError(err.message);
          else if (err.field === "hireDate") setHireDateError(err.message);
        });
      }
      // 409 에러인 경우
      else if (error.response?.status === 409) {
        setEmployeeNumberError("이미 등록된 사번이에요.");
      }
      // 500 에러인 경우
      else if (error.response?.status === 500) {
        setErrorMessage(
          "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.",
        );
      } else {
        setErrorMessage("계정 생성에 실패했어요. 다시 시도해 주세요.");
      }
    } finally {
      setIsLoading(false);
    }
  };

  // class 정리
  const inputClass = (hasError) =>
    `w-full h-[49px] px-[12px] text-[14px] rounded-[8px] border-[1px] focus:outline-none ${
      hasError
        ? "border-[#F03E3E] focus:border-[#F03E3E]"
        : "border-[#CED4DA] focus:border-[#339AF0]"
    }`;

  // 계정 생성하기 버튼 활성화 여부
  const isFormValid =
    name &&
    employeeNumber &&
    hireDate &&
    !nameError &&
    !employeeNumberError &&
    !hireDateError;

  return (
    <div>
      {/* breadcrumb */}
      <div className="flex items-center gap-[8px] text-[14px] text-[#868E96] my-[16px]">
        <span
          className="cursor-pointer hover:text-[#204867]"
          onClick={() => handleViewChange("main")}
        >
          계정 관리
        </span>
        <ChevronRight size={14} />
        <span className="text-[#204867] font-medium">계정 생성하기</span>
      </div>

      {/* 제목 + 설명 */}
      <h1 className="text-[18px] md:text-[22px] font-medium text-[#000000] mb-[4px] md:mb-[8px]">
        계정 생성하기
      </h1>
      <p className="text-[14px] md:text-[16px] text-[#495057] mb-[24px] md:mb-[32px]">
        사원이 로그인할 수 있는 계정을 만들어요.
      </p>

      {/* 입력 폼 */}
      <div className="flex flex-col gap-[12px] md:gap-[24px] max-w-[400px] mx-auto">
        {/* 사원명 입력칸 */}
        <div className="flex flex-col gap-[8px]">
          <label className="font-bold text-[14px] text-[#000000]">
            사원명 <span className="text-[#F03E3E]">*</span>
          </label>
          <input
            className={inputClass(nameError)}
            value={name}
            onChange={handleNameChange}
            type="text"
            placeholder="사원명을 입력해 주세요."
          />
          {nameError && (
            <p className="text-[#F03E3E] text-[12px]">{nameError}</p>
          )}
        </div>

        {/* 사원번호 입력칸 */}
        <div className="flex flex-col gap-[8px]">
          <label className="font-bold text-[14px] text-[#000000]">
            사원번호 <span className="text-[#F03E3E]">*</span>
          </label>
          <input
            className={inputClass(employeeNumberError)}
            value={employeeNumber}
            onChange={handleEmployeeNumberChange}
            type="text"
            placeholder="사원번호를 입력해 주세요."
          />
          {employeeNumberError && (
            <p className="text-[#F03E3E] text-[12px]">{employeeNumberError}</p>
          )}
        </div>

        {/* 입사일 입력칸 */}
        <div className="flex flex-col gap-[8px]">
          <label className="font-bold text-[14px] text-[#000000]">
            입사일 <span className="text-[#F03E3E]">*</span>
          </label>
          <div
            className={`flex items-center justify-between px-[12px] rounded-[8px] border-[1px] h-[49px] focus-within:border-[#339AF0] ${
              hireDateError ? "border-[#F03E3E]" : "border-[#CED4DA]"
            }`}
          >
            <input
              value={hireDateInput}
              onChange={handleRawChange}
              placeholder="YYYY-MM-DD"
              className="w-full focus:outline-none text-[14px] bg-transparent"
            />
            {/* 달력 아이콘 → DatePicker 팝업 트리거 */}
            <DatePicker
              ref={datePickerRef}
              selected={hireDate}
              onChange={handleHireDateChange}
              dateFormat="yyyy-MM-dd"
              popperPlacement="bottom-end"
              popperProps={{
                strategy: "fixed",
              }}
              customInput={
                <Calendar
                  size={16}
                  className="text-[#868E96] shrink-0 cursor-pointer"
                />
              }
            />
          </div>
          {hireDateError && (
            <p className="text-[#F03E3E] text-[12px]">{hireDateError}</p>
          )}
        </div>

        {/* 공통 에러 메시지 */}
        {errorMessage && (
          <p className="text-[#F03E3E] text-[12px]">{errorMessage}</p>
        )}

        {/* 버튼 */}
        <div className="flex gap-[8px] mt-[8px]">
          <button
            onClick={() => handleViewChange("main")}
            className="flex-1 h-[49px] text-[14px] font-medium rounded-[8px] border-[1px] border-[#CED4DA] text-[#495057] hover:bg-[#F1F3F5]"
          >
            취소
          </button>
          <button
            onClick={handleSubmit}
            disabled={isLoading}
            className={`flex-1 h-[49px] text-[14px] font-semibold rounded-[8px] transition-colors ${
              isFormValid
                ? "bg-[#204867] border-[0.5px] border-[#DEE2E6] text-[#FFFFFF] hover:bg-[#183348]"
                : "bg-[#F1F3F5] border-[1px] border-[#F1F3F5] text-[#868E96]"
            } ${isLoading ? "opacity-50" : ""}`}
          >
            <div className="flex items-center justify-center gap-3">
              <span>계정 생성하기</span>
              {isLoading && (
                <div className="border-gray-300 border-t-blue-700 rounded-full animate-spin w-4 h-4 border-2 md:w-6 md:h-6 md:border-4" />
              )}
            </div>
          </button>
        </div>
      </div>
    </div>
  );
}

export default AdminCreateView;
