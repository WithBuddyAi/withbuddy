import char from "../assets/Favicon_web.svg";
import withbuddy from "../assets/WithBuddy_web.svg";
import SessionModal from "../components/SessionModal";
import LoginBackground from "../components/login/LoginBackground";
import HeroSection from "../components/login/HeroSection";
import LoginForm from "../components/login/LoginForm";
import useDesktop from "../hooks/useDesktop";
import useLoginForm from "../hooks/useLoginForm";
import useTurnstile from "../hooks/useTurnstile";
import useTypingEffect from "../hooks/useTypingEffect";

function Login({ setUser }) {
  // Hooks
  const isDesktop = useDesktop(1024);
  const { turnstileToken, turnstileContainerRef, resetTurnstile } =
    useTurnstile(isDesktop);
  const form = useLoginForm({ setUser, turnstileToken, resetTurnstile });
  const typing = useTypingEffect();

  return (
    <div className="min-h-screen bg-[#FFFFFF] flex flex-col">
      {/* 세션 모달 (redis 에러) */}
      <SessionModal
        modalType={form.modalType}
        setModalType={form.setModalType}
        handleRetry={form.handleLogin}
      />

      {/* 데스크탑 배경 */}
      <LoginBackground />

      <div
        className="flex-1 flex flex-col items-center justify-center lg:flex-row lg:items-center relative z-10"
        inert={form.modalType !== null ? true : undefined}
      >
        {/* 좌측 소개 섹션 (데스크탑) */}
        <HeroSection
          displayedText={typing.displayedText}
          currentSubCopy={typing.currentSubCopy}
          isSubCopyVisible={typing.isSubCopyVisible}
        />

        {/* 모바일/태블릿: 로고 + 타이틀 */}
        <div className="flex flex-col items-center lg:hidden mt-[56px] md:mt-[60px]">
          <img
            src={char}
            alt="위드버디 캐릭터 이미지"
            className="w-[56px] md:w-[72px]"
          />
          <img
            src={withbuddy}
            alt="WithBuddy 로고"
            className="w-[96px] md:w-[127px] mt-[8px] md:mt-[12px]"
          />
          <p className="text-[10px] md:text-[14px] text-[#6A7282] mt-[8px] mb-[28px] md:mb-[36px]">
            회사 생활 길잡이, 위드버디
          </p>
        </div>

        {/* 모바일/태블릿: 폼 */}
        {!isDesktop && (
          <LoginForm
            {...form}
            turnstileContainerRef={turnstileContainerRef}
            isDesktop={false}
          />
        )}

        {/* 데스크탑: 우측 폼 카드 */}
        {isDesktop && (
          <div
            className="flex items-center justify-center shrink-0 w-1/2"
            style={{
              padding: "40px clamp(16px, 2.5vw, 48px)",
            }}
          >
            <div
              className="bg-[#FFFFFFCC] border border-[#33679B4D] rounded-[28px] shadow-[0px_4px_8px_0px_#00000029] flex flex-col gap-[32px]"
              style={{
                width: "clamp(380px, 90%, 526px)",
                padding: "clamp(24px, 3.5vw, 48px)",
              }}
            >
              {/* 로고 */}
              <div className="flex items-center gap-[12px]">
                <img
                  className="w-[29px] h-[29px]"
                  src={char}
                  alt="위드버디 캐릭터"
                />
                <span
                  className="text-[#204867] font-extrabold leading-normal"
                  style={{ fontSize: "clamp(22px, 2.5vw, 32px)" }}
                >
                  WithBuddy
                </span>
              </div>

              <LoginForm
                {...form}
                turnstileContainerRef={turnstileContainerRef}
                isDesktop={true}
              />

              <p className="text-[12px] text-[#868E96] text-center mt-[24px]">
                © 2026 WithBuddy. A Builders League Project.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* 모바일 footer */}
      <footer className="text-[10px] text-[#6A7282] text-center pb-6 md:text-[12px] lg:hidden">
        © 2026 WithBuddy. A Builders League Project.
      </footer>
    </div>
  );
}

export default Login;
