import { useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { UserRoundCog, Menu } from "lucide-react";
import axiosInstance from "../api/axiosInstance";
import SessionModal from "../components/SessionModal";
import { setModalHandler } from "../api/handlers";
import LogoutModal from "../components/LogoutModal";
import AdminSidebar from "../components/admin/AdminSidebar";
import AdminMainView from "../components/admin/AdminMainView/AdminMainView";
import AdminCreateView from "../components/admin/AdminCreateView";
import AdminDocumentView from "../components/admin/AdminDocumentView/AdminDocumentView";
import DocDeleteModal from "../components/admin/AdminDocumentView/DocDeleteModal";
import DocToast from "../components/admin/AdminDocumentView/DocToast";
import DocDuplicateModal from "../components/admin/AdminDocumentView/DocDuplicateModal";

function Admin({ setIsLoggedIn }) {
  // 세션 정책
  const [modalType, setModalType] = useState(null);
  const [retryBt, setRetryBt] = useState(null);

  const navigate = useNavigate();
  const [isSidebarOpen, setIsSidebarOpen] = useState(window.innerWidth >= 768);
  const [isLogoutModal, setIsLogoutModal] = useState(false);

  // view 상태: 'main' | 'new'
  const [view, setView] = useState("main");

  // 계정 생성 완료 메시지
  const [successMessage, setSuccessMessage] = useState("");

  // 문서 삭제
  const [isDeleteModal, setIsDeleteModal] = useState(false);
  const [deleteSelectedIds, setDeleteSelectedIds] = useState([]);
  const [deleteDocuments, setDeleteDocuments] = useState([]);
  const [refetchDocuments, setRefetchDocuments] = useState(null);
  const [clearSelectedIds, setClearSelectedIds] = useState(null);

  // 문서 삭제 메시지 'success' | 'error' | null
  const [docToast, setDocToast] = useState(null);

  // 문서 업로드 중복 안내 모달
  const [isDuplicateModal, setIsDuplicateModal] = useState(false);

  // 로그아웃
  const handleLogout = async () => {
    try {
      await axiosInstance.post("/api/v1/auth/logout");
    } finally {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("dayCount");
      localStorage.removeItem("hireDate");
      localStorage.removeItem("name");
      localStorage.removeItem("department");
      localStorage.removeItem("teamName");
      localStorage.removeItem("role");
      localStorage.removeItem("accountStatus");
      setIsLoggedIn(false);
      navigate("/login");
    }
  };

  // 뷰 전환 시 입력값 초기화
  const handleViewChange = (newView) => {
    setView(newView);
  };

  useEffect(() => {
    setModalHandler((type) => setModalType(type));
  }, []);

  // 계정 생성 완료 토스트
  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => {
        setSuccessMessage("");
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  // 문서 토스트 (문서 삭제 성공 | 문서 업로드 성공)
  useEffect(() => {
    if (docToast === "success" || docToast === "uploadSuccess") {
      const timer = setTimeout(() => setDocToast(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [docToast]);

  return (
    <div className="h-screen flex relative overflow-hidden">
      {/* 세션 정책 */}
      <SessionModal
        modalType={modalType}
        setModalType={setModalType}
        handleRetry={retryBt}
        setIsLoggedIn={setIsLoggedIn}
      />

      {/* 로그아웃 모달 */}
      <LogoutModal
        isLogoutModal={isLogoutModal}
        setIsLogoutModal={setIsLogoutModal}
        handleLogout={handleLogout}
      />

      {/* 문서 삭제 모달 */}
      {isDeleteModal && (
        <DocDeleteModal
          selectedIds={deleteSelectedIds}
          documents={deleteDocuments}
          onClose={() => setIsDeleteModal(false)}
          // DocDeleteModal onSuccess 수정
          onSuccess={() => {
            setIsDeleteModal(false);
            setDeleteSelectedIds([]);
            setDeleteDocuments([]);
            setDocToast("success");
            refetchDocuments?.();
            clearSelectedIds?.();
          }}
          onError={() => {
            setIsDeleteModal(false);
            setDocToast("error");
          }}
        />
      )}

      {/* 문서 업로드 중복 모달 */}
      {isDuplicateModal && (
        <DocDuplicateModal onClose={() => setIsDuplicateModal(false)} />
      )}

      {/* 문서 토스트 (성공 | 실패) */}
      <DocToast
        type={docToast}
        onRetry={() => {
          setDocToast(null);
          setIsDeleteModal(true);
        }}
      />

      <div
        className="flex flex-1 relative overflow-hidden"
        inert={isLogoutModal || modalType !== null ? true : undefined}
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
          currentView={view}
          handleViewChange={handleViewChange}
        />

        {/* 본문 영역 */}
        <div
          className={`relative z-10 flex flex-1 flex-col md:my-[32px] md:ml-[8px] md:mr-[32px] border-[1px] bg-[#FFFFFF] drop-shadow md:rounded-[32px] justify-between md:p-[40px] overflow-hidden ${
            view === "new" || view === "documents" ? "lg:max-w-[1112px]" : ""
          }`}
        >
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
          <div className="flex-1 flex flex-col overflow-hidden px-[24px] pb-[16px] md:px-0 md:pb-0">
            {view === "main" && (
              <AdminMainView
                handleViewChange={handleViewChange}
                successMessage={successMessage}
              />
            )}
            {view === "new" && (
              <AdminCreateView
                handleViewChange={handleViewChange}
                onSuccess={(message) => {
                  setSuccessMessage(message);
                  handleViewChange("main");
                }}
              />
            )}
            {view === "dashboard" && <div>대시보드 준비 중</div>}
            {view === "documents" && (
              <AdminDocumentView
                onDeleteModalOpen={(ids, docs) => {
                  setDeleteSelectedIds(ids);
                  setDeleteDocuments(docs);
                  setIsDeleteModal(true);
                }}
                onDeleteSuccess={(refetch, clearIds) => {
                  setRefetchDocuments(() => refetch);
                  setClearSelectedIds(() => clearIds);
                }}
                onUploadSuccess={() => setDocToast("uploadSuccess")}
                onUploadError={() => setDocToast("uploadError")}
                onUploadDuplicate={() => setIsDuplicateModal(true)}
              />
            )}
            {view === "unanswered" && <div>미답변 질문 준비 중</div>}
          </div>
        </div>
      </div>
    </div>
  );
}

export default Admin;
