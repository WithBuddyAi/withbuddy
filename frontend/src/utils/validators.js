// 회사코드 정규식(영문 + 숫자 4 ~ 20자)
export const validateCompanyCode = (value) => {
  if (!value) return "";
  if (!/^[A-Za-z0-9]{4,20}$/.test(value))
    return "영문, 숫자를 조합하여 4 ~ 20자로 입력해 주세요.";
  return "";
};

// 사원번호 정규식(영문 + 숫자 4 ~ 20자)
export const validateEmployeeNumber = (value) => {
  if (!value) return "";
  if (!/^[A-Za-z0-9]{4,20}$/.test(value))
    return "영문, 숫자를 조합하여 4 ~ 20자로 입력해 주세요.";
  return "";
};

// 이름 정규식(한글 or 영문 1 ~ 20자)
export const validateName = (value) => {
  if (!value) return "";
  if (!/^[A-Za-zㄱ-힣]{1,20}$/.test(value))
    return "한글 또는 영문 1 ~ 20자로 입력해 주세요.";
  return "";
};