import { useState } from "react";
import { format } from "date-fns";
import axiosInstance from "../api/axiosInstance";

function useSidebar({ isLoading, setRetryBt, setModalType }) {
  // const today = format(new Date(), "yyyy-MM-dd");
  const [selectedDate, setSelectedDate] = useState(null);
  const [isSidebarOpen, setIsSidebarOpen] = useState(window.innerWidth >= 768);
  const [activeDates, setActiveDates] = useState([]);

  // 대화 기록 달력
  const handleDateChange = async (date) => {
    if (isLoading) return;
    setSelectedDate(date);
    const formattedDate = format(date, "yyyy-MM-dd");
    try {
      const { data: message } = await axiosInstance.get(
        `/api/v1/chat/messages?date=${formattedDate}`,
      );
      return message.messages;
    } catch (error) {
      if (error.response?.status === 503) {
        error.handled = true;
        setRetryBt(() => () => handleDateChange(date));
        setModalType("redis");
        return;
      }
    }
  };

  return {
    selectedDate,
    isSidebarOpen,
    setIsSidebarOpen,
    activeDates,
    setActiveDates,
    handleDateChange,
  };
}

export default useSidebar;
