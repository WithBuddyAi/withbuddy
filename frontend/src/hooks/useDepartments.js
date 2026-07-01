import { useState, useEffect } from "react";
import axiosInstance from "../api/axiosInstance";

// 동일한 API 통합
function useDepartments() {
  const [orgOptions, setOrgOptions] = useState([]);

  useEffect(() => {
    const fetchOrgOptions = async () => {
      try {
        const res = await axiosInstance.get(
          "/api/v1/admin/organization-options",
        );
        setOrgOptions(res.data.departments);
      } catch (error) {
        console.error("부서/팀 목록 조회 실패", error);
      }
    };
    fetchOrgOptions();
  }, []);

  return orgOptions;
}

export default useDepartments;
