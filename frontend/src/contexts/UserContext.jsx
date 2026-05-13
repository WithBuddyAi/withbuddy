import { createContext, useContext, useState } from "react";

const UserContext = createContext(null);

export function UserProvider({ children }) {
  const [hireDate, setHireDate] = useState();
  const [dayOffset, setDayOffset] = useState();
  const [role, setRole] = useState();

  return (
    <UserContext.Provider
      value={{ hireDate, setHireDate, dayOffset, setDayOffset, role, setRole }}
    >
      {children}
    </UserContext.Provider>
  );
}

export function useUser() {
  return useContext(UserContext);
}
