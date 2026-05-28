import LoadingState from "./LoadingState";
import EmptyState from "./EmptyState";
import UserMobileCard from "./UserMobileCard";

function UserMobileList({ users, isLoading }) {
  return (
    <div className="md:hidden flex flex-col gap-[12px]">
      {isLoading ? (
        // 불러오는 중
        <LoadingState />
      ) : users.length === 0 ? (
        // 계정이 없으면
        <EmptyState />
      ) : (
        users.map((user) => <UserMobileCard key={user.id} user={user} />)
      )}
    </div>
  );
}

export default UserMobileList;
