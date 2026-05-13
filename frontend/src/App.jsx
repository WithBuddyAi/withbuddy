import { Routes, Route, Navigate } from "react-router-dom";
import { useEffect, useState } from "react";
import Login from "./pages/Login";
import MyBuddy from "./pages/MyBuddy";
import Admin from "./pages/Admin";
import { setLogoutHandler } from "./api/axiosInstance";

function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(
    !!localStorage.getItem("accessToken"),
  );
  const role = localStorage.getItem("role");

  useEffect(() => {
    setLogoutHandler(() => {
      setIsLoggedIn(false);
    });
  }, []);

  return (
    <div>
      <Routes>
        {/* 로그인 여부에 따른 페이지 이동 | 로그인(USER) -> My Buddy 페이지, 로그인(ADMIN) -> Admin 페이지, 미로그인 -> Login 페이지 */}
        <Route
          path="/"
          element={
            isLoggedIn ? (
              role === "ADMIN" ? (
                <Navigate to="/admin" />
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
              role === "ADMIN" ? (
                <Navigate to="/admin" />
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
              role === "ADMIN" ? (
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
          path="/admin"
          element={
            isLoggedIn ? (
              role === "ADMIN" ? (
                <Admin setIsLoggedIn={setIsLoggedIn}/>
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
