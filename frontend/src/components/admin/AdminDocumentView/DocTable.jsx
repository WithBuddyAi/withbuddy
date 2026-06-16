import { Trash2 } from "lucide-react";
import DocRow from "./DocRow";

function DocTable({
  documents,
  isLoading,
  selectedIds,
  handleSelect,
  handleSelectAll,
  onDeleteClick,
  search,
  selectedType,
}) {
  const allChecked =
    documents.length > 0 && selectedIds.length === documents.length;

  // 빈 상태 메시지 결정
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

  return (
    <div className="flex flex-col">
      {/* 헤더 */}
      <div className="min-w-[700px] grid md:grid-cols-[40px_minmax(0,1fr)_100px_100px_100px_100px_40px] items-center px-[2px] py-[12px] text-[12px] lg:text-[14px] text-[#868E96] text-center border-b border-[#F1F3F5]">
        <input
          type="checkbox"
          checked={allChecked}
          onChange={(e) => handleSelectAll(e.target.checked)}
          className="accent-[#4791CA]"
        />
        <span className="text-left">문서명</span>
        <span>문서 타입</span>
        <span>문서 형태</span>
        <span>담당 부서</span>
        <span>업로드일</span>

        {/* 휴지통 - 선택된 게 있을 때만 활성화 */}
        <button
          onClick={onDeleteClick}
          disabled={selectedIds.length === 0}
          className={`flex items-center justify-center ${
            selectedIds.length > 0
              ? "text-[#E03131] cursor-pointer"
              : "text-[#c9c9c9] cursor-default"
          }`}
        >
          <Trash2 size={16} />
        </button>
      </div>

      {/* 바디 */}
      <div className="min-w-[700px]">
        {isLoading ? (
          <p className="text-center pt-[32px] text-[14px] text-[#495057]">
            불러오는 중...
          </p>
        ) : documents.length === 0 ? (
          <div className="text-center pt-[32px] text-[14px] text-[#495057]">
            <p>
              {emptyMessage.main}
              <br/>
              {emptyMessage.sub}
            </p>
          </div>
        ) : (
          documents.map((doc) => (
            <DocRow
              key={doc.documentId}
              doc={doc}
              isSelected={selectedIds.includes(doc.documentId)}
              onSelect={() => handleSelect(doc.documentId)}
            />
          ))
        )}
      </div>
    </div>
  );
}

export default DocTable;
