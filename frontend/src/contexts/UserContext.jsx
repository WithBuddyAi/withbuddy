import { createContext, useContext, useState } from "react";

const UserContext = createContext(null)

export function UserProvider ({children}) {
  const [hireDate, setHireDate] = useState()
  const [dayOffset, setDayOffset] = useState()
  const [isLoggedIn, setIsLoggedIn] = useState(!!localStorage.getItem('accessToken'))

  return (
    <UserContext.Provider value = {{hireDate, setHireDate, dayOffset, setDayOffset}}>
      {children}
    </UserContext.Provider>
  )
}

export function useUser() {
  return useContext(UserContext)
}