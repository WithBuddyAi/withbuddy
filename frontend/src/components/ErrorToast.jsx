function ErrorToast({ errorMessage }) {
  return(
    <div>
      {errorMessage && (
        <div className="fixed bottom-[40px] left-1/2 -translate-x-1/2 z-50
          bg-[#343A40] text-white text-[14px]
          py-[12px] px-[24px] rounded-[9999px] drop-shadow-lg
          whitespace-nowrap">
          ⚠️ {errorMessage}
        </div>
      )}
    </div>
  )
}

export default ErrorToast