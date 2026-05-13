import axiosInstance from "../api/axiosInstance";

function QuickQuestions({ quickQuestion, handleSubmit, isLoading }) {
  return (
    <div className="flex items-center gap-[10px] pb-[10px] whitespace-nowrap my-[16px] mx-[16px] overflow-x-auto">
      <p className="text-[#868E96] text-[11px] md:text-[12px] lg:text-[14px]">
        빠른 질문
      </p>
      {quickQuestion.map((q, index) => (
        <button
          key={index}
          type="button"
          onClick={async () => {
            if (isLoading) return;
            try {
              await axiosInstance.post("/api/v1/chat/quick-questions/click", {
                eventTarget: q.eventTarget,
              });
            } catch (error) {
              console.error("클릭 로그 기록 실패:", error);
            }
            handleSubmit(null, q.content);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") e.preventDefault();
          }}
          disabled={isLoading}
          className="border-[1px] border-[#DEE2E6] py-[8px] px-[16px] rounded-[9999px] text-[#495057] text-[11px] md:text-[12px] lg:text-[14px]
        hover:bg-[#E9ECEF] hover:border-[#E9ECEF]"
        >
          {q.buttonText}
        </button>
      ))}
    </div>
  );
}

export default QuickQuestions;
