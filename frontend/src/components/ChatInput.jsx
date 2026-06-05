import { Send } from "lucide-react";
import { useState, useRef } from "react";

function ChatInput({ handleSubmit, isLoading, isReadOnly }) {
  const [text, setText] = useState("");
  const textareaRef = useRef(null);

  return (
    <div className="mb-[10px]">
      <form
        className="flex gap-[12px] mx-[10px]"
        onSubmit={(e) => {
          if (isLoading || isReadOnly) return;
          handleSubmit(e, text);
          setText("");
          textareaRef.current?.focus();
        }}
      >
        <div className="flex-1 relative">
          <textarea
            value={text}
            ref={textareaRef}
            onChange={(e) => {
              if (isReadOnly) return;
              setText(e.target.value);
              e.target.style.height = "auto";
              e.target.style.height = e.target.scrollHeight + "px";
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                if (text.length > 500 || isLoading || isReadOnly) return;
                handleSubmit(e, text);
                setText("");
                textareaRef.current?.focus();
              }
            }}
            className={`w-full resize-none border-[1px] rounded-[4px] py-[8px] pl-[16px] pr-[55px] text-[12px] md:text-[14px] lg:text-[16px] min-h-[52px] md:min-h-[40px] lg:min-h-[44px] max-h-[120px]
              focus:outline-none
              ${isReadOnly
                ? "bg-[#F1F3F5] border-[#E9ECEF] text-[#ADB5BD] cursor-not-allowed focus:border-[#E9ECEF]"
                : `bg-[#FFFFFF] focus:border-[#204867] ${text.length > 500 ? "border-[#F03E3E] focus:border-[#F03E3E]" : "border-[#E9ECEF]"}`
              }`}
            style={{ scrollbarWidth: "none" }}
            placeholder={
              isReadOnly
                ? "수습 기간이 종료되어 새로운 질문을 받을 수 없어요. 기존 대화 기록과 답변 출처는 계속 확인할 수 있어요."
                : "사소한 것도 괜찮아요, 버디에게 무엇이든 물어보세요!"
            }
            disabled={isReadOnly}
          />
          {!isReadOnly && (
            <p
              className={`absolute bottom-[20px] md:bottom-[18px] right-[10px] text-[10px] md:text-[12px] ${text.length > 500 ? "text-[#F03E3E]" : "text-[#ADB5BD]"}`}
            >
              {text.length}/500
            </p>
          )}
        </div>
        <button
          className={`flex items-center justify-center border-[1px] rounded-[8px] w-[40px] h-[44px] md:h-[48px]
            ${
              !isReadOnly && text.trim() && !isLoading && text.length <= 500
                ? "bg-[#204867] border-[#204867] text-[#FFFFFF] hover:bg-[#183348]"
                : "bg-[#F1F3F5] border-[#E9ECEF] text-[#ADB5BD]"
            }`}
          disabled={isReadOnly || !text.trim() || isLoading || text.length > 500}
        >
          <Send size={15} className="text-inherit" />
        </button>
      </form>
      {!isReadOnly && text.length > 500 && (
        <p className="text-[#F03E3E] text-[10px] md:text-[12px] mx-[10px]">
          500자 이내로 조금만 다듬어주시겠어요?
        </p>
      )}
    </div>
  );
}

export default ChatInput;