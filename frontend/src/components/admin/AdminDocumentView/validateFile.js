// 허용 파일 확장자
const ALLOWED_EXTENSIONS = [".pdf", ".docx", ".txt", ".md"];

// 파일 크기 제한
const MAX_DOC_SIZE = 20 * 1024 * 1024; // 20MB

// 파일 형식 및 크기 검사
export const validateFile = (file) => {
  const ext = "." + file.name.split(".").pop().toLowerCase();

  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    return "지원하지 않는 파일 형식이에요.";
  }

  if (file.size > MAX_DOC_SIZE) {
    return "문서 파일은 20MB 이하만 업로드할 수 있어요.";
  }

  return null;
};
