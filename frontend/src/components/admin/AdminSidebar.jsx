import { NavLink } from "react-router-dom";
import { LogOut, UserRoundCog, ChevronRight, Menu } from "lucide-react";
import char from "../../assets/Favicon_web.svg";
import bar from "../../assets/side_bar.svg";

function AdminSidebar({ isSidebarOpen, setIsSidebarOpen, setIsLogoutModal }) {
  return (
    <div>
      {isSidebarOpen ? (
        <div
          className="flex flex-col w-[232px] lg:w-[255px] fixed top-0 left-0 h-full z-40 md:relative md:z-10 md:top-auto md:left-auto md:h-auto md:mt-[32px]"
          style={{
            backgroundImage: `url('/chat_bg.png')`,
            backgroundSize: "cover",
            backgroundPosition: "center",
            backgroundAttachment: "fixed",
          }}
        >
          {/* 상단 */}
          <div className="flex items-center justify-between py-[12px] px-[16px]">
            <div className="flex items-center">
              <img
                src={char}
                alt="위드버디 대표 로고"
                className="w-[26px] mr-[12px]"
              />
              <p className="text-[#343A40] text-[16px] font-semibold">관리자</p>
            </div>
            {/* 데스크탑: bar 아이콘 */}
            <img
              src={bar}
              className="hidden md:block w-[20px] h-[16px] cursor-pointer"
              onClick={() => setIsSidebarOpen(!isSidebarOpen)}
            />
            {/* 모바일: 햄버거 메뉴 */}
            <button
              className="md:hidden"
              onClick={() => setIsSidebarOpen(false)}
            >
              <Menu size={16} />
            </button>
          </div>

          {/* 메뉴 */}
          <div className="py-[24px] px-[16px]">
            <NavLink
              to="/admin"
              className={({ isActive }) =>
                `flex items-center justify-between py-[10px] px-[12px] rounded-[6px] border-[1px] w-[200px] lg:w-[231px] h-[48px] lg:h-[46px] hover:bg-[#D0EBFFCC] hover:border-[#D0EBFF] text-[#336B97] text-[16px]
                  ${isActive ? "bg-[#EAF6FF] border-[#4791CA]" : "bg-[#FFFFFF] border-[#D0EBFF]"}`
              }
            >
              <div className="flex items-center gap-[10px]">
                <UserRoundCog size={18} />
                <span>계정 관리</span>
              </div>
              <ChevronRight size={16} />
            </NavLink>
          </div>

          {/* 로그아웃 */}
          <button
            className="text-[#204867] mt-auto mb-[36px] flex items-center gap-2 py-[10px] px-[8px] ml-[16px]"
            onClick={() => setIsLogoutModal(true)}
          >
            <LogOut size={14} />
            로그아웃
          </button>
        </div>
      ) : (
        <div className="hidden md:flex relative z-10 w-[76px] flex-col mt-[32px]">
          {/* 상단 */}
          <div className="flex items-center justify-center py-[12px] px-[16px]">
            <img
              src={bar}
              alt="사이드바 토글 아이콘"
              className="w-[20px] h-[16px] cursor-pointer"
              onClick={() => setIsSidebarOpen(!isSidebarOpen)}
            />
          </div>

          {/* 메뉴 */}
          <div className="py-[24px] px-[16px]">
            <NavLink
              to="/admin"
              className={({ isActive }) =>
                `flex items-center justify-center py-[10px] px-[12px] rounded-[6px] border-[1px] w-[44px] h-[48px] hover:bg-[#D0EBFFCC] hover:border-[#D0EBFF] text-[#336B97]
                  ${isActive ? "bg-[#EAF6FF] border-[#4791CA]" : "bg-[#FFFFFF] border-[#D0EBFF]"}`
              }
            >
              <UserRoundCog size={18} />
            </NavLink>
          </div>

          {/* 로그아웃 */}
          <button
            className="text-[#204867] mt-auto mb-[36px] flex items-center gap-2 py-[10px] px-[8px] ml-[16px]"
            onClick={() => setIsLogoutModal(true)}
          >
            <LogOut size={14} />
          </button>
        </div>
      )}
    </div>
  );
}

export default AdminSidebar;
