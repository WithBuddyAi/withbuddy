import { useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { UserRoundCog, Menu } from "lucide-react";
import axiosInstance from "../api/axiosInstance";
import LogoutModal from "../components/LogoutModal";
import AdminSidebar from "../components/admin/AdminSidebar";
import AdminMainView from "../components/admin/AdminMainView";
import AdminCreateView from "../components/admin/AdminCreateView";

function Admin({ setIsLoggedIn }) {
  const navigate = useNavigate();
  const [isSidebarOpen, setIsSidebarOpen] = useState(window.innerWidth >= 768);
  const [isLogoutModal, setIsLogoutModal] = useState(false);

  // view 상태: 'main' | 'new'
  const [view, setView] = useState("main");

  // 계정 생성 완료 메시지
  const [successMessage, setSuccessMessage] = useState("");

  // 로그아웃
  const handleLogout = async () => {
try {
    await axiosInstance.post("/api/v1/auth/logout");
  } finally {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("dayCount");
    localStorage.removeItem("hireDate");
    localStorage.removeItem("name");
    localStorage.removeItem("role");
    setIsLoggedIn(false);
    navigate("/login");
  }
};

  // 뷰 전환 시 입력값 초기화
  const handleViewChange = (newView) => {
    setView(newView);
  };

  // 계정 생성 완료 토스트
  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => {
        setSuccessMessage("");
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  return (
    <div className="h-screen flex relative overflow-hidden">
      {/* 로그아웃 모달 */}
      <LogoutModal
        isLogoutModal={isLogoutModal}
        setIsLogoutModal={setIsLogoutModal}
        handleLogout={handleLogout}
      />

      <div
        className="flex flex-1 relative overflow-hidden"
        inert={isLogoutModal ? true : undefined}
      >
        {/* 배경 이미지 */}
        <div
          className="absolute inset-0 z-0"
          style={{
            backgroundImage: `url('/chat_bg.png')`,
            backgroundSize: "cover",
            backgroundPosition: "center",
            backgroundAttachment: "fixed",
          }}
        />

        {/* 모바일 사이드바 오버레이 */}
        {isSidebarOpen && (
          <div
            className="fixed inset-0 z-30 bg-[#00000080] md:hidden"
            onClick={() => setIsSidebarOpen(false)}
          />
        )}

        {/* 사이드바 영역 */}
        <AdminSidebar
          isSidebarOpen={isSidebarOpen}
          setIsSidebarOpen={setIsSidebarOpen}
          setIsLogoutModal={setIsLogoutModal}
        />

        {/* 본문 영역 */}
        <div className="relative z-10 flex flex-1 flex-col md:my-[32px] md:ml-[8px] md:mr-[32px] border-[1px] bg-[#FFFFFF] drop-shadow md:rounded-[32px] justify-between md:p-[40px] overflow-hidden">
          {/* 모바일 헤더 */}
          <div className="flex md:hidden items-center py-[16px] px-[24px] bg-[#EAF6FF]">
            <button onClick={() => setIsSidebarOpen(!isSidebarOpen)}>
              <Menu size={16} />
            </button>
            <div className="absolute left-1/2 -translate-x-1/2 flex items-center gap-[8px] text-[#336B97]">
              <UserRoundCog size={14} />
              <p>계정 관리</p>
            </div>
          </div>

          {/* 본문 콘텐츠 */}
          <div className="flex-1 overflow-y-auto px-[24px] pb-[16px] md:px-0 md:pb-0">
            {view === "main" ? (
              // 메인 화면
              <AdminMainView
                handleViewChange={handleViewChange}
                successMessage={successMessage}
              />
            ) : (
              // 계정 생성 화면
              <AdminCreateView
                handleViewChange={handleViewChange}
                onSuccess={(message) => {
                  setSuccessMessage(message);
                  handleViewChange("main");
                }}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default Admin;
