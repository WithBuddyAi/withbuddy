import { useState, useEffect } from "react";
import axios from "axios";
import AdminDashboardHeader from "./AdminDashboardHeader";
import AdminDashboardCards from "./AdminDashboardCards";
import AdminDashboardQuestions from "./AdminDashboardQuestions";

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

function AdminDashboardView() {
  const [dashboard, setDashboard] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchDashboard = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const token = localStorage.getItem("accessToken");
      const { data } = await axios.get(
        `${BASE_URL}/api/v1/admin/metrics/dashboard`,
        {
          params: { unansweredPatternLimit: 5 },
          headers: { Authorization: `Bearer ${token}` },
        },
      );
      setDashboard(data);
    } catch (err) {
      setError(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboard();
  }, []);

  // 회사 데이터 추출 (ADMIN은 본인 회사 1개만 반환됨)
  const company = {
    rag: dashboard?.ragExperienceRate?.companies?.[0] || null,
    gap: dashboard?.documentGapRate?.companies?.[0] || null,
    unstarted: dashboard?.unstartedUsers?.companies?.[0] || null,
  };
  const patterns = dashboard?.unansweredQuestionPatterns?.patterns || [];

  return (
    <div className="flex flex-col gap-[12px] min-h-0 flex-1 overflow-auto pr-[20px]">
      <AdminDashboardHeader />
      <AdminDashboardCards
        company={company}
        isLoading={isLoading}
        error={error}
        onRetry={fetchDashboard}
      />
      <AdminDashboardQuestions
        patterns={patterns}
        isLoading={isLoading}
        error={error}
        onRetry={fetchDashboard}
      />
    </div>
  );
}

export default AdminDashboardView;
