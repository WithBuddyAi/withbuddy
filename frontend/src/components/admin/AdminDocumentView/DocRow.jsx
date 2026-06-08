import { FileText } from "lucide-react";

function StatusBadge({ status }) {
  if (status === "COMPLETED") {
    return (
      <div className="rounded-[16px] py-[4px] px-[12px] bg-[#E6FCF5] text-[#0CA678] text-[12px]">
        <span className="text-[8px] mr-[4px]">●</span>등록 완료
      </div>
    );
  }
  if (status === "IN_PROGRESS") {
    return (
      <div className="rounded-[16px] py-[4px] px-[12px] bg-[#FFF9DB] text-[#F59F00] text-[12px]">
        <span className="text-[8px] mr-[4px]">●</span>처리중
      </div>
    );
  }
  return (
    <div className="rounded-[16px] py-[4px] px-[12px] bg-[#FFF5F5] text-[#F03E3E] text-[12px]">
      <span className="text-[8px] mr-[4px]">●</span>등록 실패
    </div>
  );
}

function DocRow({ doc, isSelected, onSelect }) {
  return (
    <div
      className={`min-w-[700px] grid md:grid-cols-[40px_1fr_120px_100px_100px_100px_40px] lg:grid-cols-[40px_500px_220px_220px_220px_220px_80px] items-center px-[2px] py-[12px] text-[12px] lg:text-[14px] text-[#495057] text-center border-b border-[#F1F3F5]
  ${isSelected ? "bg-[#F7FBFF]" : "bg-white"}`}
    >
      <input
        type="checkbox"
        checked={isSelected}
        onChange={onSelect}
        className="accent-[#4791CA]"
      />
      <span className="flex items-center gap-[8px] pr-[12px] min-w-0">
        <FileText size={16} className="shrink-0 text-[#336B97]" />
        <span className="truncate text-[#000000] font-medium">{doc.title}</span>
      </span>
      <span className="flex justify-center">
        <StatusBadge status={doc.backupStatus} />
      </span>
      <span>{doc.documentType}</span>
      <span>{doc.department}</span>
      <span>{doc.createdAt?.slice(0, 10)}</span>
    </div>
  );
}

export default DocRow;
