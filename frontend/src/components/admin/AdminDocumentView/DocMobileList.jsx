import DocMobileCard from "./DocMobileCard";
import { Trash2 } from "lucide-react";

function DocMobileList({
  documents,
  isLoading,
  selectedIds,
  handleSelect,
  handleSelectAll,
  search,
  selectedType,
  onDeleteClick,
}) {
  const getEmptyMessage = () => {
    if (search && selectedType) {
      return {
        main: "조건에 맞는 문서가 없어요.",
        sub: "검색어나 문서 유형을 변경해보세요.",
      };
    }
    if (search) {
      return {
        main: `검색어와 일치하는 문서가 없어요.`,
        sub: "다른 검색어로 다시 시도해보세요.",
      };
    }
    if (selectedType) {
      return {
        main: `등록된 ${selectedType} 문서가 없어요.`,
        sub: "다른 유형을 선택하거나 문서를 업로드해보세요.",
      };
    }
    return {
      main: "아직 등록된 문서가 없어요.",
      sub: "온보딩 문서를 업로드하면 신입 질문에 AI가 답변할 수 있어요.",
    };
  };

  const emptyMessage = getEmptyMessage();

  // 전체 선택
  const allChecked =
    documents.length > 0 && selectedIds.length === documents.length;

  return (
    <div className="md:hidden flex flex-col gap-[12px]">
      {/* 전체 선택 + 휴지통 */}
      {!isLoading && documents.length > 0 && (
        <div className="flex items-center justify-between px-[4px]">
          <div className="flex items-center gap-[8px]">
            <input
              type="checkbox"
              checked={allChecked}
              onChange={(e) => handleSelectAll(e.target.checked)}
              className="accent-[#4791CA]"
            />
            <span className="text-[14px] text-[#868E96]">전체 선택</span>
          </div>
          <button
            onClick={onDeleteClick}
            disabled={selectedIds.length === 0}
            className={`${
              selectedIds.length > 0 ? "text-[#E03131]" : "text-transparent"
            }`}
          >
            <Trash2 size={16} />
          </button>
        </div>
      )}

      {isLoading ? (
        <p className="text-center pt-[32px] text-[14px] text-[#495057]">
          불러오는 중...
        </p>
      ) : documents.length === 0 ? (
        <div>
          <p className="text-center pt-[32px] text-[14px] text-[#495057]">
            {emptyMessage.main}
          </p>
          <p className="text-center pt-[6px] text-[12px] text-[#868E96]">
            {emptyMessage.sub}
          </p>
        </div>
      ) : (
        documents.map((doc) => (
          <DocMobileCard
            key={doc.documentId}
            doc={doc}
            isSelected={selectedIds.includes(doc.documentId)}
            onSelect={() => handleSelect(doc.documentId)}
          />
        ))
      )}
    </div>
  );
}

export default DocMobileList;
