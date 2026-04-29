import { Send } from "lucide-react"
import { useState } from "react"

function ChatInput({ handleSubmit, isLoading }) {
  const [text, setText] = useState('')

  return(
    <div className="mb-[10px]">
      <form
      className="flex gap-[12px] mx-[10px]"
      onSubmit={(e) => {
        handleSubmit(e, text)
        setText('')
      }}>
        <div className="flex-1 relative">
          <textarea 
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            e.target.style.height = 'auto'
            e.target.style.height = e.target.scrollHeight + 'px'
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              if (text.length > 500) return
              handleSubmit(e, text)
              setText('')
            }
          }}
          disabled={isLoading}
          className={`w-full resize-none border-[1px] rounded-[4px] bg-[#FFFFFF] py-[8px] pl-[16px] pr-[55px] text-[12px] md:text-[14px] lg:text-[16px] min-h-[52px] md:min-h-[40px] lg:min-h-[44px] max-h-[120px]
          focus:border-[#204867] focus:outline-none
          ${text.length > 500 ? 'border-[#F03E3E] focus:border-[#F03E3E]' : 'border-[#E9ECEF]'}`}
          style={{scrollbarWidth: 'none'}}
          placeholder="사소한 것도 괜찮아요, 버디에게 무엇이든 물어보세요!" />
          <p className={`absolute bottom-[20px] md:bottom-[18px] right-[10px] text-[10px] md:text-[12px] ${text.length > 500 ? 'text-[#F03E3E]' : 'text-[#ADB5BD]'}`}>
            {text.length}/500</p>
        </div>
        <button 
        className="flex items-center justify-center bg-[#F1F3F5] border-[1px] border-[#E9ECEF] rounded-[8px] w-[40px] h-[44px] md:h-[48px] text-[#ADB5BD] active:text-[#FFFFFF] active:bg-[#336B97] active:enabled:bg-[#336B97]"
        disabled={!text.trim() || isLoading || text.length > 500}>
          <Send size={15} className="text-inherit" />
        </button>
      </form>
      {text.length > 500 && (
      <p className="text-[#F03E3E] text-[10px] md:text-[12px] mx-[10px]">
      500자 이내로 조금만 다듬어주시겠어요?
      </p>
      )}
    </div>
  )
}

export default ChatInput