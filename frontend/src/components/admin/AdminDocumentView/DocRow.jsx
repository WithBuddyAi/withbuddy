import { FileText } from "lucide-react";

function DocRow({ doc, isSelected, onSelect }) {
  return (
    <div
      className={`min-w-[780px] grid md:grid-cols-[40px_minmax(0,1fr)_100px_100px_100px_80px_100px_40px] items-center px-[2px] py-[12px] text-[12px] lg:text-[14px] text-[#495057] text-center border-b border-[#F1F3F5]
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
        <span className="truncate text-[#000000] font-medium" title={doc.title}>
          {doc.title}
        </span>
      </span>
      <span>
        <span
          className={`rounded-[16px] text-[11px] lg:text-[12px] px-[12px] py-[4px] ${
            doc.preOnboardingTag
              ? "bg-[#EAF6FF] text-[#336B97]"
              : "bg-[#F1F3F5] text-[#495057]"
          }`}
        >
          {doc.preOnboardingTag ? "입사 전 포함" : "입사 전 제외"}
        </span>
      </span>
      <span>{doc.documentType}</span>
      <span>{doc.contentType.toUpperCase()}</span>
      <span>{doc.department}</span>
      <span>{doc.createdAt?.slice(0, 10)}</span>
    </div>
  );
}

export default DocRow;
