function AdminDocHeader({ handleViewChange }) {
  return (
    <div className="flex flex-col md:flex-row items-start md:items-center justify-between my-[14px]">
      <div className="mb-[10px] md:mb-[16px]">
        <h1 className="text-[17px] md:text-[20px] lg:text-[22px] font-medium text-[#000000]">
          문서 관리
        </h1>
        <p className="text-[12px] md:text-[14px] lg:text-[16px] text-[#495057] mt-[4px] md:mt-[8px] md:mr-[4px]">
          문서를 업로드하면 신입 사원의 질문에 위드버디가 자동으로 답변해요.
          등록된 문서 목록과 처리 상태를 아래에서 확인할 수 있어요.
        </p>
      </div>
    </div>
  );
}

export default AdminDocHeader;
