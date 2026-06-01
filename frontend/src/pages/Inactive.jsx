import { useNavigate } from "react-router-dom";
import axiosInstance from "../api/axiosInstance";

function Inactive({ setIsLoggedIn }) {
  const navigate = useNavigate();

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

  return (
    <div className="h-screen flex flex-col items-center justify-center gap-[16px]">
      <p className="text-[18px] font-medium">서비스 이용 기간이 종료됐어요.</p>
      <p className="text-[14px] text-[#868E96]">
        궁금한 사항은 담당자에게 직접 문의해주세요.
      </p>
      <button
        onClick={handleLogout}
        className="mt-[24px] bg-[#204867] text-white px-[24px] py-[12px] rounded-[8px] text-[14px]"
      >
        로그인 화면으로 돌아가기
      </button>
    </div>
  );
}

export default Inactive;
