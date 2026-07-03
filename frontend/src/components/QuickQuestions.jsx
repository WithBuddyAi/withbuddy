import { trackEvent } from "../utils/tracking";
import { useUser } from "../contexts/UserContext";

function QuickQuestions({ quickQuestion, handleSubmit, isLoading }) {
  const { accountStatus } = useUser();

  return (
    <div className="flex items-center gap-[10px] pb-[10px] whitespace-nowrap my-[16px] mx-[16px] overflow-x-auto">
      <p className="text-[#868E96] text-[11px] md:text-[12px] lg:text-[14px]">
        빠른 질문
      </p>
      {quickQuestion.map((q, index) => (
        <button
          key={index}
          type="button"
          onClick={() => {
            // GA4 사용자 트래킹용 이벤트
            trackEvent("quick_question_click", {
              button_text: q.buttonText,
              account_status: accountStatus,
            });
            // input_method를 "quick_question"으로 전달
            handleSubmit(
              null,
              q.content,
              q.eventTarget,
              "quick_question",
              q.buttonText,
            );
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
