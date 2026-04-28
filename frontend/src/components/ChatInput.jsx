import { Send } from "lucide-react"

function ChatInput({ handleSubmit, text, setText, isLoading }) {

  return(
    <div>
      <form onSubmit={handleSubmit} className="flex gap-[12px] mb-[10px] mx-[10px]">
        <textarea 
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            handleSubmit(e)
          }
        }}
        disabled={isLoading}
        className="flex-1 border-[1px] border-[#E9ECEF] rounded-[8px] bg-[#FFFFFF] py-[16px] md:py-[8px] px-[16px] text-[12px] md:text-[14px] lg:text-[16px] h-[52px] md:h-[40px] lg:h-[44px]
        focus:border-[#204867] fucus:border-[1px] focus:outline-none resize-none"
        placeholder="사소한 것도 괜찮아요, 버디에게 무엇이든 물어보세요!" />
        <button 
        className="flex items-center justify-center bg-[#F1F3F5] border-[1px] border-[#E9ECEF] rounded-[8px] w-[40px] h-[44px] md:h-[48px] text-[#ADB5BD] active:text-[#FFFFFF] active:bg-[#336B97] active:enabled:bg-[#336B97]"
        disabled={!text.trim() || isLoading}>
          <Send size={15} className="text-inherit" />
        </button>
      </form>
    </div>
  )
}

export default ChatInput