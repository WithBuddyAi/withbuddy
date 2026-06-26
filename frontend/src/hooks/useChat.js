import { format } from "date-fns";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import axiosInstance from "../api/axiosInstance";

function useChat({
  accountStatus,
  setActiveDates,
  name,
  setModalType,
  setUser,
  setRetryBt,
}) {
  const navigate = useNavigate();
  const today = format(new Date(), "yyyy-MM-dd");

  // 채팅 화면
  const [messageList, setMessageList] = useState([]);
  const [quickQuestion, setQuickQuestion] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState(false);
  const [loadingMessage, setLoadingMessage] = useState("");
  const [hasSubmitted, setHasSubmitted] = useState(false);
  const submitLockRef = useRef(false);

  // 자동 스크롤
  const chatTopRef = useRef(null);
  const chatBottomRef = useRef(null);
  const lastUserMessageRef = useRef(null);
  // 스크롤 방향 추적: "bottom" | "top" | "user-message"
  const scrollTargetRef = useRef("bottom");

  // 첫 렌딩 시 화면
  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      try {
        const [messageResponse, quickResponse] = await Promise.allSettled([
          axiosInstance.get("/api/v1/chat/messages", {}),
          accountStatus !== "READ_ONLY"
            ? axiosInstance.get("/api/v1/chat/quick-questions", {})
            : Promise.resolve(null),
        ]);
        if (messageResponse.status === "fulfilled") {
          const messages = messageResponse.value.data.messages;
          const dates = [
            ...new Set([...messages.map((m) => m.createdAt.slice(0, 10))]),
          ];
          setActiveDates(dates);
          setMessageList(messages);
        } else {
          const error = messageResponse.reason;
          throw error;
        }
        if (
          quickResponse.status === "fulfilled" &&
          quickResponse.value !== null
        ) {
          setQuickQuestion(quickResponse.value.data.quickQuestions);
        }
      } catch (error) {
        if (error.response) {
          const serverMessage = error.response?.data?.errors?.[0]?.message;
          setErrorMessage(
            serverMessage || "에러가 발생했어요. 다시 시도해 주세요.",
          );
        }
      } finally {
        setIsLoading(false);
      }
    };

    const sessionStart = async () => {
      try {
        await axiosInstance.post("/api/v1/chat/session-start", {});
      } catch (error) {
        // 확인용(일부러 넣어둠)
        console.error("session-start 실패:", error);
      }
    };

    const exposure = async () => {
      try {
        await axiosInstance.post("/api/v1/onboarding-suggestions/me/exposure");
      } catch (error) {
        // 확인용(일부러 넣어둠)
        console.error("exposure 실패:", error);
      }
    };

    const init = async () => {
      if (accountStatus !== "READ_ONLY") {
        await sessionStart();
        await exposure();
      }
      fetchData();
    };
    init();
  }, []);

  // 자동 스크롤
  useEffect(() => {
    if (scrollTargetRef.current === "top") {
      chatTopRef.current?.scrollIntoView({ behavior: "auto" });
      scrollTargetRef.current = "bottom";
    } else if (isLoading) {
      chatBottomRef.current?.scrollIntoView({ behavior: "auto" });
    } else if (hasSubmitted) {
      lastUserMessageRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    } else {
      chatBottomRef.current?.scrollIntoView({ behavior: "auto" });
    }
  }, [messageList, isLoading, loadingMessage]);

  // 달력에서 날짜 선택 시 맨 위로 스크롤
  const setMessagesFromCalendar = (messages) => {
    scrollTargetRef.current = "top";
    setMessageList(messages);
  };

  // 에러 토스트 자동 사라짐
  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(false);
      }, 3000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  // 답변 지연 메시지
  useEffect(() => {
    if (isLoading) {
      setLoadingMessage(
        "잠시만요! 우리 사내 문서에서 관련 내용을 꼼꼼히 찾아보고 있어요!",
      );
      const timer1 = setTimeout(() => {
        setLoadingMessage(
          `거의 완성됐어요! ${name}님을 돕기 위해 최선을 다하는 중입니다! 😊`,
        );
      }, 5000);
      return () => {
        clearTimeout(timer1);
      };
    } else {
      setLoadingMessage("");
    }
  }, [isLoading]);

  // 사용자 질문 전송
  const handleSubmit = async (e, submitText, eventTarget) => {
    e?.preventDefault();
    const sendText = submitText?.trim();
    if (!sendText) return;

    // 중복 요청 완전 차단
    if (submitLockRef.current) return;
    submitLockRef.current = true;

    // 클릭 로그 기록
    if (eventTarget) {
      try {
        await axiosInstance.post("/api/v1/chat/quick-questions/click", {
          eventTarget,
        });
      } catch (error) {
        console.error("클릭 로그 기록 실패:", error);
      }
    }

    setIsLoading(true);
    setHasSubmitted(true);

    await new Promise((resolve) => setTimeout(resolve, 0));
    if (!submitLockRef.current) return;

    setMessageList((prev) => [
      ...prev,
      {
        id: `temp-${Date.now()}`,
        senderType: "USER",
        content: sendText,
        createdAt: new Date().toISOString(),
      },
    ]);

    try {
      // SSE fetch — credentials: 'include'로 쿠키 자동 전송
      const baseURL = import.meta.env.VITE_API_BASE_URL;
      const response = await fetch(`${baseURL}/api/v1/chat/messages/stream`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: sendText }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        const message = errorData?.errors?.[0]?.message;
        const code = errorData?.code;

        setIsLoading(false);

        if (response.status === 401) {
          if (code === "SESSION_EXPIRED") {
            setModalType("sessionExpired");
            return;
          }

          if (code === "SESSION_REVOKED") {
            setModalType("duplicateLogin");
            return;
          }

          if (
            code === "TOKEN_MISSING" ||
            code === "INVALID_TOKEN" ||
            code === "USER_NOT_FOUND"
          ) {
            setUser(null);
            navigate("/login");
            return;
          }
        }

        if (response.status === 503) {
          setRetryBt(() => () => handleSubmit(null, sendText));
          setModalType("redis");
          return;
        }

        if (response.status === 504) {
          setMessageList((prev) => [
            ...prev,
            {
              id: `error-${Date.now()}`,
              senderType: "BOT",
              messageType: "ai_timeout",
              content:
                message ||
                "AI 답변 생성 시간이 초과됐어요. 잠시 후 다시 시도해 주세요.",
              createdAt: new Date().toISOString(),
            },
          ]);
        } else {
          setErrorMessage(message || "메시지 전송에 실패했어요.");
        }
        return;
      }

      // SSE 스트리밍
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let delimiterIndex;
        while ((delimiterIndex = buffer.indexOf("\n\n")) !== -1) {
          const rawEvent = buffer.slice(0, delimiterIndex).trim();
          buffer = buffer.slice(delimiterIndex + 2);
          if (!rawEvent) continue;
          const lines = rawEvent.split("\n");
          let eventName = "";
          for (const line of lines) {
            if (line.startsWith("event:")) {
              eventName = line.replace("event:", "").trim();
            }
            if (line.startsWith("data:")) {
              const parsed = JSON.parse(line.replace("data:", "").trim());
              if (eventName === "question_saved") {
                setMessageList((prev) => {
                  const exists = prev.some(
                    (msg) => msg.id === parsed.question.id,
                  );
                  if (exists) {
                    return prev.map((msg) =>
                      msg.id === parsed.question.id ? parsed.question : msg,
                    );
                  }
                  const lastTempIndex = [...prev]
                    .map((msg, i) => ({ msg, i }))
                    .reverse()
                    .find(({ msg }) =>
                      msg.id.toString().startsWith("temp-"),
                    )?.i;
                  if (lastTempIndex === undefined) return prev;
                  return prev.map((msg, i) =>
                    i === lastTempIndex ? parsed.question : msg,
                  );
                });
              } else if (eventName === "answer_delta") {
                setMessageList((prev) => {
                  const last = prev[prev.length - 1];
                  if (
                    last?.senderType === "BOT" &&
                    last?.messageType === "streaming"
                  ) {
                    const next = [...prev];
                    next[next.length - 1] = {
                      ...next[next.length - 1],
                      content: next[next.length - 1].content + parsed.content,
                    };
                    return next;
                  } else {
                    return [
                      ...prev,
                      {
                        id: `streaming-${Date.now()}-${Math.random()}`,
                        senderType: "BOT",
                        messageType: "streaming",
                        content: parsed.content,
                        createdAt: new Date().toISOString(),
                      },
                    ];
                  }
                });
              } else if (eventName === "answer_completed") {
                setMessageList((prev) =>
                  prev.map((msg) =>
                    msg.messageType === "streaming" ? parsed.answer : msg,
                  ),
                );
                setActiveDates((prev) =>
                  prev.includes(today) ? prev : [...prev, today],
                );
                setIsLoading(false);
              } else if (eventName === "error") {
                setMessageList((prev) => [
                  ...prev,
                  {
                    id: `error-${Date.now()}`,
                    senderType: "BOT",
                    messageType: "ai_timeout",
                    content:
                      parsed.message ||
                      "AI 답변 생성 시간이 초과됐어요. 잠시 후 다시 시도해 주세요.",
                    createdAt: new Date().toISOString(),
                  },
                ]);

                setIsLoading(false);
              }
            }
          }
        }
      }
    } catch (error) {
      setIsLoading(false);

      const isNetworkError =
        error.message === "Failed to fetch" || !navigator.onLine;

      setMessageList((prev) => [
        ...prev,
        {
          id: `error-${Date.now()}`,
          senderType: "BOT",
          messageType: "send_error",
          content: isNetworkError
            ? "인터넷 연결을 확인하고 다시 시도해 주세요."
            : "메시지 전송에 실패했어요. 다시 시도해 주세요.",
          createdAt: new Date().toISOString(),
        },
      ]);
    } finally {
      submitLockRef.current = false;
    }
  };

  // 응답 지연 시 재시도
  const handleRetry = (errorMessage) => {
    if (!errorMessage) {
      const lastUserMessage = [...messageList]
        .reverse()
        .find((msg) => msg.senderType === "USER");
      if (lastUserMessage) {
        setMessageList((prev) =>
          prev.filter(
            (msg) =>
              msg.messageType !== "ai_timeout" &&
              msg.messageType !== "send_error" &&
              msg.id !== lastUserMessage.id,
          ),
        );
        handleSubmit(null, lastUserMessage.content);
      }
      return;
    }
    const errorIndex = messageList.findIndex(
      (msg) => msg.id === errorMessage.id,
    );
    const userMessage = [...messageList]
      .slice(0, errorIndex)
      .reverse()
      .find((msg) => msg.senderType === "USER");

    if (userMessage) {
      setMessageList((prev) =>
        prev.filter(
          (msg) => msg.id !== errorMessage.id && msg.id !== userMessage.id,
        ),
      );
      handleSubmit(null, userMessage.content);
    }
  };

  // 파일 다운로드
const handleDownload = async (downloadUrl) => {
  try {
    const { data } = await axiosInstance.get(downloadUrl);

    window.open(
      `${import.meta.env.VITE_API_BASE_URL}${data.downloadUrl}`,
      "_blank",
    );
  } catch (error) {
    if (error.response?.status === 404) {
      setErrorMessage("해당 문서를 찾을 수 없어요.");
    } else {
      setErrorMessage("파일 다운로드에 실패했어요. 다시 시도해 주세요.");
    }
  }
};

  return {
    messageList,
    setMessageList,
    quickQuestion,
    isLoading,
    errorMessage,
    loadingMessage,
    chatTopRef,
    chatBottomRef,
    lastUserMessageRef,
    setMessagesFromCalendar,
    handleSubmit,
    handleRetry,
    handleDownload,
  };
}

export default useChat;
