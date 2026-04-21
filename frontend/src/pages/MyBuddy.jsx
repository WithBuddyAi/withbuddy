import { NavLink, useLocation, useNavigate } from "react-router-dom"
import { MessageSquare, RotateCw, ChevronRight, Send, LogOut, Menu, Calendar as CalendarIcon } from "lucide-react"
import char from '../assets/Favicon_web.svg'
import bot from '../assets/Bot_icon.svg'
import bar from '../assets/side_bar.svg'
import { useEffect, useRef, useState } from "react"
import { format } from 'date-fns';
import { ko } from 'date-fns/locale';
import axios from "axios";
import Calendar from "react-calendar"
import 'react-calendar/dist/Calendar.css'
import ReactMarkdown from 'react-markdown'

function MyBuddy ({setIsLoggedIn}) {
  // 사이드바에 표시되는 정보 state
  const name = localStorage.getItem('name')
  const dayCount = localStorage.getItem('dayCount')
  // const hireDate = localStorage.getItem('hireDate')
  const today = format(new Date(), 'yyyy-MM-dd')
  // const progress = Math.min(Math.round((Number(dayCount) / 90) * 100), 100)
  const [selectedDate, setSelectedDate] = useState(null)
  const [isSidebarOpen, setIsSidebarOpen] = useState(window.innerWidth >= 768)
  const [activeDates, setActiveDates] = useState([])
  const [isLogoutModal, setIsLogoutModal] = useState(false)
  const location = useLocation()
  const navItems = [
    { path: '/mybuddy', label: '마이버디', icon: <MessageSquare size={14}/>}
  ]
  
  const navigate = useNavigate()

  // 채팅 화면
  const [messageList, setMessageList] = useState([])
  const [text, setText] = useState('')
  const [quickQuestion, setQuickQuestion] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const BASE_URL = import.meta.env.VITE_API_BASE_URL
  const accessToken = localStorage.getItem('accessToken')
  const [errorMessage, setErrorMessage] = useState(false)
  const chatBottomRef = useRef(null)
  const [loadingMessage, setLoadingMessage] = useState('')
  const [suggestionMessages, setSuggestionMessages] = useState([])

  // 대화 기록 달력
  const handleDateChange = async (date) => {
    setSelectedDate(date)
    const formattedDate = format(date, 'yyyy-MM-dd')
    try {
      const {data: message } = await axios.get(
        `${BASE_URL}/api/v1/chat/messages?date=${formattedDate}`,
        { headers: { 'Authorization' : `Bearer ${accessToken}` } }
      )
      setMessageList([
        ...message.messages,
        ...suggestionMessages.filter(s => s.createdAt.slice(0, 10) === formattedDate)
      ].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)))
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
      const code = error.response?.data?.code
      if (code === 'TOKEN_MISSING' || code === 'TOKEN_EXPIRED' || code === 'INVALID_TOKEN' || code === 'USER_NOT_FOUND') {
        const tokenError = error.response?.data?.errors[0]?.message
        localStorage.removeItem('accessToken')
        localStorage.removeItem('dayCount')
        localStorage.removeItem('hireDate')
        localStorage.removeItem('name')
        setIsLoggedIn(false)
        navigate('/login', {
          state: { tokenError }
        })
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
        if (messageResponse.status === 'fulfilled' && suggestionResponse.status === 'fulfilled') {
          const messages = messageResponse.value.data.messages
          const suggestions = suggestionResponse.value.data.suggestions
          const suggestionMessages = suggestions.map(s => ({
            id: `suggestion-${s.id}`,
            senderType: 'BOT',
            messageType: 'suggestion',
            content: s.content,
            createdAt: s.createdAt
          }))
          setSuggestionMessages(suggestionMessages)
          const dates = [...new Set([...messages.map(m => m.createdAt.slice(0, 10)),
          ...suggestionMessages.map(s => s.createdAt.slice(0, 10))]
          )]
          setActiveDates(dates)
          
          setMessageList([...messages, ...suggestionMessages].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)))

          // // 환영메시지 작성 (suggestion이랑 기능이 유사해서 우선 주석 처리함)
          // const isFirstLogin = localStorage.getItem('isFirstLogin')

          // if (!isFirstLogin) {
          //   localStorage.setItem('isFirstLogin', 'true')
          //   setMessageList( [{
          //     id: 'welcome',
          //     senderType: 'BOT',
          //     content: `${name}님, 만나서 반가워요😊\n저는 ${name}님의 회사 생활을 함께 할 위드버디예요.\n\n인사(연차/급여)부터 행정(비품/보안/시설)까지,\n회사 생활에 필요한 모든 정보를 편하게 물어봐 주세요!\n\n오늘부터 제가 ${name}님의 든든한 위드버디가 되어 드릴게요!`,
          //     createdAt: new Date().toISOString()
          //   }, ...messages])
          // } else {
          //   setMessageList(messages)
          // }

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
  const sessionStart = async () => {
    try {
      await axios.post(
        `${BASE_URL}/api/v1/chat/session-start`,
        {},
        { headers: {'Authorization': `Bearer ${accessToken}`}}
      )
    } catch (error) {
      console.error('session-start 실패:', error)
    }
  }
  sessionStart()
  }, [])
  
  // 자동 스크롤
  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({
      behavior: messageList.length > 1 ? "smooth" : "auto",
    })
  }, [messageList, isLoading])

  // 에러 토스트 자동 사라짐
  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(false)
      }, 3000)
      return () => clearTimeout(timer)
    }
  }, [errorMessage])

  // 답변 지연 메시지
  useEffect(() => {
    if (isLoading) {
      setLoadingMessage('잠시만요! 우리 사내 문서에서 관련 내용을 꼼꼼히 찾아보고 있어요!')
      const timer1 = setTimeout(() => {setLoadingMessage(`거의 완성됐어요! ${name}님을 돕기 위해 최선을 다하는 중입니다! 😊`)}, 2000)
      return () => {
        clearTimeout(timer1)
      }
    } else {
      setLoadingMessage('')
    }
  }, [isLoading])

  // 사용자 질문 전송
  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!text.trim()) return
    setIsLoading(true)
    const sendText = text
    setMessageList(prev => [...prev, {
      id: `temp-${Date.now()}`,
      senderType: 'USER',
      content: sendText,
      createdAt: new Date().toISOString()
    }])
    setText('')

    try {
      const {data} = await axios.post(`${BASE_URL}/api/v1/chat/messages`,
        {content: sendText},
        {headers: { 'Authorization': `Bearer ${accessToken}` }}
      )
      
      setMessageList(prev => [...prev, data.answer])
      setText('')
      setActiveDates(prev => prev.includes(today) ? prev : [...prev, today])
    } catch (error) {
      if (!handle401(error)) {
        if (error.response?.status === 400) {
          error.response.data.errors.forEach(err => {
            if (err.field === 'content') {
              setErrorMessage(err.message)
            }
          })
        } else if (error.response?.status === 504 || (!error.response && error.message?.includes('504'))) {
            const timeoutMessage = error.response?.data?.errors?.[0]?.message
            setMessageList(prev => [...prev, {
              id: `error-${Date.now()}`,
              senderType: 'BOT',
              messageType: 'ai_timeout',
              content: timeoutMessage || 'AI 답변 생성 시간이 초과됐어요. 잠시 후 다시 시도해 주세요.',
              createdAt: new Date().toISOString()
            }])
        } else {
          setErrorMessage('메시지 전송에 실패했어요.')
        }
      }
    } finally {
    setIsLoading(false)
    }
  }

  // 응답 지연 시 재시도
  const handleRetry = () => {
    const lastUserMessage = [...messageList].reverse().find(msg => msg.senderType === 'USER')
    if (lastUserMessage) {
      setMessageList(prev => prev.filter(msg => msg.messageType !== 'ai_timeout' && msg.id !== lastUserMessage.id))
      setText(lastUserMessage.content)
    }
  }

  // User Class 정리
  const userClass = 
  `rounded-tl-[24px]
  rounded-tr-[4px]
  lg:rounded-tr-[8px]
  rounded-bl-[24px]
  rounded-br-[24px]
  text-[#FFFFFF]
  text-[12px]
  md:text-[16px]
  text-left
  max-w-[310px]
  md:max-w-[550px]
  lg:max-w-[800px]
  p-[16px]
  md:px-[24px]
  whitespace-pre-wrap
  mt-[20px]
  mb-[12px]
  md:mr-[42px]
  drop-shadow
  `
  // Bot Class 정리
  const botClass = 
  `
  lg:rounded-tl-[8px]
  rounded-tl-[4px]
  rounded-tr-[24px]
  rounded-bl-[24px]
  rounded-br-[24px]
  border-[#E9ECEF]
  border-[1px]
  bg-[#FFFFFF]
  text-[#000000]
  text-[12px]
  md:text-[16px]
  text-left
  max-w-[310px]
  md:max-w-[500px]
  lg:max-w-[800px]
  p-[16px]
  lg:py-[20px]
  lg:px-[24px]
  whitespace-pre-wrap
  mb-[12px]
  ml-[16px]
  drop-shadow
  y-2
  `

  return (
    <div className="h-screen flex relative overflow-hidden">
      {/* 전송 실패 에러 메시지 */}
      {errorMessage && (
        <div className="fixed bottom-[40px] left-1/2 -translate-x-1/2 z-50
          bg-[#343A40] text-white text-[14px]
          py-[12px] px-[24px] rounded-[9999px] drop-shadow-lg
          whitespace-nowrap">
          ⚠️ {errorMessage}
        </div>
      )}

      {/* 로그아웃 모달 */}
      {isLogoutModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#00000080]">
          <div className="w-[289px] h-[158px] bg-[#FFFFFF] p-[24px] rounded-[10px] drop-shadow">
            <div className="flex flex-col gap-[10px]">
              <p className="text-[18px] font-semibold">로그아웃 하시겠어요?</p>
              <p className="text-[14px] text-[#868E96]">도움이 필요할 때 언제든 다시 찾아주세요!</p>
            </div>
            <div className="flex justify-end gap-[12px] mt-[20px]">
              <button onClick={() => setIsLogoutModal(false)} className="w-[52px] h-[32px] border-[1px] border-[#E9ECEF] px-[12px] rounded-[8px] text-[#868E96] text-[14px]">취소</button>
              <button onClick={handleLogout} className="w-[76px] h-[32px] bg-[#868E96] px-[12px] rounded-[8px] text-[#FFFFFF] text-[14px]">로그아웃</button>
            </div>
          </div>
        </div>
      )}

      {/* 배경 이미지 적용 */}
      <div className="absolute inset-0 z-0"
      style={{
        backgroundImage: `url('/chat_bg.png')`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        backgroundAttachment: 'fixed'
      }}>
      </div>

      {/* 모바일 사이드바 오버레이 */}
      {isSidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-[#00000080] md:hidden"
          onClick={() => setIsSidebarOpen(false)}
        />
      )}

      {/* 사이드바 - 추후 컴포넌트 분리 필요 */}
      {isSidebarOpen ? (<div className="flex flex-col w-[232px] lg:w-[255px] fixed top-0 left-0 h-full z-40 md:relative md:z-10 md:top-auto md:left-auto md:h-auto md:mt-[32px]"
      style={{
        backgroundImage: `url('/chat_bg.png')`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        backgroundAttachment: 'fixed'
      }}>
        {/* 사용자 정보 부분 */}
        <div>
          <div className="flex items-center justify-between py-[12px] px-[16px]">
            <div className="flex items-center">
              <img src={char} alt="위드버디 대표 로고" className="w-[26px] mr-[12px]"/>
              <div className="flex items-center"><p className="text-[#343A40] text-[16px] font-semibold mr-[8px]">{name}</p>
              <p className="text-[#20486799] text-[12px]">Day {dayCount}</p>
              </div>
            </div>
            {/* 데스크탑: bar 아이콘 */}
            <img src={bar} 
              className="hidden md:block w-[20px] h-[16px] cursor-pointer" 
              onClick={() => setIsSidebarOpen(!isSidebarOpen)}/>

            {/* 모바일: 햄버거 메뉴 */}
            <button className="md:hidden" onClick={() => setIsSidebarOpen(false)}>
              <Menu size={16}/>
            </button>
          </div>
        </div>

        {/* 메뉴 탭 부분 */}
        <div className="py-[24px] px-[16px]">
          <NavLink to='/mybuddy' className={({isActive}) => `flex items-center justify-between py-[10px] px-[12px] rounded-[6px] border-[1px] w-[200px] lg:w-[231px] h-[48px] lg:h-[46px] hover:bg-[#D0EBFFCC] hover:border-[#D0EBFF] text-[#336B97] text-[16px]
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
              <CalendarIcon size={14} />대화기록
            </div>
            <p className="text-[#868E96] text-[12px]">날짜를 선택하면 해당 날짜의 대화 내용을 확인할 수 있습니다.</p>
          </div>

          <div className="flex justify-center">
            <Calendar
              onChange={handleDateChange}
              value={selectedDate}
              locale="ko-KR"
              formatDay={(locale, date) => date.getDate()}
              calendarType="gregory"
              prev2Label={null}
              next2Label={null}
              tileDisabled={({ date, view }) => {
                if (view === 'month') {
                  const formatted = format(date, 'yyyy-MM-dd')
                  return !activeDates.includes(formatted)
                }
                return false
              }}
              tileContent={({ date }) => {
                const formatted = format(date, 'yyyy-MM-dd')
                if (activeDates.includes(formatted)) {
                  return <div className="absolute bottom-[1px] flex justify-center w-[full]">
                    <div className="w-[4px] h-[4px] rounded-full bg-[#7DC1FF]"/>
                  </div>
                }
              }}
              tileClassName={({ date }) => {
                const formatted = format(date, 'yyyy-MM-dd')
                if (activeDates.includes(formatted)) {
                  return 'has-chat'
                }
              }}
            />
          </div>
        </div>

        {/* 로그아웃 */}
        <button onClick={() => setIsLogoutModal(true)} className="text-[#204867] mt-auto mb-[36px] flex items-center gap-2 py-[10px] px-[8px] ml-[16px]" ><LogOut size={14} />로그아웃</button>
      </div>) 

      : 
      
      (<div className="hidden md:flex relative z-10 w-[76px] flex-col mt-[32px]">
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
        <button onClick={() => setIsLogoutModal(true)} className="text-[#204867] mt-auto mb-[36px] flex items-center gap-2 py-[10px] px-[8px] ml-[16px]" ><LogOut size={14} /></button>
      </div>)}


      {/* 채팅 영역 */}
      <div className="relative z-10 flex flex-1 flex-col md:my-[32px] md:ml-[8px] md:mr-[32px] border-[1px] bg-[#FFFFFF] drop-shadow md:rounded-[32px] justify-between  md:p-[40px]">
        {/* 모바일 헤더 */}
        <>
          <div className="flex md:hidden items-center py-[16px] px-[24px] bg-[#EAF6FF]">
            <button onClick={() => setIsSidebarOpen(!isSidebarOpen)}>
              <Menu size={16}/>
            </button>
            <div className="absolute left-1/2 -translate-x-1/2 flex items-center gap-[8px] text-[#336B97]">
              {navItems.map(item => 
              location.pathname === item.path && (
                <div key={item.path} className="flex items-center justify-center gap-[10px]">
                  {item.icon}
                  <p>{item.label}</p>
                </div>
              ))}
            </div>
          </div>
        </>

        <div className="flex-1 overflow-y-auto px-[24px] pb-[16px]">
        {/* 답변(messageList에 온보딩 제안도 포함됨) */}
          {messageList.map((message, index) => {
            const currentDate = message.createdAt.slice(0, 10)
            const prevDate = index > 0 ? messageList[index - 1].createdAt.slice(0, 10) : null
            const isNewDate = currentDate !== prevDate

            return(
            <div key={message.id}>
              {isNewDate && (
                <div className="flex items-center justify-center">
                  <p className="border-[1px] border-[#DEE2E6] bg-[#FFFFFF] w-[130px] md:w-[150px] py-[6px] px-[16px] rounded-[9999px] drop-shadow-sm text-[#495057] text-[12px] md:text-[14px] text-center mt-[16px]">
                    {format(new Date(currentDate), 'yyyy년 M월 d일', {locale: ko})}</p>
                </div>)}
              
              <div className={
                message.senderType === 'USER' ? 'flex justify-end' : 'flex justify-start items-start mt-[32px]'}>
                {message.senderType === 'BOT' && <img src={bot} alt="WithBuddy 채팅봇 이미지"/>}
                <div className="flex flex-col">
                  <div className={
                    message.senderType === 'USER' ? `${userClass}` : `${botClass}`}
                    style={message.senderType === 'USER' ? {background: 'linear-gradient(to right, #7DC1FF, #6BB5F2, #57A7E4, #4F9CD7, #4791CA)'} : {}}>

                    {message.messageType === 'ai_timeout' ? (
                      <div className="flex gap-[10px]">
                        <p className="text-[#495057] text-[16px]">{message.content}</p>
                        <button onClick={handleRetry} className="bg-blue-100 rounded-[9999px] py-[6px] px-[12px] text-[#204867] text-[12px]">
                          <RotateCw size={14} />다시 시도하기
                        </button>
                      </div>)
                      :
                      (<ReactMarkdown>{message.content}</ReactMarkdown>)
                    }
                  </div>

                  <p className={
                    `${message.senderType === 'USER' ? 'text-right md:mr-[48px]' : 'text-left ml-[16px]'}`}>
                    <p className="text-[#868E96] text-[10px] md:text-[16px] ">{format(new Date(message.createdAt), 'a h:mm', {locale: ko})}</p>
                  </p>
                </div>
              </div>
            </div>
            )
          })}

          {/* 로딩 인디케이터 */}
          {isLoading && (
            <div className="flex justify-start items-start mt-[32px]">
              <img src={bot} alt="WithBuddy 채팅봇 이미지"/>
              <div className={botClass}>
                <div className="flex gap-1">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '0ms'}}/>
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '150ms'}}/>
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '300ms'}}/>
                </div>
                {loadingMessage && <p className="mt-[18px]">{loadingMessage}</p>}
              </div>
            </div>
          )}

          {/* 질문 전송 시 하단으로 자동 스크롤 */}
          <div ref={chatBottomRef}/>
        </div>

        {/* 빠른 질문 */}
        <div className="flex items-center gap-[10px] my-[16px] mx-[16px]">
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
        <form onSubmit={handleSubmit} className="flex gap-[12px] mb-[10px] mx-[10px]">
          <textarea 
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              handleSubmit(e)
            }
          }}
          disabled={isLoading}
          className="flex-1 border-[1px] border-[#E9ECEF] rounded-[8px] bg-[#FFFFFF] py-[16px] md:py-[8px] px-[16px] text-[12px] md:text-[14px] lg:text-[16px] h-[52px] md:h-[40px] lg:h-[44px]
          focus:border-[#204867] fucus:border-[1px] focus:outline-none resize-none"
          placeholder="사소한 것도 괜찮아요, 버디에게 무엇이든 물어보세요!" />
          <button 
          className="flex items-center justify-center bg-[#F1F3F5] border-[1px] border-[#E9ECEF] rounded-[8px] w-[40px] h-[44px] md:h-[48px] text-[#ADB5BD] active:text-[#FFFFFF] active:bg-[#336B97] active:enabled:bg-[#336B97]"
          disabled={!text.trim() || isLoading}>
            <Send size={15} className="text-inherit" />
          </button>
        </form>
      </div>
    </div>
  )
}

export default MyBuddy