import { Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login'
import './App.css'


function App() {
  return (
    <div>
      <Routes>
        {/* 로그인 여부에 따른 페이지 이동 / 로그인 -> My Buddy 페이지, 미로그인 -> Login 페이지 */}
        <Route path="/" element={
          localStorage.getItem('accessToken') ? 
          <Navigate to='/mybuddy'/> : <Navigate to='/login'/>
        } />
        <Route path="/login" element={ <Login/> } />
        <Route path="/mybuddy" element={<div>마이버디</div>} />
      </Routes>
    </div>
  );
}

export default App;