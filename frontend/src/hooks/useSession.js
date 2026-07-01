import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { setModalHandler } from "../api/handlers";
import axiosInstance from "../api/axiosInstance";

function useSession({ setUser }) {
  // 에러 타입 'redis' | 'sessionExpired' | 'duplicateLogin' | 기본 = null
  const [modalType, setModalType] = useState(null);

  // 재시도 기능
  const [retryBt, setRetryBt] = useState(null);

  const navigate = useNavigate();

  // setModalHandler 연결
  useEffect(() => {
    setModalHandler((type) => setModalType(type));
  }, []);

  // 로그아웃 — 서버가 쿠키 만료 처리
  const handleLogout = async () => {
    try {
      await axiosInstance.post("/api/v1/auth/logout");
    } finally {
      setUser(null);
      navigate("/login");
    }
  };

  return {
    modalType,
    setModalType,
    retryBt,
    setRetryBt,
    handleLogout,
  };
}

export default useSession;
