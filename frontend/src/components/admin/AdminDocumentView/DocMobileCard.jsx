import { FileText } from "lucide-react";

function StatusBadge({ status }) {
  if (status === "COMPLETED") {
    return (
      <div className="rounded-[16px] py-[4px] px-[12px] bg-[#E6FCF5] text-[#0CA678] text-[12px]">
        <span className="text-[8px] mr-[4px]">●</span>등록 완료
      </div>
    );
  }
  return (
    <div className="rounded-[16px] py-[4px] px-[12px] bg-[#FFF5F5] text-[#F03E3E] text-[12px]">
      <span className="text-[8px] mr-[4px]">●</span>등록 실패
    </div>
  );
}

function InfoRow({ label, value }) {
  return (
    <div className="flex">
      <span className="w-[80px] text-[#868E96] shrink-0">{label}</span>
      <span className="text-[#495057]">{value}</span>
    </div>
  );
}

function DocMobileCard({ doc, isSelected, onSelect }) {
  return (
    <div
      className={`bg-white border border-[#DEE2E6] rounded-[12px] px-[20px] py-[16px] ${isSelected ? "bg-[#EAF6FF]" : ""}`}
    >
      {/* 체크박스 + 문서명 + 상태 */}
      <div className="flex items-start justify-between mb-[4px]">
        <div className="flex items-center gap-[8px] min-w-0">
          <input
            type="checkbox"
            checked={isSelected}
            onChange={onSelect}
            className="accent-[#4791CA] shrink-0"
          />
          <div className="flex items-center gap-[6px] min-w-0">
            <FileText size={14} className="shrink-0 text-[#336B97]" />
            <span
              className="text-[14px] font-medium text-[#000000] truncate"
              title={doc.title}
            >
              {doc.title}
            </span>
          </div>
        </div>
      </div>

      {/* 상세 정보 */}
      <div className="flex flex-col gap-[6px] text-[13px] mt-[12px]">
        <InfoRow label="문서 타입" value={doc.documentType} />
        <InfoRow label="문서 형태" value={doc.fileName.split(".").pop()?.toUpperCase()} />
        <InfoRow label="담당 부서" value={doc.department} />
        <InfoRow label="업로드일" value={doc.createdAt?.slice(0, 10)} />
      </div>
    </div>
  );
}

export default DocMobileCard;
