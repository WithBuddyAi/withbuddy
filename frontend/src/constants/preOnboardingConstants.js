 // 넛지 구간 정의
// D-7 ~ D-4: 여유 있게 준비할 것
// D-3 ~ D-1: 내일 바로 필요한 것
export const NUDGE_PERIODS = {
  D_MINUS_7: "d_minus_7", // D-7 ~ D-4
  D_MINUS_1: "d_minus_1", // D-3 ~ D-1
};

// days_until_start_bucket 값
export const DAYS_UNTIL_START_BUCKETS = {
  D_MINUS_7: "d_minus_7", // D-7
  D_MINUS_3_TO_6: "d_minus_3_to_6", // D-3 ~ D-6
  D_MINUS_1_TO_2: "d_minus_1_to_2", // D-1 ~ D-2
};

// PRE 처리 등급
export const PRE_GRADE = {
  ALLOWED: "allowed", // 가능: PRE 상태에서 답변 가능
  LIMITED: "limited", // 제한: 개요 수준만 답변 or 담당자 안내
  EXCLUDED: "excluded", // 제외: 답변하지 않음 → out_of_scope_pre
};
