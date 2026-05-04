import { NavLink, useLocation, useNavigate } from "react-router-dom"
import { MessageSquare, Menu } from "lucide-react"
import bot from '../assets/Bot_icon.svg'
import { useEffect, useRef, useState } from "react"
import { format } from 'date-fns';
import axios from "axios";
import axiosInstance from "../api/axiosInstance"
import { useUser } from "../contexts/UserContext"
import Sidebar from "../components/Sidebar"
import LogoutModal from "../components/LogoutModal"
import ErrorToast from "../components/ErrorToast"
import MessageList from "../components/MessageList";
import QuickQuestions from "../components/QuickQuestions";
import ChatInput from "../components/ChatInput";

function MyBuddy ({setIsLoggedIn}) {
  const { dayOffset } = useUser()
  const dayCount = dayOffset !== undefined && dayOffset !== null ? dayOffset : localStorage.getItem('dayCount')
  // 사이드바에 표시되는 정보 state
  const name = localStorage.getItem('name')
  const today = format(new Date(), 'yyyy-MM-dd')
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
  const [quickQuestion, setQuickQuestion] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState(false)
  const chatBottomRef = useRef(null)
  const lastUserMessageRef = useRef(null)
  const [loadingMessage, setLoadingMessage] = useState('')
  const [hasSubmitted, setHasSubmitted] = useState(false)


  // 대화 기록 달력
  const handleDateChange = async (date) => {
    setSelectedDate(date)
    const formattedDate = format(date, 'yyyy-MM-dd')
    try {
      const {data: message } = await axiosInstance.get(
        `/api/v1/chat/messages?date=${formattedDate}`
      )
      setMessageList([
        ...message.messages])
    } catch (error) {
      const serverMessage = error.response?.data?.errors?.[0]?.message
      setErrorMessage(serverMessage || '에러가 발생했어요')
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

  // 첫 렌딩 시 화면
  useEffect (() => {
    setIsLoading(true)
    const fetchData = async () => {
      try {
        const [messageResponse, quickResponse] = await Promise.allSettled([
          axiosInstance.get('/api/v1/chat/messages', {}),
          axiosInstance.get('/api/v1/chat/quick-questions', {})
        ])
        if (messageResponse.status === 'fulfilled') {
          const messages = messageResponse.value.data.messages

          const dates = [...new Set([...messages.map(m => m.createdAt.slice(0, 10))]
          )]
          setActiveDates(dates)
          
          setMessageList(messages)

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

        } else {
          const error = messageResponse.reason
          throw error
        }
        if (quickResponse.status === 'fulfilled') {
          setQuickQuestion(quickResponse.value.data.quickQuestions)
        }
      } catch (error) {
        const serverMessage = error.response?.data?.errors?.[0]?.message
        setErrorMessage(serverMessage || '에러가 발생했어요. 다시 시도해 주세요.')
      } finally {
        setIsLoading(false)
      }
    } 
  fetchData()
  const sessionStart = async () => {
    try {
      await axiosInstance.post(
        '/api/v1/chat/session-start',
        {}
      )
    } catch (error) {
      console.error('session-start 실패:', error)
    }
  }
  sessionStart()
  }, [])
  
  // 자동 스크롤
  useEffect(() => {
    if (isLoading) {
      chatBottomRef.current?.scrollIntoView({
        behavior: "smooth",
      })
    } else if (hasSubmitted) {
      lastUserMessageRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start"
      })
    } else {
      chatBottomRef.current?.scrollIntoView({
        behavior: "auto",
      })
    }
  }, [messageList, isLoading, loadingMessage])

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
      const timer1 = setTimeout(() => {setLoadingMessage(`거의 완성됐어요! ${name}님을 돕기 위해 최선을 다하는 중입니다! 😊`)}, 5000)
      return () => {
        clearTimeout(timer1)
      }
    } else {
      setLoadingMessage('')
    }
  }, [isLoading])

  // 사용자 질문 전송
  const handleSubmit = async (e, submitText) => {
    e?.preventDefault()
    const sendText = submitText
    if (!sendText.trim()) return
    setIsLoading(true)
    setHasSubmitted(true)
    setMessageList(prev => [...prev, {
      id: `temp-${Date.now()}`,
      senderType: 'USER',
      content: sendText,
      createdAt: new Date().toISOString()
    }])

    let data = null

    try {
      const response = await axiosInstance.post('/api/v1/chat/messages',
        {content: sendText}
      )
      data = response.data
      if (data.answer !== null) {
        setMessageList(prev => [...prev, data.answer])
        setActiveDates(prev => prev.includes(today) ? prev : [...prev, today])
      }
    } catch (error) {
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
        setMessageList(prev => [...prev, {
          id: `error-${Date.now()}`,
          senderType: 'BOT',
          messageType: 'send_error',
          content: '메시지 전송에 실패했어요. 다시 시도해 주세요.',
          createdAt: new Date().toISOString()
        }])
      }
    } finally {
      if (data?.status !== 'PENDING') {
        setIsLoading(false)
      } else {
        setLoadingMessage('잠시만요! 우리 사내 문서에서 관련 내용을 꼼꼼히 찾아보고 있어요!')
        setTimeout(() => {
          setLoadingMessage(`거의 완성됐어요! ${name}님을 돕기 위해 최선을 다하는 중입니다! 😊`)
        }, 5000);
        setTimeout(() => {
          setIsLoading(false)
          setMessageList(prev => [...prev, {
            id: `error-${Date.now()}`,
            senderType: 'BOT',
            messageType: 'ai_timeout',
            content: 'AI 답변 생성 시간이 초과됐어요. 잠시 후 다시 시도해 주세요.',
            createdAt: new Date().toISOString()
          }])
        }, 10000);
      }
    }
  }

  // 응답 지연 시 재시도
  const handleRetry = () => {
    const lastUserMessage = [...messageList].reverse().find(msg => msg.senderType === 'USER')
    if (lastUserMessage) {
      setMessageList(prev => prev.filter(msg => msg.messageType !== 'ai_timeout' && msg.messageType !== 'send_error' && msg.id !== lastUserMessage.id))
      handleSubmit(null, lastUserMessage.content)
    }
  }

  // 파일 다운로드
  const handleDownload = async (downloadUrl, fileName) => {
    try {
      const isAbsoluteUrl = /^https?:\/\//i.test(downloadUrl)
      let resolvedUrl = downloadUrl

      if (!isAbsoluteUrl) {
        const { data } = await axiosInstance.get(downloadUrl)
        if (typeof data?.downloadUrl === 'string' && data.downloadUrl.length > 0) {
          resolvedUrl = data.downloadUrl
        }
      }

      const isApiPath = /^\/api\//.test(resolvedUrl)
      const response = isApiPath
        ? await axiosInstance.get(resolvedUrl, { responseType: 'blob' })
        : await axios.get(resolvedUrl, { responseType: 'blob' })

      const blob = new Blob([response.data])
      const objectUrl = URL.createObjectURL(blob)

      const a = document.createElement('a')
      a.href = objectUrl
      a.download = fileName
      document.body.appendChild(a)
      a.click()

      document.body.removeChild(a)
      URL.revokeObjectURL(objectUrl)

    } catch (error) {
      setErrorMessage('파일 다운로드에 실패했어요. 다시 시도해 주세요.')
    }
  }

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
      {/* 전송 실패 에러 메시지 - 컴포넌트 분리 완료 */}
      <ErrorToast errorMessage={errorMessage}/>

      {/* 로그아웃 모달 - 컴포넌트 분리 완료 */}
      <LogoutModal
        isLogoutModal={isLogoutModal}
        setIsLogoutModal={setIsLogoutModal}
        handleLogout={handleLogout}  
      />

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

      {/* 사이드바 - 컴포넌트 분리 완료 */}
      <Sidebar
        name={name}
        dayCount={dayCount}
        isSidebarOpen={isSidebarOpen}
        setIsSidebarOpen={setIsSidebarOpen}
        selectedDate={selectedDate}
        activeDates={activeDates}
        handleDateChange={handleDateChange}
        setIsLogoutModal={setIsLogoutModal}
        />

      {/* 채팅 영역 */}
      <div className="relative z-10 flex flex-1 flex-col md:my-[32px] md:ml-[8px] md:mr-[32px] border-[1px] bg-[#FFFFFF] drop-shadow md:rounded-[32px] justify-between md:p-[40px] overflow-hidden">
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
        <MessageList
          messageList={messageList}
          botClass={botClass}
          handleSubmit={handleSubmit}
          handleRetry={handleRetry}
          isLoading={isLoading}
          handleDownload={handleDownload}
          lastUserMessageRef={lastUserMessageRef}
        />

          {/* 로딩 인디케이터 */}
          {isLoading && (
            <div className="flex justify-start items-start mt-[32px]">
              <img src={bot} alt="WithBuddy 채팅봇 이미지"/>
              <div className={botClass}>
                <div className="flex gap-1">
                  <div className="w-2 h-2 bg-[#CED4DA] rounded-full animate-bounce" style={{animationDelay: '0ms'}}/>
                  <div className="w-2 h-2 bg-[#868E96] rounded-full animate-bounce" style={{animationDelay: '150ms'}}/>
                  <div className="w-2 h-2 bg-[#868E96] rounded-full animate-bounce" style={{animationDelay: '300ms'}}/>
                </div>
                {loadingMessage && <p className="mt-[18px]">{loadingMessage}</p>}
              </div>
            </div>
          )}

          {/* 질문 전송 시 하단으로 자동 스크롤 */}
          <div ref={chatBottomRef}/>
        </div>

        {/* 빠른 질문 */}
        <QuickQuestions
          quickQuestion={quickQuestion}
          handleSubmit={handleSubmit}
        />

        {/* 입력 창 */}
        <ChatInput
          handleSubmit={handleSubmit}
          isLoading={isLoading}
        />
      </div>
    </div>
  )
}

export default MyBuddy
