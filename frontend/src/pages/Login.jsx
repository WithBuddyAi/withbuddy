import { useState, useRef } from 'react';
import char from '../assets/Favicon.svg'
import withbuddy from '../assets/WithBuddy.svg'
import { useNavigate } from "react-router-dom";
import { differenceInDays } from 'date-fns';


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
      localStorage.setItem('hireDate', data.user.hireDate)

      const today = new Date()
      const hireDate = new Date(data.user.hireDate)
      const dayCount = differenceInDays(today, hireDate) + 1
      localStorage.setItem('dayCount', dayCount)

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
    if(e.target.value === '') {
      setCompanyCodeError('')
      return
    }
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
    if(e.target.value === '') {
      setNameError('')
      return
    }
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
    if(e.target.value === '') {
      setEmployeeNumberError('')
      return
    }
    const regex = /^[A-Za-z0-9]{1,20}$/;
    if(!regex.test(e.target.value)) {
      setEmployeeNumberError('1 ~ 20자, 영문 대소문자, 숫자로만 입력해주세요.')
    } else {
      setEmployeeNumberError('')
    }
  }

  // class 정리
  const inputClass = 
    `w-[430px]
    h-[50px]
    p-[12px]
    rounded-[8px]
    border-[1px]
    focus:outline-none`

  const buttonClass = 
    `w-[430px] bg-[#F1F3F5]
    border-[1px]
    border-[#F1F3F5]
    hover:bg-[#E9ECEF] 
    hover:border-[0.5px] 
    hover:border-[#DEE2E6] 
    hover:text-[#495057]
    active:bg-[#E9ECEF] 
    active:border-[0.5px]
    active:border-[#DEE2E6]
    active:text-[#495057]
    mt-[36px]
    py-3
    rounded-[8px]
    text-[16px]
    text-[#101113]
    font-semibold`

  return (
    <div className='h-screen bg-[#FFFFFF] flex flex-col overflow-hidden'>
      <div className='flex-1 flex flex-col items-center justify-center gap-[64px]'>
        <div className='flex items-center justify-center'>
          <img className='w-[68px] h-[59px] mr-[24px]' src={char} alt="위드버디 캐릭터"/>
          <img className='w-[291px] h-[69px]' src={withbuddy} alt="위드버디 로고"/>
        </div>

        <div className='flex flex-col items-center gap-5'>
          {/* 회사코드 입력칸 */}
          <div className='flex flex-col gap-[10px] rounded-[8px]'>
            <label className='font-semibold text-[14px]'>회사코드 <span className='text-red-500'>*</span></label>
            <input 
            className={`${inputClass} ${companyCodeError ?
              'border-[#F03E3E] focus:border-[#F03E3E]' : 'border-[#CED4DA] focus:border-[#339AF0]'}`}
            value={companyCode} 
            ref={companyCodeRef}
            onChange={handleCompanyCodeChange}
            type="text" 
            placeholder="회사 코드를 입력하세요."/>
            {companyCodeError && <p className='text-[#F03E3E] text-[14px]'>{companyCodeError}</p>}
          </div>
          
          {/* 이름 입력칸 */}
          <div className='flex flex-col gap-[10px] rounded-[8px]'>
            <label className='font-semibold text-[14px]'>사원명 <span className='text-red-500'>*</span></label>
            <input 
            className={`${inputClass} ${nameError ? 
              'border-[#F03E3E] focus:border-[#F03E3E]' : 'border-[#CED4DA] focus:border-[#339AF0]'}`}
            value={name} 
            ref={nameRef}
            onChange={handleNameChange}  
            type="text" 
            placeholder="이름을 입력하세요"/>
            {nameError && <p className='text-[#F03E3E] text-[14px]'>{nameError}</p>}
          </div>

          {/* 사원번호 입력칸 */}
          <div className='flex flex-col gap-[10px] rounded-[8px]'>
            <label className='font-semibold text-[14px]'>사원번호 <span className='text-red-500'>*</span></label>
            <input 
            className={`${inputClass} ${employeeNumberError ? 
              'border-[#F03E3E] focus:border-[#F03E3E]' : 'border-[#CED4DA] focus:border-[#339AF0]'}`}
            value={employeeNumber} 
            ref={employeeNumberRef}
            onChange={handleEmployeeNumberChange}
            type="text" 
            placeholder="사원번호를 입력하세요."/>
            {employeeNumberError && <p className='text-[#F03E3E] text-[14px]'>{employeeNumberError}</p>}
          </div>

          <button 
            className={buttonClass}
            type="submit" 
            onClick={handleLogin}>로그인</button>
            {errorMessage && <p className='text-[#F03E3E] text-[14px]'>{errorMessage}</p>}
        </div>
      </div>
      
      <footer className='text-[12px] text-[#6A7282] text-center pb-6'>© 2026 WithBuddy. A Builders League Project.</footer>
    </div>
  )
}




export default Login;