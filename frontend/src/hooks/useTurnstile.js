import { useState, useRef, useEffect } from "react";

// Cloudflare Turnstile site key
const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY;

function useTurnstile(isDesktop) {
  const [turnstileToken, setTurnstileToken] = useState("");
  // 위젯이 렌더링될 DOM 위치
  const turnstileContainerRef = useRef(null);
  // turnstile.render()가 반환하는 위젯 id (reset/remove용)
  const turnstileWidgetIdRef = useRef(null);

  useEffect(() => {
    let pollTimer;
    let cancelled = false;

    const renderWidget = () => {
      if (cancelled || !turnstileContainerRef.current) return;

      // 스크립트가 아직 로드되지 않았으면 100ms마다 재시도
      if (!window.turnstile) {
        pollTimer = setTimeout(renderWidget, 100);
        return;
      }

      // 이전 위젯이 남아있으면 정리 (중복 렌더링 방지)
      if (turnstileWidgetIdRef.current) {
        window.turnstile.remove(turnstileWidgetIdRef.current);
      }

      turnstileWidgetIdRef.current = window.turnstile.render(
        turnstileContainerRef.current,
        {
          sitekey: TURNSTILE_SITE_KEY,
          theme: "light", // 테마 지정
          size: "flexible",
          callback: (token) => setTurnstileToken(token),
          "expired-callback": () => setTurnstileToken(""),
          "error-callback": () => setTurnstileToken(""),
        },
      );
    };

    renderWidget();

    return () => {
      cancelled = true;
      clearTimeout(pollTimer);
      if (turnstileWidgetIdRef.current && window.turnstile) {
        window.turnstile.remove(turnstileWidgetIdRef.current);
        turnstileWidgetIdRef.current = null;
      }
    };
  }, [isDesktop]);

  // 1회용으로 실패 시 위젯 리셋 후 새 토큰을 받아야 함
  const resetTurnstile = () => {
    setTurnstileToken("");
    if (window.turnstile && turnstileWidgetIdRef.current) {
      window.turnstile.reset(turnstileWidgetIdRef.current);
    }
  };
  return {
    turnstileToken,
    turnstileContainerRef,
    resetTurnstile,
  };
}

export default useTurnstile;
