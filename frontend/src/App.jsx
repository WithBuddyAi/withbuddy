import { Routes, Route, Navigate } from "react-router-dom";
import { useEffect, useState } from "react";
import Login from "./pages/Login";
import MyBuddy from "./pages/MyBuddy";
import Admin from "./pages/Admin";
import Inactive from "./pages/Inactive";
import { setLogoutHandler, setToastHandler } from "./api/handlers";
import ErrorToast from "./components/ErrorToast";

function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(
    !!localStorage.getItem("accessToken"),
  );
  const [toastMessage, setToastMessage] = useState("");
  const role = isLoggedIn ? localStorage.getItem("role") : null;
  const accountStatus = isLoggedIn
    ? localStorage.getItem("accountStatus")
    : null;

  useEffect(() => {
    setLogoutHandler(() => {
      setIsLoggedIn(false);
    });
    setToastHandler((message) => {
      setToastMessage(message);
      setTimeout(() => setToastMessage(""), 3000);
    });
  }, []);

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
              <Login setIsLoggedIn={setIsLoggedIn} />
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
                <MyBuddy setIsLoggedIn={setIsLoggedIn} />
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
                <Inactive setIsLoggedIn={setIsLoggedIn} />
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
                <Admin setIsLoggedIn={setIsLoggedIn} />
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
