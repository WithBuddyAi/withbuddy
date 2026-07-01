import { CircleCheckBig } from "lucide-react";
import { useState, useEffect, useRef } from "react";
import axiosInstance from "../../../api/axiosInstance";
import useDepartments from "../../../hooks/useDepartments";
import UserMobileList from "./UserMobileList";
import UserTable from "./UserTable";
import Pagination from "./Pagination";
import AdminHeader from "./AdminHeader";

function AdminMainView({ handleViewChange, successMessage }) {
  const [users, setUsers] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // 모바일 구분용(화면 너비 감지)
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768);

  // 필터 state
  const orgOptions = useDepartments();
  const [selectedDept, setSelectedDept] = useState("");
  const [showDeptFilter, setShowDeptFilter] = useState(false);
  const [sortBy, setSortBy] = useState("");
  const [sortDirection, setSortDirection] = useState("asc");
  const deptFilterRef = useRef(null); // 외부 클릭 시 닫기용

  useEffect(() => {
    const fetchUsers = async () => {
      setIsLoading(true);
      // (페이지네이션 | 모바일: 카드 5개 / 태블릿 & PC: 행 10줄)
      try {
        const params = {
          page: currentPage,
          size: isMobile ? 5 : 10,
        };

        // 부서 필터
        if (selectedDept) {
          params.department = selectedDept;
        }

        // 정렬(이름, 사번, 입사일)
        if (sortBy) {
          params.sortBy = sortBy;
          params.sortDirection = sortDirection;
        }

        const res = await axiosInstance.get("/api/v1/admin/users", { params });
        setUsers(res.data.content);
        setTotalPages(res.data.totalPages);
      } catch (error) {
        console.error("계정 목록 조회 실패", error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchUsers();
  }, [currentPage, selectedDept, isMobile, sortBy, sortDirection]);

  // 화면 너비 감지
  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768);
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
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
    <div className="flex flex-col h-full pr-[16px]">
      {/* 토스트 메시지 */}
      {successMessage && (
        <div className="fixed bottom-[40px] right-[40px] flex items-center gap-[8px] bg-white border border-[#DEE2E6] rounded-[12px] px-[12px] py-[10px] md:px-[20px] md:py-[16px] shadow-md text-[10px] md:text-[14px] text-[#343A40]">
          <CircleCheckBig /> {successMessage}
        </div>
      )}

      {/* Admin 헤더 */}
      <AdminHeader handleViewChange={handleViewChange} />

      <div className="flex-1 overflow-auto md:pb-[12px]">
        <UserMobileList users={users} isLoading={isLoading} />

        {/* 계정 목록 */}
        <UserTable
          users={users}
          isLoading={isLoading}
          orgOptions={orgOptions}
          selectedDept={selectedDept}
          showDeptFilter={showDeptFilter}
          deptFilterRef={deptFilterRef}
          setShowDeptFilter={setShowDeptFilter}
          setSelectedDept={setSelectedDept}
          setCurrentPage={setCurrentPage}
          sortBy={sortBy}
          sortDirection={sortDirection}
          setSortBy={setSortBy}
          setSortDirection={setSortDirection}
        />
      </div>

      {/* 페이지네이션 */}
      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        setCurrentPage={setCurrentPage}
      />
    </div>
  );
}

export default AdminMainView;
