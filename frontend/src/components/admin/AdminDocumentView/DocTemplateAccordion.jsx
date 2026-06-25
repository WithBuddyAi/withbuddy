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
  const [errorMessage, setErrorMessage] = useState("");

  const handleToggle = () => {
    setOpenAccordion(!openAccordion);
  };

  // 다운로드 버튼 클릭 시 URL 발급 → 파일 다운로드
  const handleDownload = async (documentId) => {
    setLoadingId(documentId);
    setErrorMessage("");
    try {
      // 1단계: 다운로드 URL 발급
      const res = await axiosInstance.get(
        `/api/v1/admin/documents/${documentId}/download`,
      );
      const { downloadUrl } = res.data;

      // 2단계: 브라우저 네비게이션으로 다운로드 (302 리다이렉트 자동 처리)
      window.open(
        `${import.meta.env.VITE_API_BASE_URL}${downloadUrl}`,
        "_blank",
      );
    } catch (error) {
      setErrorMessage("다운로드에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setLoadingId(null);
    }
  };

  return (
    <div className="border border-[#DEE2E6] rounded-[8px]">
      {/* 아코디언 헤더 */}
      <div
        className="flex items-center justify-between px-[20px] py-[14px] bg-[#E6EDF266] rounded-[8px] cursor-pointer gap-[8px]"
        onClick={handleToggle}
      >
        {/* 왼쪽: 아이콘 + 제목 (+ lg 미만일 때 설명도 아래에) */}
        <div className="flex flex-col gap-[2px]">
          <div className="flex items-center gap-[6px]">
            <File size={16} className="text-[#1A3A52] shrink-0" />
            <span className="text-[12px] md:text-[14px] lg:text-[15px]">
              필수 온보딩 문서 템플릿
            </span>
          </div>
          {/* lg 미만일 때만 설명 텍스트를 제목 아래에 표시 */}
          <span className="block lg:hidden text-[#495057] text-[12px] pl-[22px]">
            필요한 양식을 내려받아 작성한 뒤 문서로 업로드해보세요
          </span>
        </div>

        {/* 오른쪽: lg 이상일 때 설명 + 화살표 / lg 미만일 때 화살표만 */}
        <div className="flex items-center gap-[10px] shrink-0">
          <span className="hidden lg:block text-[#495057] text-[12px] lg:text-[14px]">
            필요한 양식을 내려받아 작성한 뒤 문서로 업로드해보세요
          </span>
          {openAccordion ? (
            <ChevronUp size={18} className="text-[#495057] shrink-0" />
          ) : (
            <ChevronDown size={18} className="text-[#495057] shrink-0" />
          )}
        </div>
      </div>

      {/* 아코디언 내용 (열렸을 때만 보임) */}
      {openAccordion && (
        <div className="border-t border-[#DEE2E6] px-[20px] py-[16px] flex flex-col sm:grid sm:grid-cols-2 gap-[12px]">
          {TEMPLATES.map((t) => (
            <div key={t.category} className="flex items-start gap-[12px]">
              {/* 카테고리 라벨 */}
              <div className="flex items-center gap-[6px] shrink-0 bg-[#F8F9FA] text-[#868E96] rounded-[4px] px-[6px] h-[25px]">
                <span>{t.icon}</span>
                <span className="text-[12px] md:text-[13px] lg:text-[14px]">
                  {t.category}
                </span>
              </div>

              {/* 문서 목록 (세로 나열) */}
              <div className="flex flex-wrap gap-x-[8px] gap-y-[6px]">
                {t.items.map((item) => (
                  <button
                    key={item.documentId}
                    onClick={() => handleDownload(item.documentId, item.title)}
                    disabled={loadingId === item.documentId}
                    className="flex items-center gap-[4px] py-[2px] text-[13px] disabled:opacity-40 transition-colors group text-left"
                  >
                    <span className="group-hover:underline">
                      {loadingId === item.documentId
                        ? "다운로드 중..."
                        : item.title}
                    </span>
                    <Download
                      size={13}
                      className="text-[#495057] shrink-0 group-hover:text-[#000000]"
                    />
                  </button>
                ))}
              </div>
            </div>
          ))}
          {errorMessage && (
            <p className="text-[12px] text-[#F03E3E] sm:col-span-2">
              {errorMessage}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

export default DocTemplateAccordion;
