import { CircleCheck, CircleX, RotateCcw } from "lucide-react";

function DocToast({ type, onRetry }) {
  if (!type) return null;

  return (
    <div className="fixed bottom-[70px] right-[70px] z-50 flex items-center gap-[8px] bg-white border border-[#DEE2E6] rounded-[8px] px-[16px] py-[12px] shadow-md text-[14px]">
      {type === "success" ? (
        <>
          <CircleCheck size={16} />
          문서가 삭제됐어요.
        </>
      ) : (
        <>
          <CircleX size={16} />
          문서 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.
          <button
            onClick={onRetry}
            className="flex items-center gap-[4px] rounded-[9999px] px-[12px] py-[6px] bg-[#EAF6FF] text-[#204867] ml-[8px]"
          >
            <RotateCcw size={14} />
            다시 시도
          </button>
        </>
      )}
    </div>
  );
}

export default DocToast;
