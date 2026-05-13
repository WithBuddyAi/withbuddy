import { useNavigate } from "react-router-dom";

function SessionModal({ modalType, setModalType, handleRetry, setIsLoggedIn }) {
  const navigate = useNavigate();

  const modalContent = {
    redis: {
      title: "잠시 연결이 원활하지 않아요.",
      body: "일시적인 문제가 생겼어요.\n조금 후에 다시 시도해 주세요.",
      button: "다시 시도하기",
    },
    sessionExpired: {
      title: "보안을 위해 로그인이 만료되었어요.",
      body: "오랫동안 자리를 비우신 것 같아요.\n다시 로그인하시면 바로 이어서 사용할 수 있어요.",
      button: "다시 로그인하기",
    },
    duplicateLogin: {
      title: "다른 기기에서 로그인 되었어요.",
      body: "동일한 계정으로 다른 기기에서 로그인이 확인되어\n현재 기기의 연결이 종료되었어요.",
      button: "다시 로그인하기",
    },
  };
  const content = modalContent[modalType];

  const handleButtonClick = () => {
    if (modalType === "redis") {
      handleRetry();
      setModalType(null);
    } else {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("dayCount");
      localStorage.removeItem("hireDate");
      localStorage.removeItem("name");
      localStorage.removeItem("role");
      if (setIsLoggedIn) {
        setIsLoggedIn(false);
      }
      setModalType(null);
      navigate("/login");
    }
  };

  return (
    <div>
      {modalType !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#00000080]">
          <div className="bg-[#FFFFFF] p-[24px] rounded-[10px] drop-shadow w-auto">
            <div className="flex flex-col gap-[10px]">
              <p className="text-[18px] font-semibold">{content.title}</p>
              <p className="text-[14px] text-[#868E96] whitespace-pre-line">
                {content.body}
              </p>
            </div>
            <div className="flex justify-end mt-[20px]">
              <button
                onClick={handleButtonClick}
                className="h-[32px] bg-[#204867] hover:bg-[#183348] px-[12px] rounded-[8px] text-[#FFFFFF] text-[14px]"
              >
                {content.button}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default SessionModal;
