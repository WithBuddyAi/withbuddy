function UserRow({ user }) {
  return (
    <div className="min-w-[900px] grid grid-cols-8 bg-white items-center md:px-0 lg:px-[24px] py-[8px] md:text-[12px] lg:text-[16px] text-[#495057] text-center border-b border-[#F1F3F5]">
      <span className="px-[24px] text-[#000000] font-medium">{user.name}</span>
      <span className="px-[24px]">{user.employeeNumber}</span>
      <span className="px-[6px]">
        {user.department}
        <br />({user.teamName})
      </span>
      <span className="px-[20px]">{user.hireDate}</span>
      <span className="px-[24px]">D+{user.hireDay}</span>
      {/* API 업데이트 되면 맞는 값으로 수정 */}
      <span className="px-[24px]">-</span>
      {/* API 업데이트 되면 맞는 값으로 수정 */}
      <span className="px-[24px]">-</span>
      {/* API 업데이트 되면 맞는 값으로 수정 */}
      <span className="px-[24px]">-</span>
    </div>
  );
}

export default UserRow;
