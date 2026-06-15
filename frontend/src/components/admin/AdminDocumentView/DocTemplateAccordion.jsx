import { useState } from "react";
import {
  File,
  ChevronDown,
  ChevronUp,
  Download,
  Users,
  Building,
  Laptop,
  ShieldCheck,
} from "lucide-react";
import axiosInstance from "../../../api/axiosInstance";

// 필수 온보딩 문서 템플릿 목록
const TEMPLATES = [
  {
    category: "인사",
    icon: <Users size={16} />,
    items: [
      { documentId: 64, title: "근로계약 및 수습 기간 안내" },
      { documentId: 63, title: "급여 지급 안내" },
      { documentId: 62, title: "신입사원 온보딩 안내" },
    ],
  },
  {
    category: "행정",
    icon: <Building size={16} />,
    items: [
      { documentId: 58, title: "오피스 이용 및 행정 지원 안내" },
      { documentId: 59, title: "경비·구매·법인카드 처리 안내" },
    ],
  },
  {
    category: "복지",
    icon: <ShieldCheck size={16} />,
    items: [
      { documentId: 61, title: "건강·보험·가족지원 안내" },
      { documentId: 60, title: "복리후생 안내" },
    ],
  },
  {
    category: "IT",
    icon: <Laptop size={16} />,
    items: [
      { documentId: 57, title: "업무 도구 및 계정 안내" },
      { documentId: 56, title: "IT 보안 및 장애대응 안내" },
    ],
  },
];

function DocTemplateAccordion() {
  const [openAccordion, setOpenAccordion] = useState(false);
  const [loadingId, setLoadingId] = useState(null);

  const handleToggle = () => {
    setOpenAccordion(!openAccordion);
  };

  // 다운로드 버튼 클릭 시 URL 발급 → 파일 다운로드
  const handleDownload = async (documentId, title) => {
    setLoadingId(documentId);
    try {
      // 1단계: 다운로드 URL 발급
      const res = await axiosInstance.get(
        `/api/v1/admin/documents/${documentId}/download`,
      );
      const { downloadUrl } = res.data;

      // 2단계: /file은 토큰 없이 일반 fetch로 호출 → blob 받기
      const fileRes = await fetch(
        `${import.meta.env.VITE_API_BASE_URL}${downloadUrl}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("accessToken")}`,
          },
        },
      );
      const blob = await fileRes.blob();

      // 3단계: 브라우저 다운로드 트리거
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;

      // 파일명 지정 추가
      const fileName = title + ".docx";
      link.setAttribute("download", fileName);

      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error("다운로드 실패", error);
    } finally {
      setLoadingId(null);
    }
  };

  return (
    <div className="border border-[#DEE2E6] rounded-[8px]">
      {/* 아코디언 헤더 */}
      <div
        className="flex items-center justify-between px-[20px] py-[14px] bg-[#E6EDF266] rounded-[8px] cursor-pointer"
        onClick={handleToggle}
      >
        <div className="flex items-center gap-[6px]">
          <File size={16} className="text-[#1A3A52]" />
          <span className="md:text-[14px] lg:text-[16px]">
            필수 온보딩 문서 템플릿
          </span>
        </div>
        <div className="flex items-center gap-[10px]">
          <span className="hidden md:block text-[#495057] md:text-[12px] lg:text-[14px]">
            필요한 양식을 내려받아 작성한 뒤 문서로 업로드해보세요
          </span>
          {openAccordion ? (
            <ChevronUp size={18} className="text-[#495057]" />
          ) : (
            <ChevronDown size={18} className="text-[#495057]" />
          )}
        </div>
      </div>

      {/* 아코디언 내용 (열렸을 때만 보임) */}
      {openAccordion && (
        <div className="border-t border-[#DEE2E6] px-[20px] py-[16px] flex flex-col gap-[16px] lg:grid lg:grid-cols-2 lg:gap-[12px]">
          {/* 카테고리별 행 */}
          {TEMPLATES.map((t) => (
            <div
              key={t.category}
              className="flex flex-col gap-[12px] md:flex-row lg:items-center"
            >
              {/* 카테고리 라벨 */}
              <div className="flex items-center gap-[6px] w-fit bg-[#F8F9FA] text-[#868E96] rounded-[4px] px-[6px]">
                <span>{t.icon}</span>
                <span className="text-[12px] md:text-[13px] lg:text-[14px]">
                  {t.category}
                </span>
              </div>

              {/* 문서 목록 (가로 나열) */}
              <div className="flex flex-wrap items-center gap-x-[16px] gap-y-[8px]">
                {t.items.map((item) => (
                  <button
                    key={item.documentId}
                    onClick={() => handleDownload(item.documentId, item.title)}
                    disabled={loadingId === item.documentId}
                    className="flex items-center gap-[4px] py-[2px] text-[13px] disabled:opacity-40 transition-colors group"
                  >
                    <span className="group-hover:underline">
                      {loadingId === item.documentId
                        ? "다운로드 중..."
                        : item.title}
                    </span>
                    <Download
                      size={13}
                      className="text-[#495057] group-hover:text-[#000000]"
                    />
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default DocTemplateAccordion;
