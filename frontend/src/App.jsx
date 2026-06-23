import { Routes, Route, Navigate } from "react-router-dom";
import { useEffect, useState } from "react";
import axiosInstance from "./api/axiosInstance";
import Login from "./pages/Login";
import MyBuddy from "./pages/MyBuddy";
import Admin from "./pages/Admin";
import Inactive from "./pages/Inactive";
import { setLogoutHandler, setToastHandler } from "./api/handlers";
import ErrorToast from "./components/ErrorToast";

function App() {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [toastMessage, setToastMessage] = useState("");

  // 앱 시작 시 로그인 상태 복원 (쿠키 기반)
  useEffect(() => {
    axiosInstance
      .get("/api/v1/auth/me")
      .then((res) => setUser(res.data))
      .catch(() => setUser(null))
      .finally(() => setIsLoading(false));
  }, []);

  useEffect(() => {
    setLogoutHandler(() => {
      setUser(null);
    });
    setToastHandler((message) => {
      setToastMessage(message);
      setTimeout(() => setToastMessage(""), 3000);
    });
  }, []);

  const isLoggedIn = !!user;
  const role = user?.role || null;
  const accountStatus = user?.accountStatus || null;

  // API 응답 오기 전에는 라우팅하지 않음
  if (isLoading) {
    return null;
  }

  return (
    <div>
      <ErrorToast errorMessage={toastMessage} />
      <Routes>
        {/* 로그인 여부에 따른 페이지 이동
        로그인(ACTIVE) -> My Buddy 페이지
        로그인(INACTIVE) -> 이용 종료 안내 페이지
        로그인(ADMIN) -> Admin 페이지
        미로그인 -> Login 페이지 */}
        <Route
          path="/"
          element={
            isLoggedIn ? (
              role === "ADMIN" && accountStatus === "ACTIVE" ? (
                <Navigate to="/admin" />
              ) : role === "USER" && accountStatus === "INACTIVE" ? (
                <Navigate to="/inactive" />
              ) : (
                <Navigate to="/mybuddy" />
              )
            ) : (
              <Navigate to="/login" />
            )
          }
        />

        <Route
          path="/login"
          element={
            isLoggedIn ? (
              role === "ADMIN" && accountStatus === "ACTIVE" ? (
                <Navigate to="/admin" />
              ) : role === "USER" && accountStatus === "INACTIVE" ? (
                <Navigate to="/inactive" />
              ) : (
                <Navigate to="/mybuddy" />
              )
            ) : (
              <Login user={user} setUser={setUser} />
            )
          }
        />

        <Route
          path="/mybuddy"
          element={
            isLoggedIn ? (
              role === "ADMIN" && accountStatus === "ACTIVE" ? (
                <Navigate to="/admin" />
              ) : (
                <MyBuddy user={user} setUser={setUser} />
              )
            ) : (
              <Navigate to="/login" />
            )
          }
        />

        <Route
          path="/inactive"
          element={
            isLoggedIn ? (
              role === "USER" && accountStatus === "INACTIVE" ? (
                <Inactive user={user} setUser={setUser} />
              ) : (
                <Navigate to="/mybuddy" />
              )
            ) : (
              <Navigate to="/login" />
            )
          }
        />

        <Route
          path="/admin"
          element={
            isLoggedIn ? (
              role === "ADMIN" && accountStatus === "ACTIVE" ? (
                <Admin user={user} setUser={setUser} />
              ) : (
                <Navigate to="/mybuddy" />
              )
            ) : (
              <Navigate to="/login" />
            )
          }
        />
      </Routes>
    </div>
  );
}

export default App;
