function UserRow({ user }) {
  const deptTeam = user["부서(팀)"].match(/^(.+)\((.+)\)$/);
  const department = deptTeam ? deptTeam[1] : user["부서(팀)"];
  const teamName = deptTeam ? deptTeam[2] : "";
console.log(user.name, user.accountStatus);
  return (
    <div className="min-w-[900px] grid grid-cols-8 bg-white items-center md:px-0 lg:px-[24px] py-[8px] md:text-[12px] lg:text-[16px] text-[#495057] text-center border-b border-[#F1F3F5]">
      <span className="px-[24px] text-[#000000] font-medium">{user.name}</span>
      <span className="px-[24px]">{user.employeeNumber}</span>
      <span className="px-[6px]">
        {department}
        <br />({teamName})
      </span>
      <span className="px-[12px]">{user.hireDate}</span>
      <span className="px-[24px]">D+{user.hireDay}</span>
      <span className="px-[12px]">{user.lastLoginDate ?? "-"}</span>
      <span className="px-[24px]">
        {user.questionCount != null ? `${user.questionCount}회` : "-"}
      </span>
      <span className="px-[12px]">
        {user.accountStatus === "ACTIVE" ? (
          <div className="rounded-[16px] py-[4px] px-[12px] bg-[#E6FCF5] text-[#0CA678]">
            <span className="text-[8px] mr-[4px]">●</span>활성화
          </div>
        ) : user.accountStatus === "READ_ONLY" ? (
          <div className="rounded-[16px] py-[4px] px-[12px] bg-[#EAF6FF] text-[#336B97]">
            <span className="text-[8px] mr-[4px]">●</span>조회 전용
          </div>
        ) : (
          <div className="rounded-[16px] py-[4px] px-[12px] bg-[#F1F3F5] text-[#868E96]">
            <span className="text-[8px] mr-[4px]">●</span>비활성화
          </div>
        )}
      </span>
    </div>
  );
}

export default UserRow;
