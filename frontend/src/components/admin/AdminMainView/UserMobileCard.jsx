function InfoRow({ label, value }) {
  return (
    <div className="flex">
      <span className="w-[82px] text-[#868E96]">{label}</span>

      <span>{value}</span>
    </div>
  );
}

// 모바일 카드
function UserMobileCard({ user }) {
  return (
    <div className="bg-white border border-[#DEE2E6] rounded-[12px] px-[20px] py-[16px]">
      {/* 이름 + 상태 */}
      <div className="flex items-center justify-between mb-[4px]">
        <span className="text-[16px] font-medium text-[#000000]">
          {user.name}
        </span>
        <span className="px-[12px]">
          {user.role === "ACTIVE" ? (
            <div className="rounded-[16px] py-[4px] px-[12px] bg-[#EBFBEE] text-[#37B24D] text-[12px]">
              <span className="text-[8px] mr-[4px]">●</span>활성화
            </div>
          ) : (
            <div className="rounded-[16px] py-[4px] px-[12px] bg-[#F1F3F5] text-[#868E96] text-[12px]">
              <span className="text-[8px] mr-[4px]">●</span>비활성화
            </div>
          )}
        </span>
      </div>

      {/* 부서(팀) */}
      <p className="text-[14px] text-[#495057] mb-[12px]">{user["부서(팀)"]}</p>

      {/* 상세정보 */}
      <div className="flex flex-col gap-[6px] text-[14px]">
        <InfoRow label="사번" value={user.employeeNumber} />
        <InfoRow label="입사일" value={user.hireDate} />
        <InfoRow label="입사일차" value={`D+${user.hireDay}`} />
        <InfoRow label="마지막 접속" value={user.lastLoginDate ?? "-"} />
        <InfoRow
          label="질문 횟수"
          value={user.questionCount != null ? `${user.questionCount}회` : "-"}
        />
      </div>
    </div>
  );
}

export default UserMobileCard;
