import { CircleCheckBig, ChevronDown, ChevronUp } from "lucide-react";
import { useState, useEffect, useRef } from "react";
import axiosInstance from "../../api/axiosInstance";

function AdminMainView({ handleViewChange, successMessage }) {
  const [users, setUsers] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // 필터 state
  const [orgOptions, setOrgOptions] = useState([]);
  const [selectedDept, setSelectedDept] = useState("");
  const [showDeptFilter, setShowDeptFilter] = useState(false);
  const [sortOrder, setSortOrder] = useState(""); // "asc" | "desc" | ""
  const deptFilterRef = useRef(null); // 외부 클릭 시 닫기용

  useEffect(() => {
    const fetchUsers = async () => {
      setIsLoading(true);
      try {
        const res = await axiosInstance.get(
          `/api/v1/admin/users?page=${currentPage}&size=10${selectedDept ? `&department=${selectedDept}` : ""}`,
        );
        setUsers(res.data.content);
        setTotalPages(res.data.totalPages);
      } catch (error) {
        console.error("계정 목록 조회 실패", error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchUsers();
  }, [currentPage, selectedDept]);

  // organization-options API 연동
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

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (deptFilterRef.current && !deptFilterRef.current.contains(e.target)) {
        setShowDeptFilter(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="pr-[16px]">
      {/* 토스트 메시지 */}
      {successMessage && (
        <div className="fixed bottom-[40px] right-[40px] flex items-center gap-[8px] bg-white border border-[#DEE2E6] rounded-[12px] px-[20px] py-[16px] shadow-md text-[14px] text-[#343A40]">
          <CircleCheckBig /> {successMessage}
        </div>
      )}
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between my-[14px]">
        <div className="mb-[10px] md:mb-[16px]">
          <h1 className="text-[17px] md:text-[20px] lg:text-[22px] font-medium text-[#000000]">
            계정 관리
          </h1>
          <p className="text-[12px] md:text-[14px] lg:text-[16px] text-[#495057] mt-[4px] md:mt-[8px] md:mr-[4px]">
            계정을 생성하면 신입 사원이 위드버디에 접속할 수 있어요. 온보딩 참여
            현황은 아래 목록에서 확인하세요.
          </p>
        </div>
        <button
          onClick={() => handleViewChange("new")}
          className="shrink-0 whitespace-nowrap bg-[#F1F3F5] text-[#868E96] text-[12px] w-[115px] p-[8px] md:text-[14px] md:w-[131px] md:p-[12px] rounded-[8px] hover:bg-[#204867] hover:text-[#FFFFFF] active:bg-[#336B97] active:text-[#FFFFFF]"
        >
          + 계정 생성하기
        </button>
      </div>
      {/* 계정 조회 */}
      <div className="overflow-x-auto">
        {/* 테이블 헤더 */}
        <div className="min-w-[900px] grid grid-cols-8 bg-[#336B9714] rounded-[8px] md:px-0 lg:px-[24px] py-[12px] md:text-[12px] lg:text-[16px] text-center font-medium">
          <span className="px-[24px]">이름</span>
          <span className="px-[24px]">사번</span>
          {/* 부서(팀) 헤더 */}
          <span
            ref={deptFilterRef}
            className="px-[24px] flex items-center justify-center relative"
          >
            <button
              onClick={() => setShowDeptFilter((prev) => !prev)}
              className="flex items-center gap-[4px] font-medium"
            >
              {selectedDept || "부서(팀)"}
              {showDeptFilter ? (
                <ChevronUp size={16} />
              ) : (
                <ChevronDown size={16} />
              )}
            </button>

            {showDeptFilter && (
              <ul className="absolute top-full left-1/2 -translate-x-1/2 mt-[12px] bg-white border border-[#CED4DA] mb-[6px] rounded-[8px] shadow-md z-10 min-w-[140px]">
                {/* 정렬 화살표 */}
                <li
                  onClick={() => {
                    setSortOrder("asc");
                    setShowDeptFilter(false);
                  }}
                  className={`px-[16px] py-[10px] text-[14px] cursor-pointer ${sortOrder === "asc" ? "text-[#204867] font-bold" : "text-[#ADB5BD]"}`}
                >
                  ↑ 오름차순
                </li>
                <li
                  onClick={() => {
                    setSortOrder("desc");
                    setShowDeptFilter(false);
                  }}
                  className={`px-[16px] py-[10px] text-[14px] cursor-pointer ${sortOrder === "desc" ? "text-[#204867] font-bold" : "text-[#ADB5BD]"}`}
                >
                  ↓ 내림차순
                </li>
                {/* 전체 */}
                <li
                  onMouseDown={() => {
                    setSelectedDept("");
                    setCurrentPage(0);
                    setShowDeptFilter(false);
                  }}
                  className={`px-[12px] py-[10px] text-[14px] cursor-pointer ${
                    selectedDept === ""
                      ? "bg-[#EAF6FF] text-[#204867]"
                      : "hover:bg-[#F1F3F5]"
                  }`}
                >
                  전체
                </li>
                {/* 부서 목록 */}
                {orgOptions.map((d) => (
                  <li
                    key={d.department}
                    onMouseDown={() => {
                      setSelectedDept(d.department);
                      setCurrentPage(0);
                      setShowDeptFilter(false);
                    }}
                    className={`px-[12px] py-[10px] text-[14px] cursor-pointer ${
                      selectedDept === d.department
                        ? "bg-[#EAF6FF] text-[#204867]"
                        : "hover:bg-[#F1F3F5]"
                    }`}
                  >
                    {d.department}
                  </li>
                ))}
              </ul>
            )}
          </span>
          <span className="px-[24px]">입사일</span>
          <span className="px-[24px]">입사일차</span>
          <span className="px-[24px]">마지막 접속</span>
          <span className="px-[24px]">질문 횟수</span>
          <span className="px-[24px]">상태</span>
        </div>

        {/* 테이블 바디 */}
        <div className="min-w-[900px] min-h-[500px]">
          {isLoading ? (
            <p className="text-center text-[14px] text-[#868E96] mt-[24px]">
              불러오는 중...
            </p>
          ) : users.length === 0 ? (
            <p className="text-center text-[14px] text-[#868E96] mt-[24px]">
              등록된 계정이 없어요.
            </p>
          ) : (
            users.map((user) => (
              <div
                key={user.id}
                className="min-w-[900px] grid grid-cols-8 bg-[#FFFFFF] items-center md:px-0 lg:px-[24px] py-[12px] md:text-[12px] lg:text-[16px] text-[#495057] text-center border-b border-[#F1F3F5]"
              >
                <span className="px-[24px] text-[#000000] font-medium">
                  {user.name}
                </span>
                <span className="px-[24px]">{user.employeeNumber}</span>
                <span className="px-[24px]">
                  {user.department} ({user.teamName})
                </span>
                <span className="px-[24px]">{user.hireDate}</span>
                <span className="px-[24px]">D+{user.hireDay}</span>
                {/* API 명세서 업데이트 되면 그에 맞게 수정 필요
              <span className="px-[24px]">{user.lastLoginAt}</span>
              <span className="px-[24px]">{user.questionCount}</span>
              <span className="px-[24px]">{user.status}</span> */}
              </div>
            ))
          )}
        </div>
      </div>
      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-[8px] mt-[24px]">
          {/* 이전 버튼 */}
          <button
            onClick={() => setCurrentPage((prev) => prev - 1)}
            disabled={currentPage === 0}
            className="w-[32px] h-[32px] flex items-center justify-center rounded-[6px] text-[14px] text-[#204867] disabled:text-[#ADB5BD] disabled:hover:font-normal hover:font-semibold"
          >
            &lt;
          </button>

          {/* 페이지 번호 */}
          {Array.from({ length: totalPages }, (_, i) => i).map((page) => (
            <button
              key={page}
              onClick={() => setCurrentPage(page)}
              className={`w-[30px] h-[30px] flex items-center justify-center rounded-[6px] text-[16px] ${
                currentPage === page
                  ? "bg-[#204867] text-white"
                  : "text-[#204867] hover:font-semibold"
              }`}
            >
              {page + 1}
            </button>
          ))}

          {/* 다음 버튼 */}
          <button
            onClick={() => setCurrentPage((prev) => prev + 1)}
            disabled={currentPage === totalPages - 1}
            className="w-[32px] h-[32px] flex items-center justify-center rounded-[6px] text-[14px] text-[#204867] disabled:text-[#ADB5BD] disabled:hover:font-normal hover:font-semibold"
          >
            &gt;
          </button>
        </div>
      )}
    </div>
  );
}

export default AdminMainView;
