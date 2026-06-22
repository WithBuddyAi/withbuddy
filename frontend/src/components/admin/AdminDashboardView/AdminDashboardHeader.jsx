function AdminDashboardHeader() {
  return (
    <div className="flex flex-col md:flex-row items-start md:items-center justify-between mt-[14px] pb-[24px] border-b-[1px] border-[#DEE2E6]">
      <div>
        <h1 className="text-[17px] md:text-[20px] lg:text-[22px] font-medium text-[#000000]">
          대시보드
        </h1>
        <p className="text-[12px] md:text-[14px] lg:text-[16px] text-[#495057] mt-[4px] md:mt-[8px] md:mr-[4px]">
          신입이 질문하면 위드버디가 답하고, 답하지 못한 질문은 이 대시보드에서
          확인해 문서를 보강할 수 있어요.
        </p>
      </div>
    </div>
  );
}

export default AdminDashboardHeader;
