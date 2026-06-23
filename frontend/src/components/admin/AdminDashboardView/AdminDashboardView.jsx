import { useState, useEffect } from "react";
import axiosInstance from "../../../api/axiosInstance";
import AdminDashboardHeader from "./AdminDashboardHeader";
import AdminDashboardCards from "./AdminDashboardCards";
import AdminDashboardQuestions from "./AdminDashboardQuestions";

function AdminDashboardView() {
  const [dashboard, setDashboard] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchDashboard = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const { data } = await axiosInstance.get(
        "/api/v1/admin/metrics/dashboard",
        { params: { unansweredPatternLimit: 5 } },
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
  const aiSummary = dashboard?.unansweredQuestionPatterns?.aiSummary || null;

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
        aiSummary={aiSummary}
        isLoading={isLoading}
        error={error}
        onRetry={fetchDashboard}
      />
    </div>
  );
}

export default AdminDashboardView;
