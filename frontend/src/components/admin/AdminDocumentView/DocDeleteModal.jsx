import axiosInstance from "../../../api/axiosInstance";
import { useState } from "react";

function DocDeleteModal({
  selectedIds,
  documents,
  onClose,
  onSuccess,
  onError,
}) {
  const selectedDocs = documents.filter((doc) =>
    selectedIds.includes(doc.documentId),
  );
  const [isLoading, setIsLoading] = useState(false);

  const handleDelete = async () => {
    setIsLoading(true);
    try {
      await axiosInstance.post(
        "/api/v1/admin/documents/bulk-delete?confirm=true",
        { documentIds: selectedIds },
      );
      onSuccess();
    } catch (error) {
      console.error("문서 삭제 실패", error);
      onError();
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#00000080]">
      <div className="bg-white rounded-[10px] p-[24px] w-[416px] flex flex-col gap-[16px]">
        <h2 className="text-[18px] font-semibold text-[#000000]">
          문서를 삭제할까요?
        </h2>
        {selectedIds.length > 1 ? (
          <p className="text-[14px] text-[#495057]">
            문서를 {selectedIds.length}개 선택했어요.
            <br /> 삭제한 문서는 다시 복구할 수 없어요.
          </p>
        ) : (
          <p className="text-[14px] text-[#495057]">
            '{selectedDocs[0]?.title}'를 삭제하면 다시 복구할 수 없어요.
          </p>
        )}
        <div className="flex gap-[8px] justify-end mt-[8px]">
          <button
            onClick={onClose}
            className="px-[12px] py-[10px] rounded-[8px] text-[#495057] text-[14px] hover:bg-[#F1F3F5]"
          >
            취소하기
          </button>
          <button
            onClick={handleDelete}
            disabled={isLoading}
            className="px-[12px] rounded-[8px] bg-[#FFF5F5] text-[#F03E3E] text-[14px] hover:bg-[#FFE3E3] disabled:opacity-50"
          >
            {isLoading ? "삭제 중..." : "삭제하기"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default DocDeleteModal;
