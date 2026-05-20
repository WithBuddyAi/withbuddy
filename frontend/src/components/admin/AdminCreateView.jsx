import { ChevronRight, Calendar, ChevronDown } from "lucide-react";
import DatePicker from "react-datepicker";
import { useState, useRef, useEffect } from "react";
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
  const [department, setDepartment] = useState("");
  const [departmentError, setDepartmentError] = useState("");
  const [teamName, setTeamName] = useState("");
  const [teamNameError, setTeamNameError] = useState("");
  const [orgOptions, setOrgOptions] = useState([]);
  const [teamOptions, setTeamOptions] = useState([]);
  // 자동 완성
  const [deptSuggestions, setDeptSuggestions] = useState([]);
  const [teamSuggestions, setTeamSuggestions] = useState([]);
  const [showDeptSuggestions, setShowDeptSuggestions] = useState(false);
  const [showTeamSuggestions, setShowTeamSuggestions] = useState(false);
  // 부서/팀 활성 인덱스
  const [activeDeptIndex, setActiveDeptIndex] = useState(-1);
  const [activeTeamIndex, setActiveTeamIndex] = useState(-1);

  // 키보드 허용 키 '아래 화살표 | 위 화살표 | Enter | ESC | Tab'
  const allowedKeys = ["ArrowDown", "ArrowUp", "Enter", "Escape", "Tab"];

  // 로딩 스피너
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  // DatePicker ref (아이콘 클릭 시 달력 열기)
  const datePickerRef = useRef(null);

  useEffect(() => {
    const fetchOrgOptions = async () => {
      try {
        const res = await axiosInstance.get(
          "/api/v1/admin/organization-options",
        );
        setOrgOptions(res.data.departments);
      } catch (error) {
        console.error("부서/팀 목록 조회 실패", error);
      }
    };
    fetchOrgOptions();
  }, []);

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

  // 부서 입력 핸들러
  const handleDepartmentChange = (e) => {
    const value = e.target.value;
    setDepartment(value);
    setDepartmentError("");
    setTeamName("");
    setTeamNameError("");
    setTeamOptions([]);
    setActiveDeptIndex(-1);

    if (value) {
      const filtered = orgOptions
        .filter((d) => d.department.includes(value))
        .map((d) => d.department);
      setDeptSuggestions(filtered);
      setShowDeptSuggestions(filtered.length > 0);
    } else {
      const all = orgOptions.map((d) => d.department);
      setDeptSuggestions(all);
      setShowDeptSuggestions(all.length > 0);
    }
  };

  // 부서 선택 시
  const handleDeptSelect = (dept) => {
    setDepartment(dept);
    setDepartmentError("");
    setShowDeptSuggestions(false);
    setTeamNameError("");
    const found = orgOptions.find((d) => d.department === dept);
    const teams = found ? found.teamNames : [];
    setTeamOptions(teams);

    if (teams.length === 0) {
      setTeamName(dept); // ← 팀 구분이 없는 부서면 부서명 자동 입력
    } else {
      setTeamName(""); // ← 팀 구분이 있는 부서면 초기화
    }
  };

  // 팀 입력 핸들러
  const handleTeamNameChange = (e) => {
    const value = e.target.value;
    setTeamName(value);
    setTeamNameError("");
    setActiveTeamIndex(-1);

    if (value && teamOptions.length > 0) {
      const filtered = teamOptions.filter((t) => t.includes(value));
      setTeamSuggestions(filtered);
      setShowTeamSuggestions(filtered.length > 0);
    } else {
      setShowTeamSuggestions(false);
    }
  };

  // 팀 선택 시
  const handleTeamSelect = (team) => {
    setTeamName(team);
    setTeamNameError("");
    setShowTeamSuggestions(false);
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
    if (!department) setDepartmentError("부서는 필수입니다.");
    if (!teamName) setTeamNameError("팀명은 필수입니다.");
    if (!hireDate) setHireDateError("입사일은 필수입니다.");
    if (!name || !employeeNumber || !department || !teamName || !hireDate)
      return;
    if (
      nameError ||
      employeeNumberError ||
      departmentError ||
      teamNameError ||
      hireDateError
    )
      return;

    setIsLoading(true);
    setErrorMessage("");

    try {
      await axiosInstance.post("/api/v1/admin/users", {
        name,
        employeeNumber,
        department,
        teamName,
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
          else if (err.field === "department") setDepartmentError(err.message);
          else if (err.field === "teamName") setTeamNameError(err.message);
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
    `w-full h-[40px] md:h-[49px] px-[12px] text-[14px] rounded-[8px] border-[1px] focus:outline-none ${
      hasError
        ? "border-[#F03E3E] focus:border-[#F03E3E]"
        : "border-[#CED4DA] focus:border-[#339AF0]"
    }`;

  // 계정 생성하기 버튼 활성화 여부
  const isFormValid =
    name &&
    employeeNumber &&
    department &&
    teamName &&
    hireDate &&
    !nameError &&
    !employeeNumberError &&
    !departmentError &&
    !teamNameError &&
    !hireDateError;

  return (
    <div>
      {/* breadcrumb */}
      <div className="flex items-center gap-[8px] text-[14px] text-[#868E96] my-[10px] md:my-[16px]">
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
      <p className="text-[14px] md:text-[16px] text-[#495057] mb-[16px] md:mb-[32px] pb-[8px] border-b-[1px]">
        사원이 로그인할 수 있는 계정을 만들어요.
      </p>

      {/* 입력 폼 */}
      <div className="flex flex-col gap-[20px] md:gap-[24px] w-full max-w-[952px]">
        {/* 사원명 + 사원번호 */}
        {/* 사원명 */}
        <div className="flex flex-col md:flex-row gap-[20px] md:gap-[24px]">
          <div className="flex flex-col gap-[8px] flex-1">
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
          {/* 사원번호 */}
          <div className="flex flex-col gap-[8px] flex-1">
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
              <p className="text-[#F03E3E] text-[12px]">
                {employeeNumberError}
              </p>
            )}
          </div>
        </div>

        {/* 부서 + 팀 */}
        <div className="flex flex-col md:flex-row gap-[20px] md:gap-[24px]">
          {/* 부서 */}
          <div className="flex flex-col gap-[8px] flex-1 relative">
            <label className="font-bold text-[14px] text-[#000000]">
              부서 <span className="text-[#F03E3E]">*</span>
            </label>
            <div className="relative">
              <input
                className={`${inputClass(departmentError)} caret-transparent cursor-pointer`}
                value={department}
                onChange={handleDepartmentChange}
                onFocus={() => {
                  const filtered = orgOptions.map((d) => d.department);
                  setDeptSuggestions(filtered);
                  setShowDeptSuggestions(filtered.length > 0);
                }}
                onKeyDown={(e) => {
                  if (!allowedKeys.includes(e.key)) {
                    e.preventDefault();
                  }
                  if (e.key === "ArrowDown" && !showDeptSuggestions) {
                    e.preventDefault();
                    const filtered = orgOptions.map((d) => d.department);
                    setDeptSuggestions(filtered);
                    setShowDeptSuggestions(true);
                    setActiveDeptIndex(0);
                    return;
                  }
                  if (!showDeptSuggestions) return;
                  if (e.key === "ArrowDown") {
                    e.preventDefault();
                    setActiveDeptIndex((prev) =>
                      prev < deptSuggestions.length - 1 ? prev + 1 : prev,
                    );
                  } else if (e.key === "ArrowUp") {
                    e.preventDefault();
                    setActiveDeptIndex((prev) => (prev > 0 ? prev - 1 : 0));
                  } else if (e.key === "Enter") {
                    e.preventDefault();
                    if (activeDeptIndex >= 0) {
                      handleDeptSelect(deptSuggestions[activeDeptIndex]);
                      setActiveDeptIndex(-1);
                    }
                  } else if (e.key === "Escape") {
                    setShowDeptSuggestions(false);
                    setActiveDeptIndex(-1);
                  }
                }}
                onBlur={() => {
                  setTimeout(() => {
                    setShowDeptSuggestions(false);
                    setActiveDeptIndex(-1);
                  }, 150);
                }}
                placeholder="부서를 입력해 주세요."
              />
              <ChevronDown
                size={16}
                className="absolute right-[12px] top-1/2 -translate-y-1/2 rounded-[6px] bg-[#F1F3F5] text-[#868E96] pointer-events-none"
              />
            </div>
            {showDeptSuggestions && (
              <ul className="absolute top-full left-0 right-0 bg-white border border-[#CED4DA] rounded-[8px] shadow-md z-10 mt-[4px]">
                {deptSuggestions.map((dept, index) => (
                  <li
                    key={dept}
                    onMouseDown={() => handleDeptSelect(dept)}
                    className={`px-[12px] py-[10px] text-[14px] cursor-pointer ${
                      index === activeDeptIndex
                        ? "bg-[#EAF6FF] text-[#204867]"
                        : "hover:bg-[#F1F3F5]"
                    }`}
                  >
                    {dept}
                  </li>
                ))}
              </ul>
            )}
            {departmentError && (
              <p className="text-[#F03E3E] text-[12px]">{departmentError}</p>
            )}
          </div>
          {/* 팀 */}
          <div className="flex flex-col gap-[8px] flex-1 relative">
            <label className="font-bold text-[14px] text-[#000000]">
              팀 <span className="text-[#F03E3E]">*</span>
            </label>
            <div className="relative">
              <input
                className={`${inputClass(teamNameError)} caret-transparent cursor-pointer`}
                value={teamName}
                onChange={handleTeamNameChange}
                onFocus={() => {
                  if (teamOptions.length > 0) {
                    setTeamSuggestions(teamOptions);
                    setShowTeamSuggestions(true);
                  }
                }}
                onKeyDown={(e) => {
                  if (!allowedKeys.includes(e.key)) {
                    e.preventDefault();
                  }
                  if (e.key === "ArrowDown" && !showTeamSuggestions) {
                    e.preventDefault();
                    if (teamOptions.length > 0) {
                      setTeamSuggestions(teamOptions);
                      setShowTeamSuggestions(true);
                      setActiveTeamIndex(0);
                    }
                    return;
                  }
                  if (!showTeamSuggestions) return;
                  if (e.key === "ArrowDown") {
                    e.preventDefault();
                    setActiveTeamIndex((prev) =>
                      prev < teamSuggestions.length - 1 ? prev + 1 : prev,
                    );
                  } else if (e.key === "ArrowUp") {
                    e.preventDefault();
                    setActiveTeamIndex((prev) => (prev > 0 ? prev - 1 : 0));
                  } else if (e.key === "Enter") {
                    e.preventDefault();
                    if (activeTeamIndex >= 0) {
                      handleTeamSelect(teamSuggestions[activeTeamIndex]);
                      setActiveTeamIndex(-1);
                    }
                  } else if (e.key === "Escape") {
                    setShowTeamSuggestions(false);
                    setActiveTeamIndex(-1);
                  }
                }}
                onBlur={() => {
                  setTimeout(() => {
                    setShowTeamSuggestions(false);
                    setActiveTeamIndex(-1);
                  }, 150);
                }}
                placeholder="팀명을 입력해 주세요."
              />
              <ChevronDown
                size={16}
                className="absolute right-[12px] top-1/2 -translate-y-1/2 rounded-[6px] bg-[#F1F3F5] text-[#868E96] pointer-events-none"
              />
            </div>
            {showTeamSuggestions && (
              <ul className="absolute top-full left-0 right-0 bg-white border border-[#CED4DA] rounded-[8px] shadow-md z-10 mt-[4px]">
                {teamSuggestions.map((team, index) => (
                  <li
                    key={team}
                    onMouseDown={() => handleTeamSelect(team)}
                    className={`px-[12px] py-[10px] text-[14px] cursor-pointer ${
                      index === activeTeamIndex
                        ? "bg-[#EAF6FF] text-[#204867]"
                        : "hover:bg-[#F1F3F5]"
                    }`}
                  >
                    {team}
                  </li>
                ))}
              </ul>
            )}
            {teamNameError && (
              <p className="text-[#F03E3E] text-[12px]">{teamNameError}</p>
            )}
          </div>
        </div>

        {/* 입사일 + 빈공간 */}
        <div className="flex flex-col md:flex-row gap-[20px] md:gap-[24px]">
          <div className="flex flex-col gap-[8px] flex-1">
            <label className="font-bold text-[14px] text-[#000000]">
              입사일 <span className="text-[#F03E3E]">*</span>
            </label>
            <div
              className={`flex items-center justify-between px-[12px] rounded-[8px] border-[1px] h-[40px] md:h-[49px] focus-within:border-[#339AF0] ${
                hireDateError ? "border-[#F03E3E]" : "border-[#CED4DA]"
              }`}
            >
              <input
                value={hireDateInput}
                onChange={handleRawChange}
                placeholder="YYYY-MM-DD"
                className="w-full focus:outline-none text-[14px] bg-transparent"
              />
              <DatePicker
                ref={datePickerRef}
                selected={hireDate}
                onChange={handleHireDateChange}
                dateFormat="yyyy-MM-dd"
                popperPlacement="bottom-end"
                popperProps={{ strategy: "fixed" }}
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
          {/* 빈 공간 */}
          <div className="hidden md:block flex-1" />
        </div>

        {/* 공통 에러 메시지 */}
        {errorMessage && (
          <p className="text-[#F03E3E] text-[12px]">{errorMessage}</p>
        )}

        {/* 버튼 */}
        <div className="flex gap-[8px] w-full md:w-1/2 md:ml-auto">
          {/* 취소 */}
          <button
            onClick={() => handleViewChange("main")}
            className="flex-1 h-[40px] md:h-[49px] text-[14px] font-medium rounded-[8px] border-[1px] border-[#CED4DA] text-[#495057] hover:bg-[#F1F3F5]"
          >
            취소
          </button>
          {/* 계정 생성하기 */}
          <button
            onClick={handleSubmit}
            disabled={isLoading}
            className={`flex-1 h-[40px] md:h-[49px] text-[14px] font-semibold rounded-[8px] transition-colors ${
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
