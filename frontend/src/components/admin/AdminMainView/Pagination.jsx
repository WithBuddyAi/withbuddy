function Pagination({ currentPage, totalPages, setCurrentPage }) {
  if (totalPages === 0) return null;

  const groupSize = 3;
  const currentGroup = Math.floor(currentPage / groupSize);
  const startPage = currentGroup * groupSize;
  const endPage = Math.min(startPage + groupSize, totalPages);
  const hasPrevGroup = currentGroup > 0;
  const hasNextGroup = endPage < totalPages;

  return (
    <div className="flex items-center justify-center gap-[12px] mt-[18px]">
      {/* 이전 버튼 */}
      <button
        onClick={() => setCurrentPage((prev) => prev - 1)}
        disabled={currentPage === 0}
        className="md:w-[26px] md:h-[26px] lg:w-[32px] lg:h-[32px] flex items-center justify-center rounded-[6px] text-[12px] md:text-[14px] lg:text-[16px] text-[#204867] disabled:text-[#ADB5BD] hover:font-semibold disabled:hover:font-normal"
      >
        &lt;
      </button>

      {/* 왼쪽 줄임표 */}
      {hasPrevGroup && (
        <>
          <button
            onClick={() => setCurrentPage(0)}
            className="md:w-[24px] md:h-[24px] lg:w-[30px] lg:h-[30px] flex items-center justify-center rounded-[6px] text-[12px] md:text-[14px] lg:text-[16px] text-[#204867] hover:font-semibold"
          >
            1
          </button>
          <button
            onClick={() => setCurrentPage(startPage - 1)}
            className="md:text-[14px] lg:text-[16px] text-[#ADB5BD] hover:text-[#204867]"
          >
            ...
          </button>
        </>
      )}

      {/* 현재 구간 페이지 번호 */}
      {Array.from({ length: endPage - startPage }, (_, i) => startPage + i).map(
        (page) => (
          <button
            key={page}
            onClick={() => setCurrentPage(page)}
            className={`min-w-[18px] h-[18px] md:min-w-[24px] md:h-[24px] lg:min-w-[30px] lg:h-[30px] px-[4px] flex items-center justify-center rounded-[6px] text-[12px] md:text-[14px] lg:text-[16px] ${
              currentPage === page
                ? "bg-[#204867] text-white"
                : "text-[#204867] hover:font-semibold"
            }`}
          >
            {page + 1}
          </button>
        ),
      )}

      {/* 오른쪽 줄임표 */}
      {hasNextGroup && (
        <>
          <button
            onClick={() => setCurrentPage(endPage)}
            className="md:text-[14px] lg:text-[16px] text-[#ADB5BD] hover:text-[#204867]"
          >
            ...
          </button>
          <button
            onClick={() => setCurrentPage(totalPages - 1)}
            className="md:w-[24px] md:h-[24px] lg:w-[30px] lg:h-[30px] flex items-center justify-center rounded-[6px] text-[12px] md:text-[14px] lg:text-[16px] text-[#204867] hover:font-semibold"
          >
            {totalPages}
          </button>
        </>
      )}

      {/* 다음 버튼 */}
      <button
        onClick={() => setCurrentPage((prev) => prev + 1)}
        disabled={currentPage === totalPages - 1}
        className="md:w-[26px] md:h-[26px] lg:w-[32px] lg:h-[32px] flex items-center justify-center rounded-[6px] text-[12px] md:text-[14px] lg:text-[16px] text-[#204867] disabled:text-[#ADB5BD] hover:font-semibold disabled:hover:font-normal"
      >
        &gt;
      </button>
    </div>
  );
}

export default Pagination;
