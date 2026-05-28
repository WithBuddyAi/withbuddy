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
        {/* API 업데이트 되면 맞는 값으로 수정 */}
        <span className="text-[12px] text-[#868E96]">-</span>
      </div>

      {/* 부서(팀) */}
      <p className="text-[14px] text-[#495057] mb-[12px]">
        {user.department}({user.teamName})
      </p>

      {/* 상세정보 */}
      <div className="flex flex-col gap-[6px] text-[14px]">
        <InfoRow label="사번" value={user.employeeNumber} />
        <InfoRow label="입사일" value={user.hireDate} />
        <InfoRow label="입사일차" value={`D+${user.hireDay}`} />
        {/* API 업데이트 되면 맞는 값으로 수정 */}
        <InfoRow label="마지막 접속" value="-" />
        {/* API 업데이트 되면 맞는 값으로 수정 */}
        <InfoRow label="질문 횟수" value="-" />
      </div>
    </div>
  );
}

export default UserMobileCard;
