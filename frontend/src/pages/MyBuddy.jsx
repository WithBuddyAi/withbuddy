import { NavLink, useNavigate } from "react-router-dom"
import { MessageSquare, ChevronRight, Send, LogOut, Calendar as CalendarIcon } from "lucide-react"
import char from '../assets/Favicon_web.svg'
import bot from '../assets/Bot_icon.svg'
import bar from '../assets/side_bar.svg'
import { useEffect, useState } from "react"
import { format } from 'date-fns';
import { ko } from 'date-fns/locale';
import axios from "axios";
import Calendar from "react-calendar"
import 'react-calendar/dist/Calendar.css'

function MyBuddy ({setIsLoggedIn}) {
  // 사이드바에 표시되는 정보 state
  const name = localStorage.getItem('name')
  const dayCount = localStorage.getItem('dayCount')
  const hireDate = localStorage.getItem('hireDate')
  const today = new Date()
  const progress = Math.min(Math.round((Number(dayCount) / 90) * 100), 100)
  const [selectedDate, setSelectedDate] = useState(null)
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)
  const [activeDates, setActiveDates] = useState([])
  
  const navigate = useNavigate()

  // 채팅 화면
  const [messageList, setMessageList] = useState([])
  const [text, setText] = useState('')
  const [suggestion, setSuggestion] = useState([])
  const [quickQuestion, setQuickQuestion] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const BASE_URL = import.meta.env.VITE_API_BASE_URL
  const accessToken = localStorage.getItem('accessToken')
  const [errorMessage, setErrorMessage] = useState(false)

  // 대화 기록 달력
  const handleDateChange = async (date) => {
    setSelectedDate(date)
    const formattedDate = format(date, 'yyyy-MM-dd')
    try {
      const {data: message } = await axios.get(
        `${BASE_URL}/api/v1/chat/messages?date=${formattedDate}`,
        { headers: { 'Authorization' : `Bearer ${accessToken}` } }
      )
      setMessageList(message.messages)
    } catch (error) {
      if (!handle401(error)) {
        const serverMessage = error.response?.data?.errors?.[0]?.message
        setErrorMessage(serverMessage || '에러가 발생했어요')
      }
    }
  }


  // 로그아웃
  const handleLogout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('dayCount')
    localStorage.removeItem('hireDate')
    localStorage.removeItem('name')
    setIsLoggedIn(false)
    navigate('/login')
  }

  // 401 에러 발생 시(토큰 만료)
  const handle401 = (error) => {
    if (error.response?.status === 401) {
      const code = error.response?.data?.errors?.[0]?.field
      if (code === 'token' || code === 'auth') {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('dayCount')
        localStorage.removeItem('hireDate')
        localStorage.removeItem('name')
        setIsLoggedIn(false)
        navigate('/login')
        return (true)
      }
    }
    return (false)
  }

  // 첫 렌딩 시 화면
  useEffect (() => {
    setIsLoading(true)
    const fetchData = async () => {
      try {
        const [messageResponse, suggestionResponse, quickResponse] = await Promise.allSettled([
          axios.get(`${BASE_URL}/api/v1/chat/messages`, {
          headers: { 'Authorization': `Bearer ${accessToken}` }
          }),
          axios.get(`${BASE_URL}/api/v1/onboarding-suggestions/me`, {
            headers: { 'Authorization': `Bearer ${accessToken}` }
          }),
          axios.get(`${BASE_URL}/api/v1/chat/quick-questions`, {
          headers: { 'Authorization': `Bearer ${accessToken}` }
          })
        ])
        if (messageResponse.status === 'fulfilled') {
          setMessageList(messageResponse.value.data.messages)
          const dates = [...new Set(messageResponse.value.data.messages.map(m => m.createdAt.slice(0, 10)))]
          setActiveDates(dates)
        } 
        if (suggestionResponse.status === 'fulfilled') {
          setSuggestion(suggestionResponse.value.data.suggestions)
        }
        if (quickResponse.status === 'fulfilled') {
          setQuickQuestion(quickResponse.value.data.quickQuestions)
        }
      } catch (error) {
        if (!handle401(error)) {
          const serverMessage = error.response?.data?.errors?.[0]?.message
          setErrorMessage(serverMessage || '에러가 발생했어요. 다시 시도해 주세요.')
        }
      } finally {
        setIsLoading(false)
      }
    } 
  fetchData()
  }, [])

  // 사용자 질문 전송
  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!text.trim()) return
    
    try {
      const {data} = await axios.post(`${BASE_URL}/api/v1/chat/messages`,
        {content: text},
        {headers: { 'Authorization': `Bearer ${accessToken}` }}
      )
      setMessageList(prev => [...prev, data.question, data.answer])
      setText('')
    } catch (error) {
      if (!handle401(error)) {
        if (error.response?.status === 400) {
          error.response.data.errors.forEach(err => {
            if (err.field === 'content') {
              setErrorMessage(err.message)
            }
          })
        } else {
          setErrorMessage('메시지 전송에 실패했어요.')
        }
      }
    }
  }

  // User Class 정리
  const userClass = 
  `rounded-tl-[24px]
  rounded-tr-[8px]
  rounded-bl-[24px]
  rounded-br-[24px]
  text-[#FFFFFF]
  text-[16px]
  text-left
  max-w-[800px]
  py-[16px]
  px-[24px]
  whitespace-pre-wrap
  my-[20px]
  mr-[42px]
  drop-shadow
  `
  // Bot Class 정리
  const botClass = 
  `rounded-tl-[8px]
  rounded-tr-[24px]
  rounded-bl-[24px]
  rounded-br-[24px]
  border-[#E9ECEF]
  border-[1px]
  bg-[#FFFFFF]
  text-[#000000]
  text-[16px]
  text-left
  max-w-[800px]
  py-[20px]
  px-[24px]
  whitespace-pre-wrap
  my-[20px]
  ml-[67px]
  drop-shadow
  y-2
  `

  return (
    <div className="h-screen flex relative">
      {/* 배경 이미지 적용 */}
      <div className="absolute inset-0 z-0"
      style={{
        backgroundImage: `url('/chat_bg.png')`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        opacity: 0.4
      }}>
      </div>

      {/* 사이드바 - 추후 컴포넌트 분리 필요 */}
      {isSidebarOpen ? (<div className="relative z-10 w-[255px] flex flex-col mt-[32px]">
        {/* 사용자 정보 부분 */}
        <div>
          <div className="flex items-center justify-between py-[12px] px-[16px]">
            <div className="flex items-center">
              <img src={char} alt="위드버디 대표 로고" className="w-[26px] mr-[12px]"/>
              <div className="flex items-center"><p className="text-[#343A40] text-[16px] font-semibold mr-[8px]">{name}</p>
              <p className="text-[#20486799] text-[12px]">Day {dayCount}</p>
              </div>
            </div>
            <img src={bar} alt="사이드바 토글 아이콘" className="w-[20px] h-[16px] cursor-pointer" onClick={() => setIsSidebarOpen(!isSidebarOpen)}/>
          </div>
        </div>

        {/* 메뉴 탭 부분 */}
        <div className="py-[24px] px-[16px]">
          <NavLink to='/mybuddy' className={({isActive}) => `flex items-center justify-between py-[10px] px-[12px] rounded-[6px] border-[1px] w-[231px] h-[46px] hover:bg-[#D0EBFFCC] hover:border-[#D0EBFF] text-[#336B97] text-[16px]
          ${isActive ? 'bg-[#EAF6FF] border-[#4791CA]' : 'bg-[#FFFFFF] border-[#D0EBFF]'}`
          }>
            <div className="flex items-center gap-[10px]">
            <MessageSquare size={14} />
              <span>마이버디</span>
            </div>
              <span><ChevronRight /></span>
          </NavLink>
        </div>

        {/* 대화 기록 달력 */}
        <div className="px-[16px] py-[24px]">
          <div className="text-[#336B97] gap-[4px] mb-[8px] px-[8px]">
          <div className="flex items-center gap-[10px] ">
            <CalendarIcon size={14} />대화기록</div>
          <p className="text-[#868E96] text-[12px]">날짜를 선택하면 해당 날짜의 대화 내용을 확인할 수 있습니다.</p>
          </div>
          <div>
          <Calendar
            onChange={handleDateChange}
            value={selectedDate}
            locale="ko-KR"
            formatDay={(locale, date) => date.getDate()}
            calendarType="gregory"
            />
          </div>
        </div>

        {/* 로그아웃 */}
        <button onClick={handleLogout} className="text-[#204867] mt-auto mb-[36px] flex items-center gap-2 py-[10px] px-[8px] ml-[16px]" ><LogOut size={14} />로그아웃</button>
      </div>) 
      : 
      (<div className="relative z-10 w-[76px] flex flex-col mt-[32px]">
        {/* 사용자 정보 부분 */}
        <div>
          <div className="flex items-center justify-center py-[12px] px-[16px]">
            <img src={bar} alt="사이드바 토글 아이콘" className="w-[20px] h-[16px] cursor-pointer" onClick={() => setIsSidebarOpen(!isSidebarOpen)}/>
          </div>
        </div>

        {/* 메뉴 탭 부분 */}
        <div className="py-[24px] px-[16px]">
          <NavLink to='/mybuddy' className={({isActive}) => `flex items-center justify-center py-[10px] px-[12px] rounded-[6px] border-[1px] w-[44px] h-[48px] hover:bg-[#D0EBFFCC] hover:border-[#D0EBFF] text-[#336B97] text-[16px]
          ${isActive ? 'bg-[#EAF6FF] border-[#4791CA]' : 'bg-[#FFFFFF] border-[#D0EBFF]'}`
          }>
            <MessageSquare size={14} />
          </NavLink>
        </div>

        {/* 대화 기록 달력 */}
        <div className="px-[16px] py-[24px]">
          <div className="text-[#336B97] gap-[4px] mb-[8px] px-[8px]">
          <div className="flex items-center justify-center gap-[10px] ">
            <CalendarIcon size={14} /></div>
          </div>
        </div>

        {/* 로그아웃 */}
        <button onClick={handleLogout} className="text-[#204867] mt-auto mb-[36px] flex items-center gap-2 py-[10px] px-[8px] ml-[16px]" ><LogOut size={14} /></button>
      </div>)}


      {/* 채팅 영역 */}
      <div className="relative z-10 flex flex-1 flex-col my-[32px] ml-[8px] mr-[32px] border-[1px] bg-[#FFFFFF] drop-shadow rounded-[32px] justify-between p-[40px]">
        <div className="flex-1 overflow-y-auto">
          {suggestion.length > 0 && (
            <div>
              <img src={bot} alt="WithBuddy 채팅봇 이미지"/>
              <div className="flex justify-start"> 
                <div className={botClass}>
                  {suggestion[0].content}
                </div>
              </div>
            </div>
          )}
          {messageList.map((message, index) => {
            const currentDate = message.createdAt.slice(0, 10)
            const prevDate = index > 0 ? messageList[index - 1].createdAt.slice(0, 10) : null
            const isNewDate = currentDate !== prevDate

            return(
            <div key={message.id}>
              {isNewDate && (
                <div className="flex items-center justify-center">
                  <p className="border-[1px] border-[#DEE2E6] bg-[#FFFFFF] w-[150px] py-[6px] px-[16px] rounded-[9999px] drop-shadow-sm text-[#495057] text-[14px] text-center">
                    {format(new Date(currentDate), 'yyyy년 M월 d일', {locale: ko})}</p>
                </div>)}
              {message.senderType === 'BOT' && <img src={bot} alt="WithBuddy 채팅봇 이미지"/>}
              <div className={
                message.senderType === 'USER' ? 'flex justify-end' : 'flex justify-start'}>
                  <div className="flex flex-col">
                <div className={
                  message.senderType === 'USER' ? `${userClass}` : `${botClass}`}
                  style={message.senderType === 'USER' ? {background: 'linear-gradient(to right, #7DC1FF, #6BB5F2, #57A7E4, #4F9CD7, #4791CA)'} : {}}>
                    {message.content}
                </div>
                <p className={
                  `${message.senderType === 'USER' ? 'text-right mr-[42px]' : 'text-left ml-[42px]'}`}>
                  <p className="text-[#868E96] text-[16px] ">{format(new Date(message.createdAt), 'a h:mm', {locale: ko})}</p>
                </p>
                </div>
              </div>
            </div>
            )
          })}

        </div>

        {/* 빠른 질문 */}
        <div className="flex items-center gap-[10px] my-[16px]">
          <p className="text-[#868E96] text-[14px]">빠른 질문</p>
          {quickQuestion.map((q, index) => 
          <button
          key={index}
          type="button"
          onClick={() => setText(q.content)}
          className="border-[1px] border-[#DEE2E6] py-[8px] px-[16px] rounded-[9999px] text-[#868E96] text-[14px]
          active:bg-[#F8F9FA] active:border-[#E9ECEF]">
            {q.content}
          </button>
          )}
        </div>

        {/* 입력 창 */}
        <form onSubmit={handleSubmit} className="flex gap-[12px] m-[10px]">
          <input 
          value={text}
          onChange={(e) => setText(e.target.value)}
          className="flex-1 border-[1px] border-[#E9ECEF] rounded-[8px] bg-[#FFFFFF] py-[12px] px-[16px] text-[16px] 
          active:border-[#204867]"
          placeholder="사소한 것도 괜찮아요, 버디에게 무엇이든 물어보세요!" />
          <button className="flex items-center justify-center bg-[#F1F3F5] border-[1px] border-[#E9ECEF] rounded-[8px] w-[40px] h-[48px] active:bg-[#336B97]"><Send size={15} className="text-[#ADB5BD] active:text-[#FFFFFF]" /></button>
        </form>
      </div>
    </div>
  )
}

export default MyBuddy