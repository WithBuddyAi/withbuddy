function AdminMainView({ handleViewChange }) {
  return (
    <div>
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between my-[16px]">
        <div className="mb-[16px]">
          <h1 className="text-[18px] md:text-[22px] font-medium text-[#000000]">
            계정 관리
          </h1>
          <p className="text-[14px] md:text-[16px] text-[#495057] mt-[8px]">
            신입 사원의 계정을 생성하고 등록 현황을 확인해요.
          </p>
        </div>
        <button
          onClick={() => handleViewChange("new")}
          className="bg-[#F1F3F5] text-[#868E96] text-[14px] w-[131px] p-[12px] rounded-[8px] hover:bg-[#204867] hover:text-[#FFFFFF] active:bg-[#336B97] active:text-[#FFFFFF]"
        >
          + 계정 생성하기
        </button>
      </div>
    </div>
  );
}

export default AdminMainView;
