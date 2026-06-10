function AdminHeader({ handleViewChange }) {
  return (
    <div className="flex flex-col md:flex-row items-start md:items-center justify-between my-[14px]">
      <div className="mb-[10px] md:mb-[16px]">
        <h1 className="text-[17px] md:text-[20px] lg:text-[22px] font-medium text-[#000000]">
          계정 관리
        </h1>

        <p className="text-[12px] md:text-[14px] lg:text-[16px] text-[#495057] mt-[4px] md:mt-[8px] md:mr-[4px]">
          계정을 생성하면 신입 사원이 위드버디에 접속할 수 있어요. 온보딩 참여
          현황은 아래 목록에서 확인하세요.
        </p>
      </div>

      <button
        onClick={() => handleViewChange("new")}
        className="shrink-0 whitespace-nowrap bg-[#F1F3F5] text-[#868E96] text-[12px] w-[115px] p-[8px] md:text-[14px] md:w-[131px] md:p-[12px] rounded-[8px] hover:bg-[#204867] hover:text-[#FFFFFF] active:bg-[#336B97] active:text-[#FFFFFF]"
      >
        + 계정 생성하기
      </button>
    </div>
  );
}

export default AdminHeader;
