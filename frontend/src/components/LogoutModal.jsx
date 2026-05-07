function LogoutModal({ isLogoutModal, setIsLogoutModal, handleLogout }) {
  return(
    <div>
      {isLogoutModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#00000080]">
          <div className="w-[289px] h-[158px] bg-[#FFFFFF] p-[24px] rounded-[10px] drop-shadow">
            <div className="flex flex-col gap-[10px]">
              <p className="text-[18px] font-semibold">로그아웃 하시겠어요?</p>
              <p className="text-[14px] text-[#868E96]">도움이 필요할 때 언제든 다시 찾아주세요!</p>
            </div>
            <div className="flex justify-end gap-[12px] mt-[20px]">
                <button onClick={() => setIsLogoutModal(false)} className="w-[52px] h-[32px] border-[1px] border-[#E9ECEF] hover:bg-[#E9ECEF] px-[12px] rounded-[8px] text-[#868E96] text-[14px]">취소</button>
              <button onClick={handleLogout} className="w-[76px] h-[32px] bg-[#868E96] hover:bg-[#585A5C] px-[12px] rounded-[8px] text-[#FFFFFF] text-[14px]">로그아웃</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default LogoutModal