import { Trash2 } from "lucide-react";
import DocRow from "./DocRow";

function DocTable({
  documents,
  isLoading,
  selectedIds,
  handleSelect,
  handleSelectAll,
  onDeleteClick,
}) {
  const allChecked =
    documents.length > 0 && selectedIds.length === documents.length;

  return (
    <div className="flex flex-col">
      {/* 헤더 */}
      <div className="min-w-[700px] grid md:grid-cols-[40px_1fr_120px_100px_100px_100px_40px] lg:grid-cols-[40px_500px_220px_220px_220px_220px_80px] items-center px-[2px] py-[12px] text-[12px] lg:text-[14px] text-[#868E96] text-center border-b border-[#F1F3F5]">
        <input
          type="checkbox"
          checked={allChecked}
          onChange={(e) => handleSelectAll(e.target.checked)}
          className="accent-[#4791CA]"
        />
        <span className="text-left">문서명</span>
        <span>상태</span>
        <span>문서 타입</span>
        <span>담당 부서</span>
        <span>업로드일</span>

        {/* 휴지통 - 선택된 게 있을 때만 활성화 */}
        <button
          onClick={onDeleteClick}
          disabled={selectedIds.length === 0}
          className={`flex items-center justify-center ${
            selectedIds.length > 0
              ? "text-[#E03131] cursor-pointer"
              : "text-transparent cursor-default"
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
          <div>
            <p className="text-center pt-[32px] text-[14px] text-[#495057]">
              아직 등록된 문서가 없어요.
            </p>
            <p className="text-center pt-[6px] text-[12px] text-[#868E96]">
              사내 규정, 복지 안내, 하드웨어 수령 확인서 등 신입 사원을 위한
              안내문을 먼저 등록해 보세요.
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
