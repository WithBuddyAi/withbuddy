import { CloudUpload } from "lucide-react";
import { useRef, useState } from "react";

function DocUploadZone({ onFileSelect }) {
  const [isDragging, setIsDragging] = useState(false);
  const inputRef = useRef(null);

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) onFileSelect(file);
  };

  const handleChange = (e) => {
    const file = e.target.files[0];
    if (file) onFileSelect(file);
  };

  return (
    <div
      onDragOver={(e) => {
        e.preventDefault();
        setIsDragging(true);
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
      onClick={() => inputRef.current?.click()}
      className={`flex flex-col items-center justify-center min-h-[150px] gap-[8px] border-[1px] border-dashed rounded-[16px] py-[32px] cursor-pointer transition-colors
        ${isDragging ? "border-[#4791CA] bg-[#F7FBFF] text-[#336B97" : "border-[#DEE2E6] bg-[#F8F9FA4D] hover:bg-[#F1F3F5]"}`}
    >
      <CloudUpload size={24} className="text-[#ADB5BD]" />

      {isDragging ? (
        <p className="text-[16px] text-[#336B97]">여기에 놓으세요</p>
      ) : (
        <>
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
        </>
      )}

      <input
        ref={inputRef}
        type="file"
        className="hidden"
        accept=".pdf,.docx,.pptx,.txt,.xls,.xlsx,.png,.jpg,.jpeg"
        onChange={handleChange}
      />
    </div>
  );
}

export default DocUploadZone;
