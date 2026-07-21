import { useState } from "react";
import LegalModal from "./LegalModal";

const LANDING_URL = "https://withbuddy-info.itsdev.kr";

function LoginFooter() {
  const [modalType, setModalType] = useState(null); // "terms" | "privacy" | null

  return (
    <>
      <footer className="w-full py-[20px] px-[24px] relative z-10">
        <div className="flex flex-wrap items-center justify-between max-w-screen-xl border-t-[2px] pt-[12px] mx-auto gap-y-[8px]">
          {/* 왼쪽 링크들 */}
          <div className="flex items-center gap-[24px] text-[13px] md:text-[14px] text-[#6A7282]">
            <a
              href={LANDING_URL}
              target="_blank"
              rel="noreferrer"
              className="hover:text-[#204867] transition-colors cursor-pointer"
            >
              서비스 소개하기
            </a>
            <button
              onClick={() => setModalType("terms")}
              className="hover:text-[#204867] transition-colors cursor-pointer bg-transparent border-none p-0 text-[13px] md:text-[14px] text-[#6A7282] font-inherit"
            >
              이용약관
            </button>
            <button
              onClick={() => setModalType("privacy")}
              className="hover:text-[#204867] transition-colors cursor-pointer bg-transparent border-none p-0 text-[13px] md:text-[14px] text-[#6A7282] font-inherit"
            >
              개인정보처리방침
            </button>
            <a
              href="mailto:withbuddy.official@gmail.com"
              className="hover:text-[#204867] transition-colors cursor-pointer"
            >
              문의하기
            </a>
          </div>

          {/* 오른쪽 저작권 */}
          <p className="text-[12px] md:text-[13px] text-[#6A7282]">
            &copy; 2026 WithBuddy. All rights reserved.
          </p>
        </div>
      </footer>

      {/* 모달 */}
      <LegalModal
        isOpen={modalType !== null}
        onClose={() => setModalType(null)}
        type={modalType}
      />
    </>
  );
}

export default LoginFooter;
