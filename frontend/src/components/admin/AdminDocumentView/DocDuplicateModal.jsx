function DocDuplicateModal({ onClose }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#00000080]">
      <div className="bg-white rounded-[10px] p-[24px] w-[416px] flex flex-col gap-[8px]">
        <h2 className="text-[16px] font-medium text-[#000000]">
          동일한 파일명의 문서가 이미 등록되어 있어요.
        </h2>
        <p className="text-[14px] text-[#495057]">
          기존 문서를 삭제한 후 다시 업로드해 주세요.
        </p>
        <div className="flex justify-end mt-[12px]">
          <button
            onClick={onClose}
            className="px-[12px] py-[6px] rounded-[8px] bg-[#336B97] text-white text-[14px] hover:bg-[#183348]"
          >
            확인
          </button>
        </div>
      </div>
    </div>
  );
}

export default DocDuplicateModal;
