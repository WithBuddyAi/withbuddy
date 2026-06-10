import { CloudUpload } from "lucide-react";
import { useRef, useState } from "react";
import { validateFile } from "./validateFile";

function DocUploadZone({ onFileSelect }) {
  const [isDragging, setIsDragging] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const inputRef = useRef(null);

  // 드래그앤드롭으로 파일 선택
  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) {
      const error = validateFile(file);
      if (error) {
        setErrorMessage(error);
        return;
      }
      setErrorMessage("");
      onFileSelect(file);
    }
  };

  // 클릭으로 파일 선택
  const handleChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      const error = validateFile(file);
      if (error) {
        setErrorMessage(error);
        return;
      }
      setErrorMessage("");
      onFileSelect(file);
    }
  };

  return (
    <div className="flex flex-col gap-[12px]">
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        className={`flex flex-col items-center justify-center min-h-[150px] gap-[8px] border-[1px] border-dashed rounded-[16px] py-[32px] cursor-pointer transition-colors
        ${isDragging ? "border-[#4791CA] bg-[#F7FBFF] text-[#336B97]" : "border-[#DEE2E6] bg-[#F8F9FA4D] hover:bg-[#F1F3F5]"}`}
      >
        <CloudUpload size={24} className="text-[#ADB5BD]" />

        {/* 드래그 중 문구 변경 */}
        {isDragging ? (
          <p className="text-[16px] text-[#336B97]">여기에 놓으세요</p>
        ) : (
          <div className="flex flex-col items-center gap-[4px]">
            <p className="text-[14px] md:text-[16px]">
              <span className="hidden md:inline">
                여기에 파일을 드래그하거나{" "}
              </span>
              <span className="text-[#4791CA] underline underline-offset-2">
                [파일 선택]
              </span>
              <span className="hidden md:inline">을 클릭하세요</span>
              <span className="md:hidden">을 클릭하세요</span>
            </p>
            <p className="text-[12px] text-[#868E96]">
              문서 파일 | .pdf, .docx, .txt, .md (20MB)
            </p>
          </div>
        )}

        <input
          ref={inputRef}
          type="file"
          className="hidden"
          accept=".pdf,.docx,.txt,.md,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown"
          onChange={handleChange}
        />
      </div>
      {/* 에러 메시지 */}
      {errorMessage && (
        <p className="text-[12px] text-[#F03E3E]">{errorMessage}</p>
      )}
    </div>
  );
}

export default DocUploadZone;
