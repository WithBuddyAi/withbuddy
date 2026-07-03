// 응답 시간 버킷 계산
// 질문 전송 ~ 응답 수신 시간 차이를 버킷으로 변환
export function getResponseTimeBucket(startTime) {
  const elapsed = Date.now() - startTime;
  if (elapsed < 2000) return "fast";
  if (elapsed < 5000) return "medium";
  return "slow";
}

// 파일 확장자 추출
export function getFileExtension(fileName) {
  if (!fileName) return "unknown";
  const ext = fileName.split(".").pop()?.toLowerCase();
  return ext || "unknown";
}
