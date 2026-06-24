import { useState, useRef, useEffect } from "react";

const FULL_TEXT_WIDE =
  "안녕하세요! 저는 위드버디예요😊 입사 초에 자주 헷갈리는 회사 생활 질문을 도와드려요.\n사소하지만 꼭 필요한 것부터 편하게 물어보세요.";
const FULL_TEXT_NARROW =
  "안녕하세요! 저는 위드버디예요😊\n입사 초에 자주 헷갈리는 회사 생활 질문을 도와드려요.\n사소하지만 꼭 필요한 것부터 편하게 물어보세요.";

const SUB_COPIES = [
  "메일 설정, 비품 신청, 연차 기준처럼 입사 초 자주 묻는 질문을 바로 확인해보세요.",
  "처음이라 물어보기 애매했던 질문도 괜찮아요. 위드버디에게 먼저 편하게 물어보세요.",
  "회사 문서 속 필요한 정보를 위드버디가 질문에 맞게 찾아드려요.",
];

function useTypingEffect() {
  const [displayedText, setDisplayedText] = useState("");
  const fullText = useRef(
    window.innerWidth >= 1600 ? FULL_TEXT_WIDE : FULL_TEXT_NARROW,
  );
  const chars = useRef([...fullText.current]);
  const isTypingDone = useRef(false);

  // 서브 카피 순환(페이드인/아웃)
  const [subCopyIndex, setSubCopyIndex] = useState(0);
  const [isSubCopyVisible, setIsSubCopyVisible] = useState(true);

  // 타이핑 효과 (최초 1회)
  useEffect(() => {
    setDisplayedText("");
    let i = 0;
    const timer = setInterval(() => {
      setDisplayedText(chars.current.slice(0, i + 1).join(""));
      i++;
      if (i >= chars.current.length) {
        clearInterval(timer);
        isTypingDone.current = true;
      }
    }, 80);
    return () => clearInterval(timer);
  }, []);

  // 타이핑 끝난 후 너비 변경 시 텍스트만 교체
  useEffect(() => {
    const handleResize = () => {
      if (!isTypingDone.current) return;
      const next =
        window.innerWidth >= 1600 ? FULL_TEXT_WIDE : FULL_TEXT_NARROW;
      setDisplayedText(next);
    };
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // 서브 카피 3.5초 간격 순환
  useEffect(() => {
    const interval = setInterval(() => {
      setIsSubCopyVisible(false);
      setTimeout(() => {
        setSubCopyIndex((prev) => (prev + 1) % SUB_COPIES.length);
        setIsSubCopyVisible(true);
      }, 1000);
    }, 3500);
    return () => clearInterval(interval);
  }, []);

  return {
    displayedText,
    currentSubCopy: SUB_COPIES[subCopyIndex],
    isSubCopyVisible,
  };
}

export default useTypingEffect;
