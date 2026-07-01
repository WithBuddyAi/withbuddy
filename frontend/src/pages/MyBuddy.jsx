import { MessageSquare, Menu } from "lucide-react";
import bot from "../assets/Bot_icon.svg";
import { useUser } from "../contexts/UserContext";
import Sidebar from "../components/Sidebar";
import LogoutModal from "../components/LogoutModal";
import ErrorToast from "../components/ErrorToast";
import MessageList from "../components/MessageList";
import QuickQuestions from "../components/QuickQuestions";
import ChatInput from "../components/ChatInput";
import SessionModal from "../components/SessionModal";
import { useLocation } from "react-router-dom";
import { useState } from "react";
import useSession from "../hooks/useSession";
import useSidebar from "../hooks/useSidebar";
import useChat from "../hooks/useChat";

// Bot Class 정리
const botClass = `
  lg:rounded-tl-[8px]
  rounded-tl-[4px]
  rounded-tr-[24px]
  rounded-bl-[24px]
  rounded-br-[24px]
  border-[#E9ECEF]
  border-[1px]
  bg-[#FFFFFF]
  text-[#000000]
  text-[12px]
  leading-snug
  md:text-[15px]
  text-left
  max-w-[310px]
  md:max-w-[500px]
  lg:max-w-[800px]
  p-[16px]
  lg:py-[20px]
  lg:px-[24px]
  whitespace-pre-wrap
  ml-[16px]
  drop-shadow
  y-2
  `;

const navItems = [
  { path: "/mybuddy", label: "마이버디", icon: <MessageSquare size={14} /> },
];

function MyBuddy({ user, setUser }) {
  // 사용자 정보
  const { dayOffset, accountStatus: contextAccountStatus } = useUser();
  const accountStatus = contextAccountStatus ?? user?.accountStatus;
  const dayCount = dayOffset ?? 0;
  const name = user?.name || "";

  // Hooks
  const session = useSession({ setUser });
  const [isLogoutModal, setIsLogoutModal] = useState(false);
  const location = useLocation();
  const sidebar = useSidebar({
    isLoading: false,
    setRetryBt: session.setRetryBt,
    setModalType: session.setModalType,
  });
  const {
    messageList,
    quickQuestion,
    isLoading,
    errorMessage,
    loadingMessage,
    chatTopRef,
    chatBottomRef,
    lastUserMessageRef,
    setMessagesFromCalendar,
    handleSubmit,
    handleRetry,
    handleDownload,
  } = useChat({
    accountStatus,
    name,
    setUser,
    setModalType: session.setModalType,
    setActiveDates: sidebar.setActiveDates,
    setRetryBt: session.setRetryBt,
  });

  // 날짜별 메시지 조회
  const onDateChange = async (date) => {
    if (isLoading) return;
    const messages = await sidebar.handleDateChange(date);
    if (messages) {
      setMessagesFromCalendar([...messages]);
    }
  };

  return (
    <div className="h-screen flex relative overflow-hidden">
      {/* 세션 정책 모달 */}
      <SessionModal
        modalType={session.modalType}
        setModalType={session.setModalType}
        handleRetry={session.retryBt || handleRetry}
        setUser={setUser}
      />

      {/* 에러 토스트 */}
      <ErrorToast errorMessage={!session.modalType ? errorMessage : false} />

      {/* 로그아웃 모달 */}
      <LogoutModal
        isLogoutModal={isLogoutModal}
        setIsLogoutModal={setIsLogoutModal}
        handleLogout={session.handleLogout}
      />

      <div
        className="flex flex-1 relative overflow-hidden"
        inert={session.modalType !== null || isLogoutModal ? true : undefined}
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
        ></div>

        {/* 모바일 사이드바 오버레이 */}
        {sidebar.isSidebarOpen && (
          <div
            className="fixed inset-0 z-40 bg-[#00000080] md:hidden"
            onClick={() => sidebar.setIsSidebarOpen(false)}
          />
        )}

        {/* 사이드바 - 컴포넌트 분리 완료 */}
        <Sidebar
          name={name}
          dayCount={dayCount}
          isSidebarOpen={sidebar.isSidebarOpen}
          setIsSidebarOpen={sidebar.setIsSidebarOpen}
          selectedDate={sidebar.selectedDate}
          activeDates={sidebar.activeDates}
          handleDateChange={onDateChange}
          setIsLogoutModal={setIsLogoutModal}
          isReadOnly={accountStatus === "READ_ONLY"}
          user={user}
        />

        {/* 채팅 영역 */}
        <div className="relative z-10 flex flex-1 flex-col md:my-[32px] md:ml-[8px] md:mr-[32px] border-[1px] bg-[#FFFFFF] drop-shadow md:rounded-[32px] justify-between md:p-[40px] overflow-hidden">
          {/* 모바일 헤더 */}
          <>
            <div className="flex md:hidden items-center py-[16px] px-[24px] bg-[#EAF6FF]">
              <button
                onClick={() => sidebar.setIsSidebarOpen(!sidebar.isSidebarOpen)}
              >
                <Menu size={16} />
              </button>
              <div className="absolute left-1/2 -translate-x-1/2 flex items-center gap-[8px] text-[#336B97]">
                {navItems.map(
                  (item) =>
                    location.pathname === item.path && (
                      <div
                        key={item.path}
                        className="flex items-center justify-center gap-[10px]"
                      >
                        {item.icon}
                        <p>{item.label}</p>
                      </div>
                    ),
                )}
              </div>
            </div>
          </>

          <div className="flex-1 overflow-y-auto px-[24px] pb-[16px]">
            {/* 날짜 클릭 시 맨 위로 자동 스크롤 */}
            <div ref={chatTopRef} />

            {/* 답변(messageList에 온보딩 제안도 포함됨) */}
            <MessageList
              messageList={messageList}
              botClass={botClass}
              handleSubmit={handleSubmit}
              handleRetry={handleRetry}
              isLoading={isLoading}
              dayCount={dayCount}
              handleDownload={handleDownload}
              lastUserMessageRef={lastUserMessageRef}
            />

            {/* 로딩 인디케이터 */}
            {isLoading &&
              !messageList.some((msg) => msg.messageType === "streaming") && (
                <div className="flex justify-start items-start mt-[32px]">
                  <img src={bot} alt="WithBuddy 채팅봇 이미지" />
                  <div className={botClass}>
                    <div className="flex gap-1">
                      <div
                        className="w-2 h-2 bg-[#CED4DA] rounded-full animate-bounce"
                        style={{ animationDelay: "0ms" }}
                      />
                      <div
                        className="w-2 h-2 bg-[#868E96] rounded-full animate-bounce"
                        style={{ animationDelay: "150ms" }}
                      />
                      <div
                        className="w-2 h-2 bg-[#868E96] rounded-full animate-bounce"
                        style={{ animationDelay: "300ms" }}
                      />
                    </div>
                    {loadingMessage && (
                      <p className="mt-[18px]">{loadingMessage}</p>
                    )}
                  </div>
                </div>
              )}

            {/* 질문 전송 시 하단으로 자동 스크롤 */}
            <div ref={chatBottomRef} />
          </div>

          {/* 빠른 질문 */}
          {accountStatus !== "READ_ONLY" && (
            <QuickQuestions
              quickQuestion={quickQuestion}
              handleSubmit={handleSubmit}
              isLoading={isLoading}
            />
          )}

          {/* 입력 창 */}
          <ChatInput
            handleSubmit={handleSubmit}
            isLoading={isLoading}
            isReadOnly={accountStatus === "READ_ONLY"}
          />
        </div>
      </div>
    </div>
  );
}

export default MyBuddy;
