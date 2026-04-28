function QuickQuestions({ quickQuestion, setText }) {
  return(
    <div className="flex items-center gap-[10px] my-[16px] mx-[16px]">
      <p className="text-[#868E96] text-[14px]">빠른 질문</p>
      {quickQuestion.map((q, index) => 
      <button
        key={index}
        type="button"
        onClick={() => setText(q.content)}
        className="border-[1px] border-[#DEE2E6] py-[8px] px-[16px] rounded-[9999px] text-[#868E96] text-[14px]
        active:bg-[#F8F9FA] active:border-[#E9ECEF]">
          {q.content}
        </button>
        )
      }
    </div>
  )
}

export default QuickQuestions