import { File, ChevronDown } from "lucide-react";
import { useState, useEffect, useRef } from "react";
import axiosInstance from "../../../api/axiosInstance";
import { validateFile } from "./validateFile";

const DOC_TYPES = ["POLICY", "GUIDE", "TEMPLATE"];

function DocUploadForm({ file, onCancel, onSuccess, onError }) {
  const [title, setTitle] = useState(
    file?.name?.replace(/\.[^/.]+$/, "") || "",
  );
  const [documentType, setDocumentType] = useState("");
  const [department, setDepartment] = useState("");
  const [orgOptions, setOrgOptions] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  // 파일 크기 표시
  const formatFileSize = (bytes) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  // 담당 부서 드롭다운
  const [showDeptFilter, setShowDeptFilter] = useState(false);
  const deptFilterRef = useRef(null);

  // 담당 부서 옵션 조회
  useEffect(() => {
    const fetchOrgOptions = async () => {
      try {
        const res = await axiosInstance.get(
          "/api/v1/admin/organization-options",
        );
        setOrgOptions(res.data.departments);
      } catch (error) {
        console.error("부서 목록 조회 실패", error);
      }
    };
    fetchOrgOptions();
  }, []);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (deptFilterRef.current && !deptFilterRef.current.contains(e.target)) {
        setShowDeptFilter(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // 모든 필드가 입력됐을 때만 업로드 버튼 활성화
  const isValid = title.trim() && documentType && department;

  // 문서 업로드 API 호출
  const handleUpload = async () => {
    if (!isValid) return;
    setIsLoading(true);
    setErrorMessage("");
    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("title", title.trim());
      formData.append("documentType", documentType);
      formData.append("department", department);

      await axiosInstance.post("/api/v1/admin/documents/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      onSuccess();
    } catch (error) {
      const code = error.response?.data?.code;
      if (code === "FILE_001") {
        setErrorMessage("파일 크기 또는 문서 수 제한을 초과했어요.");
      } else if (code === "FILE_002") {
        setErrorMessage("지원하지 않는 파일 형식이에요.");
      } else {
        onError?.(); // 그 외 에러는 토스트로
      }
    } finally {
      setIsLoading(false);
    }
  };

  // 다시 업로드 버튼 클릭 시 파일 선택 창 열기 + 파일 검사
  const handleReUpload = (e) => {
    const newFile = e.target.files[0];
    if (newFile) {
      const error = validateFile(newFile);
      if (error) {
        setErrorMessage(error);
        return;
      }
      onCancel(newFile);
    }
  };

  // 파일이 바뀌면 제목/문서 타입/담당 부서/에러 메시지 초기화
  useEffect(() => {
    setTitle(file?.name?.replace(/\.[^/.]+$/, "") || "");
    setDocumentType("");
    setDepartment("");
    setErrorMessage("");
  }, [file]);

  return (
    <div className="flex flex-col">
      {/* 파일명 + 크기 + 다시 업로드 */}
      <div className="flex flex-col md:flex-row md:items-center justify-between border border-[#DEE2E6] border-b-0 rounded-t-[12px] bg-[#E6EDF266] px-[20px] py-[12px] gap-[8px]">
        <div className="flex items-center gap-[8px] text-[12px] md:text-[14px] min-w-0">
          <File size={16} className="text-[#1A3A52] shrink-0" />
          <span className="truncate" title={file.name}>
            {file.name}
          </span>
        </div>
        <div className="flex items-center justify-between gap-[24px] shrink-0">
          <span className="text-[11px] md:text-[12px] text-[#495057]">
            {formatFileSize(file.size)}
          </span>
          {/* 업로드 중에는 다시 업로드 비활성화 */}
          <label
            className={`text-[12px] md:text-[14px] underline underline-offset-2 cursor-pointer
            ${isLoading ? "text-[#ADB5BD] pointer-events-none" : "text-[#204867]"}`}
          >
            다시 업로드
            <input
              type="file"
              className="hidden"
              accept=".pdf,.docx,.txt,.md,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown"
              onChange={handleReUpload}
              disabled={isLoading}
            />
          </label>
        </div>
      </div>

      {/* 업로드 중일 때 로딩 화면으로 교체 */}
      {isLoading ? (
        <div className="border border-t-0 border-[#DEE2E6] rounded-b-[12px] p-[20px] flex flex-col items-center justify-center gap-[16px] py-[48px]">
          <div className="w-[30px] h-[30px] md:w-[40px] md:h-[40px] border-[5px] border-[#DEE2E6] border-t-[#4791CA] rounded-full animate-spin" />
          <p className="text-[12px] md:text-[14px] text-[#495057]">
            문서를 업로드하는 중이에요.
          </p>
        </div>
      ) : (
        <div className="border border-t-0 border-[#DEE2E6] rounded-b-[12px] p-[20px] flex flex-col gap-[20px]">
          {/* 제목 */}
          <div className="flex flex-col gap-[8px]">
            <label className="text-[14px] md:text-[16px] font-semibold">
              제목
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="border border-[#CED4DA] rounded-[8px] px-[12px] py-[9px] md:py-[13px] text-[12px] md:text-[14px] focus:outline-none focus:border-[#4791CA]"
            />
          </div>

          {/* 문서 타입 + 담당 부서 */}
          <div className="flex flex-col md:flex-row gap-[24px]">
            {/* 문서 타입 */}
            <div className="flex flex-col gap-[8px] flex-1">
              <label className="text-[14px] md:text-[16px] font-semibold">
                문서 타입
              </label>
              <div className="flex flex-wrap gap-[8px]">
                {DOC_TYPES.map((type) => (
                  <button
                    key={type}
                    onClick={() => setDocumentType(type)}
                    className={`md:w-[112px] lg:w-[136px] px-[8px] py-[5px] md:py-[10px] lg:px-[9px] lg:py-[11px] rounded-[8px] border text-[12px] md:text-[14px] lg:text-[16px] transition-colors
                      ${
                        documentType === type
                          ? "bg-[#F7FBFF] border-[#4791CA] text-[#336B97]"
                          : "border-[#DEE2E6] text-[#868E96] hover:bg-[#F1F3F5] hover:border-[#CED4DA] hover:text-[#495057]"
                      }`}
                  >
                    {type}
                  </button>
                ))}
              </div>
            </div>

            {/* 담당 부서 */}
            <div className="flex flex-col gap-[8px] flex-1">
              <label className="text-[14px] md:text-[16px] font-semibold">
                담당 부서
              </label>
              <div ref={deptFilterRef} className="relative">
                <button
                  onClick={() => setShowDeptFilter((prev) => !prev)}
                  className="flex items-center justify-between gap-[6px] border border-[#CED4DA] rounded-[8px] w-full px-[12px] py-[9px] md:py-[13px] text-[12px] md:text-[14px] text-[#868E96]"
                >
                  {department || "부서를 선택해주세요"}
                  <ChevronDown size={16} />
                </button>
                {showDeptFilter && (
                  <ul className="absolute top-full left-0 mt-[4px] bg-white border border-[#CED4DA] rounded-[8px] shadow-md z-10 w-full">
                    {orgOptions.map((d) => (
                      <li
                        key={d.department}
                        onMouseDown={() => {
                          setDepartment(d.department);
                          setShowDeptFilter(false);
                        }}
                        className={`px-[12px] py-[10px] text-[14px] cursor-pointer ${
                          department === d.department
                            ? "bg-[#EAF6FF] text-[#204867]"
                            : "hover:bg-[#F1F3F5]"
                        }`}
                      >
                        {d.department}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </div>

          {/* 취소 / 업로드 버튼 */}
          <div className="flex justify-end gap-[8px]">
            <button
              onClick={() => onCancel()}
              className="w-[120px] px-[20px] py-[8px] rounded-[8px] border-[1px] border-[#CED4DA] text-[#868E96] text-[14px] md:text-[16px] hover:border-[#CED4DA]"
            >
              취소
            </button>
            <button
              onClick={handleUpload}
              disabled={!isValid || isLoading}
              className={`w-[120px] px-[20px] py-[8px] rounded-[8px] border-[1px] text-[14px] md:text-[16px] transition-colors
                ${
                  isValid && !isLoading
                    ? "bg-[#4791CA] text-white hover:bg-[#336B97]"
                    : "bg-[#F1F3F5] text-[#868E96] cursor-not-allowed"
                }`}
            >
              업로드
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default DocUploadForm;
