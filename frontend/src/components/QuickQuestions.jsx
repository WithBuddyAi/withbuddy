import axiosInstance from "../api/axiosInstance"

function QuickQuestions({ quickQuestion, handleSubmit }) {
  return(
    <div className="flex items-center gap-[10px] pb-[10px] whitespace-nowrap my-[16px] mx-[16px] overflow-x-auto">
      <p className="text-[#868E96] text-[11px] md:text-[12px] lg:text-[14px]">빠른 질문</p>
      {quickQuestion.map((q, index) => 
      <button
        key={index}
        type="button"
        onClick={async () => {
          try {
            await axiosInstance.post('/api/v1/chat/quick-questions/click')
            handleSubmit(null, q.content)
          } catch (error) {
            console.error('클릭 로그 기록 실패:', error)
          }
        }}
        className="border-[1px] border-[#DEE2E6] py-[8px] px-[16px] rounded-[9999px] text-[#868E96] text-[11px] md:text-[12px] lg:text-[14px]
        active:bg-[#dbedff] active:border-[#dbedff] active:text-[#336B97]">
          {q.content}
        </button>
        )
      }
    </div>
  )
}

export default QuickQuestions