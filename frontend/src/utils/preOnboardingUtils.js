import { differenceInCalendarDays } from "date-fns";
import {
  NUDGE_PERIODS,
  DAYS_UNTIL_START_BUCKETS,
} from "../constants/preOnboardingConstants";

/**
 * 입사일까지 남은 일수 계산
 * PRE 사용자는 양수(D-7이면 7), ACTIVE 이후는 0 이하
 * @param {string} hireDate - "yyyy-MM-dd" 형식
 * @returns {number} 남은 일수 (양수 = 입사 전)
 */
export function getDaysUntilStart(hireDate) {
  if (!hireDate) return 0;
  const hire = new Date(hireDate);
  const today = new Date();
  return differenceInCalendarDays(hire, today);
}

/**
 * 현재 넛지 구간 판단
 * D-7 ~ D-4 → "d_minus_7"
 * D-3 ~ D-1 → "d_minus_1"
 * 그 외 → null (PRE 기간 아님)
 * @param {string} hireDate - "yyyy-MM-dd" 형식
 * @returns {string|null}
 */
export function getNudgePeriod(hireDate) {
  const daysLeft = getDaysUntilStart(hireDate);
  if (daysLeft >= 4 && daysLeft <= 7) return NUDGE_PERIODS.D_MINUS_7;
  if (daysLeft >= 1 && daysLeft <= 3) return NUDGE_PERIODS.D_MINUS_1;
  return null;
}

/**
 * 로그인 시 days_until_start_bucket 값 결정
 * D-7       → "d_minus_7"
 * D-3 ~ D-6 → "d_minus_3_to_6"
 * D-1 ~ D-2 → "d_minus_1_to_2"
 * 그 외      → null
 * @param {string} hireDate - "yyyy-MM-dd" 형식
 * @returns {string|null}
 */
export function getDaysUntilStartBucket(hireDate) {
  const daysLeft = getDaysUntilStart(hireDate);
  if (daysLeft === 7) return DAYS_UNTIL_START_BUCKETS.D_MINUS_7;
  if (daysLeft >= 3 && daysLeft <= 6)
    return DAYS_UNTIL_START_BUCKETS.D_MINUS_3_TO_6;
  if (daysLeft >= 1 && daysLeft <= 2)
    return DAYS_UNTIL_START_BUCKETS.D_MINUS_1_TO_2;
  return null;
}

/**
 * PRE 기간(D-7 ~ D-1) 내에 있는지 확인
 * @param {string} hireDate - "yyyy-MM-dd" 형식
 * @returns {boolean}
 */
export function isInPrePeriod(hireDate) {
  const daysLeft = getDaysUntilStart(hireDate);
  return daysLeft >= 1 && daysLeft <= 7;
}
