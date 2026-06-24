import { format } from "date-fns";
import { ko } from "date-fns/locale";
import bot from "../../assets/Bot_icon.svg";
import confetti from "../../assets/confetti.svg";

// 데스크탑 좌측 소개 섹션 (타이핑 말풍선 + 서브 카피 순환)
function HeroSection({ displayedText, currentSubCopy, isSubCopyVisible }) {
  const currentTime = format(new Date(), "a h:mm", { locale: ko });

  return (
    <div
      className="hidden lg:flex lg:flex-col lg:justify-center lg:items-start pr-[12px]"
      style={{
        flex: 1,
        minWidth: 0,
        paddingLeft: "clamp(50px, 10vw, 200px)",
      }}
    >
      <div style={{ width: "110%" }}>
        {/* 메인 카피 (고정) */}
        <h1
          className="font-bold text-[#204867] mb-[24px]"
          style={{ fontSize: "clamp(24px, 3vw, 45px)", lineHeight: "1.4" }}
        >
          처음이라 묻기 어려웠던 질문,
          <br />
          위드버디에게 먼저 물어보세요.
        </h1>

        {/* 서브 카피 (페이드 인/아웃) */}
        <div
          className="overflow-hidden mb-[30px]"
          style={{
            height: "clamp(40px, 5vw, 64px)",
            maxWidth: "100%",
          }}
        >
          <p
            className="text-[#204867] transition-all duration-[1000ms]"
            style={{
              fontSize: "clamp(14px, 1.5vw, 22px)",
              lineHeight: "1.6",
              opacity: isSubCopyVisible ? 0.88 : 0,
              transform: isSubCopyVisible
                ? "translateY(0)"
                : "translateY(-12px)",
            }}
          >
            {currentSubCopy}
          </p>
        </div>

        {/* 버디 아이콘 */}
        <div className="flex items-start gap-[20px] relative">
          <div
            className="relative shrink-0 flex items-center justify-center overflow-visible"
            style={{
              width: "clamp(36px, 4vw, 52px)",
              height: "clamp(36px, 4vw, 52px)",
            }}
          >
            <img
              src={bot}
              alt="위드버디 캐릭터"
              style={{ width: "clamp(28px, 3vw, 41px)", height: "auto" }}
            />
            <img
              src={confetti}
              alt=""
              className="absolute top-[-10px] right-[-25px]"
              style={{ width: "clamp(20px, 2.5vw, 31px)", height: "auto" }}
            />
          </div>

          {/* 말풍선 영역 */}
          <div className="flex flex-col gap-[10px] pt-[15px]">
            {/* 말풍선 */}
            <div
              className="border border-[#E9ECEF] shadow-[0px_2.59px_3.886px_rgba(0,0,0,0.06)] bg-[#FFFFFF] w-fit"
              style={{
                fontSize: "clamp(14px, 1.1vw, 16px)",
                borderRadius:
                  "clamp(8px, 1vw, 10px) clamp(20px, 2.5vw, 31px) clamp(20px, 2.5vw, 31px) clamp(20px, 2.5vw, 31px)",
                padding: "clamp(12px, 2vw, 20px)",
                display: "inline-block",
              }}
            >
              <p
                className="text-[#000000]"
                style={{ lineHeight: "1.8", whiteSpace: "pre-line" }}
              >
                {displayedText}
              </p>
            </div>

            {/* 퀵탭 */}
            <div className="flex gap-[10px] items-center flex-wrap">
              {[
                "💻 이메일·계정 세팅",
                "📦 비품 신청하기",
                "📅 연차 언제부터?",
              ].map((tap) => (
                <div
                  key={tap}
                  className="bg-[#FFFFFF] rounded-full flex items-center shadow-[0px_2.59px_2.59px_rgba(0,0,0,0.12)] text-[#495057] cursor-default whitespace-nowrap"
                  style={{
                    fontSize: "clamp(11px, 1.1vw, 14px)",
                    padding: "0 clamp(12px, 1.5vw, 20px)",
                    height: "clamp(32px, 3.5vw, 41px)",
                  }}
                >
                  {tap}
                </div>
              ))}
            </div>

            {/* 시간 */}
            <p
              className="text-[#868E96]"
              style={{ fontSize: "clamp(11px, 1.1vw, 15px)" }}
            >
              {currentTime}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default HeroSection;
