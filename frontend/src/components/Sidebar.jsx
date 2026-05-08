import { NavLink } from "react-router-dom"
import { MessageSquare, ChevronRight, LogOut, Menu, Calendar as CalendarIcon } from "lucide-react"
import char from '../assets/Favicon_web.svg'
import bar from '../assets/side_bar.svg'
import { format } from 'date-fns'
import Calendar from "react-calendar"
import 'react-calendar/dist/Calendar.css'
import { useState } from "react"

function Sidebar({ name, dayCount, isSidebarOpen, setIsSidebarOpen, selectedDate, activeDates, handleDateChange, setIsLogoutModal }) {
  const [activeStartDate, setActiveStartDate] = useState(new Date())
  const [view, setView] = useState('month')

  return(
    <div className="contents">
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
                    <p className="text-[#20486799] text-[12px]">Day {Number(dayCount) >= 0 ? Number(dayCount) + 1 : Number(dayCount)}</p>
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

                <div className="relative flex justify-center">
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      setActiveStartDate(new Date())
                      setView('month')
                      handleDateChange(new Date())
                    }}
                    className="absolute top-[11px] md:top-[18px] right-[18px] md:right-[13px] text-[#336B97] text-[12px] md:text-[13px] hover:underline hover:underline-offset-2 hover:decoration-[#336B97]">
                    오늘
                  </button>
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
                        return <div className="absolute bottom-[3px] flex justify-center w-[full]">
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
                    view={view}
                    onViewChange={({ view }) => setView(view)}
                    activeStartDate={activeStartDate}
                    onActiveStartDateChange={({ activeStartDate }) => setActiveStartDate(activeStartDate)}
                    navigationLabel={({ label }) => (
                      <div className="flex items-center justify-center w-full">
                        <span>{label}</span>
                      </div>
                    )}
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
    </div>
  )
}

export default Sidebar