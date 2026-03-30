import { useState, useRef } from 'react';
import char from '../assets/캐릭터.png'
import { useNavigate } from "react-router-dom";


function Login () {
  // 로그인 시 필요한 정보에 대한 State
  const [companyCode, setCompanyCode] = useState('')
  const [companyCodeError, setCompanyCodeError] = useState('')
  const companyCodeRef = useRef(null)
  const [name, setName] = useState ('')
  const [nameError, setNameError] = useState('')
  const nameRef = useRef(null)
  const [employeeNumber, setEmployeeNumber] = useState('')
  const [employeeNumberError, setEmployeeNumberError] = useState('')
  const employeeNumberRef = useRef(null)

  const [errorMessage, setErrorMessage] = useState('')

  // 로그인 시 서버에 데이터 전송 및 페이지 이동
  const BASE_URL = 'http://localhost:8080'
  const navigate = useNavigate()
  const handleLogin = async () => {
    // 빈 값이나 에러가 있으면 해당 input으로 커서 이동 (UX 고려)
    if(!companyCode || companyCodeError) {
      companyCodeRef.current.focus()
      return
    } else if (!name || nameError) {
      nameRef.current.focus()
      return
    } else if (!employeeNumber || employeeNumberError) {
      employeeNumberRef.current.focus()
      return
    } 
    // 서버에 데이터 전송
    const response = await fetch(`${BASE_URL}/api/v1/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        companyCode, name, employeeNumber
      })
    })
    // 응답 -> JSON으로 변환
    const data = await response.json()
    // 성공 시
    if(response.ok) {
      localStorage.setItem('accessToken', data.accessToken)
      navigate('/mybuddy')
    } 
    // 실패 시
    else {
      setErrorMessage(data.message)
    }
  }

  // 회사 코드 정규식
  const handleCompanyCodeChange = (e) => {
    setCompanyCode(e.target.value)
    const regex = /^[A-Za-z0-9]{1,20}$/;
    if(!regex.test(e.target.value)) {
      setCompanyCodeError('1 ~ 20자, 영문 대소문자, 숫자로만 입력해주세요.')
    } else {
      setCompanyCodeError('')
    }
  }

  // 이름 정규식
  const handleNameChange = (e) => {
    setName(e.target.value)
    const regex = /^[A-Za-zㄱ-힣]{1,20}$/;
    if(!regex.test(e.target.value)) {
      setNameError('1 ~ 20자 , 한글 및 영문 대소문자로만 입력해주세요.')
    } else {
      setNameError('')
    }
  }
  
  // 사원 번호 정규식
  const handleEmployeeNumberChange = (e) => {
    setEmployeeNumber(e.target.value)
    const regex = /^[A-Za-z0-9]{1,20}$/;
    if(!regex.test(e.target.value)) {
      setEmployeeNumberError('1 ~ 20자, 영문 대소문자, 숫자로만 입력해주세요.')
    } else {
      setEmployeeNumberError('')
    }
  }

  return (
    <div className='min-h-screen bg-[#FFFFFF] flex items-center justify-center'>
      <div>
        <img src={char} alt="위드버디"/>
        <p className='text-center text-[34px] font-bold'>With Buddy</p>
        <p className='text-center text-[16px] font-semibold text-[#4A5565]'>AI Onboarding Assistant</p>
      </div>

      <div>
        {/* 회사코드 입력칸 */}
        <label>회사코드 *</label>
        <input 
        value={companyCode} 
        ref={companyCodeRef}
        onChange={handleCompanyCodeChange}
        type="text" 
        placeholder="회사 코드를 입력하세요."/>
        {companyCodeError && <p style={{color : 'red'}}>{companyCodeError}</p>}
        
        {/* 이름 입력칸 */}
        <label>사원명 *</label>
        <input 
        value={name} 
        ref={nameRef}
        onChange={handleNameChange}  
        type="text" 
        placeholder="이름을 입력하세요"/>
        {nameError && <p style={{color : 'red'}}>{nameError}</p>}

        {/* 사원번호 입력칸 */}
        <label>사원번호 *</label>
        <input 
        value={employeeNumber} 
        ref={employeeNumberRef}
        onChange={handleEmployeeNumberChange}
        type="text" 
        placeholder="사원번호를 입력하세요."/>
        {employeeNumberError && <p style={{color : 'red'}}>{employeeNumberError}</p>}

        <button type="submit" onClick={handleLogin}>로그인</button>
        {errorMessage && <p style={{color : 'red'}}>{errorMessage}</p>}
      </div>
    </div>
  )
}





export default Login;