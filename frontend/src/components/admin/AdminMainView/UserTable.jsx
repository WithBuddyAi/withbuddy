import { ChevronDown, ChevronUp } from "lucide-react";
import UserRow from "./UserRow";
import LoadingState from "./LoadingState";
import EmptyState from "./EmptyState";
import triangleDown from "../../../assets/triangle_down.svg";

function SortHeader({ label, sortKey, sortBy, sortDirection, handleSort }) {
  return (
    <span className="px-[2px] flex items-center justify-center gap-[6px]">
      <span>{label}</span>
      <div className="flex flex-col gap-[2px]">
        <img
          src={triangleDown}
          onClick={() => handleSort(sortKey, "asc")}
          className={`cursor-pointer rotate-180 w-[10px] ${sortBy === sortKey && sortDirection === "asc" ? "opacity-100" : "opacity-30"}`}
        />
        <img
          src={triangleDown}
          onClick={() => handleSort(sortKey, "desc")}
          className={`cursor-pointer w-[10px] ${sortBy === sortKey && sortDirection === "desc" ? "opacity-100" : "opacity-30"}`}
        />
      </div>
    </span>
  );
}

function UserTable({
  users,
  isLoading,
  deptFilterRef,
  selectedDept,
  setShowDeptFilter,
  showDeptFilter,
  setSelectedDept,
  setCurrentPage,
  orgOptions,
  sortBy,
  sortDirection,
  setSortBy,
  setSortDirection,
}) {
  // 외부 클릭 시 정렬 드롭다운 닫기
    const handleSort = (by, direction) => {
    setSortBy(by);
    setSortDirection(direction);
    setCurrentPage(0);
  };

  return (
    <div className="hidden md:flex md:flex-col">
      {/* 테이블 헤더 */}
      <div className="min-w-[900px] grid grid-cols-8 bg-[#336B9714] rounded-[8px] md:px-0 lg:px-[24px] py-[12px] md:text-[12px] lg:text-[16px] text-center font-medium">
        {/* 이름 정렬 */}
        <SortHeader
          label="이름"
          sortKey="name"
          sortBy={sortBy}
          sortDirection={sortDirection}
          handleSort={handleSort}
        />

        {/* 사번 정렬 */}
        <SortHeader
          label="사번"
          sortKey="employeeNumber"
          sortBy={sortBy}
          sortDirection={sortDirection}
          handleSort={handleSort}
        />
        {/* 부서(팀) 헤더 */}
        <span
          ref={deptFilterRef}
          className="px-[2px] flex items-center justify-center relative"
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
        {/* 입사일 정렬 */}
        <SortHeader
          label="입사일"
          sortKey="hireDate"
          sortBy={sortBy}
          sortDirection={sortDirection}
          handleSort={handleSort}
        />
        <span className="px-[24px]">입사일차</span>
        <span className="px-[24px]">마지막 접속</span>
        <span className="px-[24px]">질문 횟수</span>
        <span className="px-[24px]">상태</span>
      </div>

      {/* 바디 */}
      <div className="min-w-[900px]">
        {isLoading ? (
          <LoadingState />
        ) : users.length === 0 ? (
          <EmptyState />
        ) : (
          users.map((user) => <UserRow key={user.id} user={user} />)
        )}
      </div>
    </div>
  );
}

export default UserTable;
