import { useState, useEffect, useRef } from "react";
import axiosInstance from "../../../api/axiosInstance";
import AdminDocHeader from "./AdminDocHeader";
import DocTable from "./DocTable";
import DocDeleteModal from "./DocDeleteModal";
import Pagination from "../AdminMainView/Pagination";
import { Search, ChevronDown } from "lucide-react";

const DOC_TYPES = ["전체", "LEGAL", "GUIDE", "TEMPLATE"];

function AdminDocumentView({ onDeleteModalOpen }) {
  const [documents, setDocuments] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // 검색 & 필터
  const [search, setSearch] = useState("");
  const [selectedType, setSelectedType] = useState("");
  const [showTypeFilter, setShowTypeFilter] = useState(false);
  const typeFilterRef = useRef(null);

  // 체크박스 선택 상태
  const [selectedIds, setSelectedIds] = useState([]);

  // 삭제 모달
  const [isDeleteModal, setIsDeleteModal] = useState(false);

  const fetchDocuments = async () => {
    setIsLoading(true);
    try {
      const params = { page: currentPage, size: 5 };
      if (search) params.search = search;
      if (selectedType) params.documentType = selectedType;

      const res = await axiosInstance.get("/api/v1/admin/documents", {
        params,
      });
      setDocuments(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotalElements(res.data.totalElements);
    } catch (error) {
      console.error("문서 목록 조회 실패", error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchDocuments();
  }, [currentPage, selectedType]);

  // 검색 엔터
  const handleSearchKeyDown = (e) => {
    if (e.key === "Enter") {
      setCurrentPage(0);
      fetchDocuments();
    }
  };

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (typeFilterRef.current && !typeFilterRef.current.contains(e.target)) {
        setShowTypeFilter(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // 전체 선택
  const handleSelectAll = (checked) => {
    if (checked) {
      setSelectedIds(documents.map((doc) => doc.documentId));
    } else {
      setSelectedIds([]);
    }
  };

  // 개별 선택
  const handleSelect = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id],
    );
  };

  // 삭제 완료 후 처리
  const handleDeleteSuccess = () => {
    setSelectedIds([]);
    setIsDeleteModal(false);
    fetchDocuments();
  };

  return (
    <div className="flex flex-col h-full">
      <AdminDocHeader />

      {/* 총 문서 수 + 검색 + 필터 */}
      <div className="flex items-center justify-between mb-[12px]">
        <div className="flex items-center gap-[16px]">
          <p className="text-[16px] font-medium">등록한 문서 목록</p>
          <p className="rounded-[9999px] px-[12px] py-[4px] bg-[#F8F9FA] text-[12px] text-[#868E96]">
            총 {totalElements}개
          </p>
        </div>
        <div className="flex items-center gap-[8px]">
          {/* 검색 */}
          <div className="flex items-center gap-[8px] border border-[#868E96] rounded-[8px] px-[12px] py-[8px] text-[12px] md:text-[14px] text-[#868E96]">
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={handleSearchKeyDown}
              placeholder="문서 검색"
              className="outline-none text-[#495057] w-[120px] md:w-[160px]"
            />
            <button
              onClick={() => {
                setCurrentPage(0);
                fetchDocuments();
              }}
            >
              <Search
                size={14}
                className="text-[#868E96] hover:text-[#204867]"
              />
            </button>
          </div>

          {/* 문서 타입 필터 */}
          <div ref={typeFilterRef} className="relative">
            <button
              onClick={() => setShowTypeFilter((prev) => !prev)}
              className="flex items-center justify-between gap-[6px] border border-[#868E96] rounded-[8px] w-[132px] px-[12px] py-[8px] text-[12px] md:text-[14px] text-[#868E96]"
            >
              {selectedType || "문서 타입"}
              <ChevronDown size={14} />
            </button>
            {showTypeFilter && (
              <ul className="absolute top-full right-0 mt-[4px] bg-white border border-[#CED4DA] rounded-[8px] shadow-md z-10 min-w-[120px]">
                {DOC_TYPES.map((type) => (
                  <li
                    key={type}
                    onMouseDown={() => {
                      setSelectedType(type === "전체" ? "" : type);
                      setCurrentPage(0);
                      setShowTypeFilter(false);
                    }}
                    className={`px-[12px] py-[10px] text-[14px] cursor-pointer ${
                      (type === "전체" && selectedType === "") ||
                      selectedType === type
                        ? "bg-[#EAF6FF] text-[#204867]"
                        : "hover:bg-[#F1F3F5]"
                    }`}
                  >
                    {type}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>

      {/* 문서 테이블 */}
      <div className="flex-1 overflow-auto">
        <DocTable
          documents={documents}
          isLoading={isLoading}
          selectedIds={selectedIds}
          handleSelect={handleSelect}
          handleSelectAll={handleSelectAll}
          onDeleteClick={() => onDeleteModalOpen(selectedIds, documents)}
        />
      </div>

      {/* 페이지네이션 */}
      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        setCurrentPage={setCurrentPage}
      />

      {/* 삭제 모달 */}
      {isDeleteModal && (
        <DocDeleteModal
          selectedIds={selectedIds}
          documents={documents}
          onClose={() => setIsDeleteModal(false)}
          onSuccess={handleDeleteSuccess}
        />
      )}
    </div>
  );
}

export default AdminDocumentView;
