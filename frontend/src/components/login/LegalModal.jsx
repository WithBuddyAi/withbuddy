import { useEffect, useRef } from "react";

const modalOverlayStyle = {
  position: "fixed",
  top: 0,
  left: 0,
  width: "100%",
  height: "100%",
  background: "rgba(0, 0, 0, 0.5)",
  zIndex: 9999,
  display: "flex",
  justifyContent: "center",
  alignItems: "center",
  padding: "24px",
};

const modalStyle = {
  background: "#fff",
  borderRadius: "16px",
  width: "100%",
  maxWidth: "1000px",
  maxHeight: "85vh",
  display: "flex",
  flexDirection: "column",
  boxShadow: "0 20px 60px rgba(0, 0, 0, 0.15)",
  animation: "modalSlideUp 0.3s ease",
};

const modalHeaderStyle = {
  display: "flex",
  justifyContent: "space-between",
  alignItems: "center",
  padding: "24px 32px",
  borderBottom: "1px solid #e9ecef",
  flexShrink: 0,
};

const modalBodyStyle = {
  padding: "32px",
  overflowY: "auto",
  lineHeight: "1.8",
  fontSize: "15px",
  color: "#333",
};

function LegalModal({ isOpen, onClose, type }) {
  const bodyRef = useRef(null);

  // 열릴 때 스크롤 초기화 + body 스크롤 잠금
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = "hidden";
      requestAnimationFrame(() => {
        if (bodyRef.current) bodyRef.current.scrollTop = 0;
      });
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [isOpen]);

  // ESC 키로 닫기
  useEffect(() => {
    if (!isOpen) return;
    const handleKey = (e) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div style={modalOverlayStyle} onClick={handleOverlayClick}>
      <style>{`
        @keyframes modalSlideUp {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .legal-modal-body h1 { font-size: 22px; font-weight: 700; margin-bottom: 8px; color: #1a1a1a; }
        .legal-modal-body h2 { font-size: 17px; font-weight: 700; margin-top: 32px; margin-bottom: 12px; color: #1a1a1a; }
        .legal-modal-body h3 { font-size: 15px; font-weight: 600; margin-top: 24px; margin-bottom: 8px; color: #1a1a1a; }
        .legal-modal-body p { margin-bottom: 12px; }
        .legal-modal-body ol { padding-left: 24px; margin-bottom: 12px; list-style: decimal; }
        .legal-modal-body ul { padding-left: 24px; margin-bottom: 12px; list-style: disc; }
        .legal-modal-body li { margin-bottom: 6px; }
        .legal-modal-body strong { font-weight: 600; }
        .legal-modal-body hr { border: none; border-top: 1px solid #e9ecef; margin: 24px 0; }
        .legal-modal-body blockquote { background: #f8f9fa; border-left: 3px solid #336B97; padding: 12px 16px; margin: 16px 0; border-radius: 0 8px 8px 0; font-size: 14px; color: #495057; }
        .legal-modal-body table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 14px; }
        .legal-modal-body th, .legal-modal-body td { border: 1px solid #dee2e6; padding: 10px 12px; text-align: left; }
        .legal-modal-body th { background: #f8f9fa; font-weight: 600; }
        .legal-modal-body a { color: #336B97; text-decoration: underline; }
        .legal-modal-body::-webkit-scrollbar { width: 6px; }
        .legal-modal-body::-webkit-scrollbar-track { background: transparent; }
        .legal-modal-body::-webkit-scrollbar-thumb { background: #ced4da; border-radius: 3px; }
        .legal-modal-close { background: none; border: none; cursor: pointer; width: 36px; height: 36px; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #868e96; font-size: 24px; transition: background 0.2s, color 0.2s; }
        .legal-modal-close:hover { background: #f1f3f5; color: #1a1a1a; }
      `}</style>
      <div style={modalStyle}>
        <div style={modalHeaderStyle}>
          <h2 style={{ fontSize: "20px", fontWeight: 700, color: "#1a1a1a" }}>
            {type === "terms" ? "이용약관" : "개인정보 처리방침"}
          </h2>
          <button
            className="legal-modal-close"
            onClick={onClose}
            aria-label="닫기"
          >
            &times;
          </button>
        </div>
        <div ref={bodyRef} className="legal-modal-body" style={modalBodyStyle}>
          {type === "terms" ? <TermsContent /> : <PrivacyContent />}
        </div>
      </div>
    </div>
  );
}

/* 이용약관 내용 */
function TermsContent() {
  return (
    <>
      <h1>WithBuddy 서비스 이용약관</h1>
      <p>
        <strong>시행일: 2026년 7월 15일</strong>
      </p>
      <hr />

      <h2>제1조 (목적)</h2>
      <p>
        본 약관은 WithBuddy(이하 "회사")가 제공하는 AI 온보딩 에이전트
        서비스(이하 "서비스")의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및
        책임사항, 기타 필요한 사항을 규정함을 목적으로 합니다.
      </p>

      <h2>제2조 (정의)</h2>
      <ol>
        <li>
          "서비스"란 회사가 제공하는 AI 기반 신입사원 온보딩 에이전트 서비스 및
          이에 부수되는 제반 서비스를 의미합니다. 서비스는 회사 문서 기반 자연어
          질의응답(RAG Q&amp;A), 온보딩 안내 카드(Buddy Nudge), 담당자 연결 등의
          기능을 포함합니다.
        </li>
        <li>
          "이용자"란 본 약관에 동의하고 회사가 제공하는 서비스를 이용하는 자를
          말합니다.
        </li>
        <li>
          "관리자"란 서비스 도입 기업의 담당자로서, 이용자 계정 생성 및 관리
          권한을 가진 자를 말합니다.
        </li>
        <li>
          "도입 기업"이란 회사와 서비스 이용 계약을 체결하고, 소속 신입사원에게
          서비스를 제공하는 기업을 말합니다.
        </li>
        <li>
          "콘텐츠"란 서비스 이용 과정에서 생성되거나 제공되는 텍스트, 이미지,
          데이터 등 일체의 정보를 의미합니다.
        </li>
      </ol>

      <h2>제3조 (약관의 효력 및 변경)</h2>
      <ol>
        <li>
          본 약관은 서비스 화면에 게시하거나 기타의 방법으로 이용자에게
          공지함으로써 효력이 발생합니다.
        </li>
        <li>
          회사는 「약관의 규제에 관한 법률」, 「개인정보 보호법」 등 관련 법령을
          위배하지 않는 범위에서 본 약관을 변경할 수 있으며, 변경 시 적용일자 및
          변경사유를 명시하여 최소 7일 전에 공지합니다. 다만, 이용자에게 불리한
          변경의 경우 최소 30일 전에 공지합니다.
        </li>
        <li>
          변경된 약관에 동의하지 않는 이용자는 서비스 이용을 중단할 수 있습니다.
          변경된 약관의 효력 발생일 이후에도 서비스를 계속 이용하는 경우 약관
          변경에 동의한 것으로 간주합니다.
        </li>
      </ol>

      <h2>제4조 (계정 생성 및 로그인)</h2>
      <ol>
        <li>
          서비스의 계정은 도입 기업의 관리자가 직접 생성합니다. 이용자가 직접
          회원가입하는 방식이 아니며, 관리자가 이름, 사번, 입사일 등의 정보를
          입력하여 계정을 생성합니다.
        </li>
        <li>
          이용자는 관리자가 생성한 계정 정보(회사코드, 사번, 이름)를 통해
          서비스에 로그인합니다.
        </li>
        <li>
          이용자는 자신의 계정 정보를 제3자에게 양도하거나 공유할 수 없습니다.
        </li>
        <li>
          관리자는 계정 생성 시 정확한 정보를 입력해야 하며, 이용자의 퇴사 등
          사유 발생 시 계정을 비활성화할 수 있습니다.
        </li>
        <li>
          관리자는 소속 이용자의 개인정보를 적법하게 처리할 권한이 있는 범위에서
          계정을 생성해야 하며, 이를 위반하여 발생한 분쟁 및 책임은 관리자가
          소속된 도입 기업에 있습니다.
        </li>
      </ol>

      <h2>제5조 (서비스의 제공 및 변경)</h2>
      <ol>
        <li>
          회사는 다음과 같은 서비스를 제공합니다.
          <ul>
            <li>
              RAG Q&amp;A: 도입 기업의 사내 문서를 기반으로 한 자연어 질의응답
              서비스
            </li>
            <li>Buddy Nudge: 입사일 기준으로 맞춤형 온보딩 안내 카드 제공</li>
            <li>
              담당자 연결: AI가 답변할 수 없는 질문에 대해 관련 담당자 정보 제공
            </li>
            <li>관리자 페이지: 신입사원 계정 생성 및 관리 기능</li>
            <li>기타 회사가 추가 개발하거나 제휴를 통해 제공하는 서비스</li>
          </ul>
        </li>
        <li>
          회사는 서비스의 내용, 품질, 기술적 사양 등을 변경할 수 있으며, 이 경우
          변경 내용을 사전에 공지합니다.
        </li>
      </ol>

      <h2>제6조 (서비스의 중단)</h2>
      <ol>
        <li>
          회사는 시스템 점검, 교체, 고장, 통신 두절 등의 사유가 발생한 경우
          서비스의 전부 또는 일부를 제한하거나 중단할 수 있습니다.
        </li>
        <li>
          회사는 서비스 중단 시 사전에 공지하며, 불가피한 경우 사후에 공지할 수
          있습니다.
        </li>
        <li>
          회사는 무료로 제공되는 서비스의 중단에 대해 별도의 보상을 하지
          않습니다.
        </li>
      </ol>

      <h2>제7조 (유료 서비스 및 결제)</h2>
      <ol>
        <li>
          회사는 일부 서비스를 유료로 제공할 수 있으며, 유료 서비스의 이용요금,
          결제방법, 환불정책 등은 해당 서비스 화면에 별도로 게시합니다.
        </li>
        <li>
          유료 서비스의 이용 계약은 도입 기업과 회사 간에 체결되며, 결제와
          동시에 이용계약이 성립합니다.
        </li>
        <li>
          환불은 「전자상거래 등에서의 소비자보호에 관한 법률」 등 관련 법령 및
          회사의 환불정책에 따릅니다.
        </li>
        <li>
          회사는 결제 관련 정보를 직접 저장하지 않으며, 제3자 결제 대행사를 통해
          안전하게 처리합니다.
        </li>
      </ol>

      <h2>제8조 (이용자의 의무)</h2>
      <ol>
        <li>
          이용자는 다음 각 호의 행위를 하여서는 안 됩니다.
          <ul>
            <li>타인의 계정 정보를 도용하거나 무단으로 사용하는 행위</li>
            <li>서비스를 이용하여 법령 또는 공서양속에 반하는 행위</li>
            <li>회사의 지적재산권 또는 제3자의 권리를 침해하는 행위</li>
            <li>
              서비스의 안정적 운영을 방해하는 행위 (해킹, 악성코드 유포 등)
            </li>
            <li>
              서비스를 통해 얻은 사내 문서 정보를 외부에 무단 유출하는 행위
            </li>
            <li>서비스를 상업적 목적으로 무단 이용하는 행위</li>
            <li>기타 관련 법령에 위반되는 행위</li>
          </ul>
        </li>
        <li>
          이용자가 본 조를 위반하여 회사 또는 제3자에게 손해를 끼친 경우,
          이용자는 그 손해를 배상할 책임이 있습니다.
        </li>
      </ol>

      <h2>제9조 (회사의 의무)</h2>
      <ol>
        <li>
          회사는 관련 법령과 본 약관이 금지하거나 공서양속에 반하는 행위를 하지
          않으며, 지속적이고 안정적인 서비스 제공을 위해 최선을 다합니다.
        </li>
        <li>
          회사는 이용자의 개인정보를 「개인정보 보호법」에 따라 보호하기 위해
          개인정보 처리방침을 수립하고 이를 준수합니다.
        </li>
        <li>
          회사는 도입 기업의 사내 문서를 안전하게 관리하며, 다른 기업의 데이터와
          철저히 분리하여 처리합니다.
        </li>
        <li>
          회사는 서비스 이용과 관련하여 이용자로부터 제기된 의견이나 불만이
          정당하다고 인정되는 경우 적절한 조치를 취합니다.
        </li>
      </ol>

      <h2>제10조 (지적재산권)</h2>
      <ol>
        <li>
          서비스에 포함된 콘텐츠, 디자인, 소프트웨어, 기술 등에 대한
          지적재산권은 회사에 귀속됩니다.
        </li>
        <li>
          도입 기업이 서비스에 업로드한 사내 문서에 대한 지적재산권은 도입
          기업에 귀속되며, 회사는 서비스 제공 목적으로만 해당 문서를 이용합니다.
        </li>
        <li>
          이용자는 회사의 사전 동의 없이 서비스를 통해 얻은 정보를 복제, 배포,
          방송 등의 방법으로 상업적으로 이용하거나 제3자에게 제공할 수 없습니다.
        </li>
      </ol>

      <h2>제11조 (AI 서비스 관련 면책)</h2>
      <ol>
        <li>
          회사가 제공하는 AI 온보딩 에이전트의 응답은 도입 기업이 제공한 사내
          문서를 기반으로 생성되며, 원본 문서의 최신성 및 내용에 따라 답변
          결과가 달라질 수 있습니다. 이용자는 인사·복무·급여 등 중요한 사항에
          대해서는 원본 문서 또는 담당자를 통해 최종 확인할 것을 권장합니다.
        </li>
        <li>
          회사는 AI 응답의 품질 향상을 위해 지속적으로 노력하나, 답변의 완전성을
          보장하지 않습니다. 다만 회사의 고의 또는 중대한 과실로 인해 이용자에게
          손해가 발생한 경우에는 관련 법령에 따라 책임을 집니다.
        </li>
        <li>
          회사는 AI 서비스의 성능 개선을 위해 이용자의 서비스 이용 데이터를
          활용할 수 있으며, 이 경우 개인정보 처리방침에 따릅니다.
        </li>
        <li>
          AI가 답변할 수 없는 질문에 대해서는 관련 담당자 정보를 안내하며, 해당
          담당자의 응대 여부 및 내용에 대해 회사는 책임지지 않습니다.
        </li>
      </ol>

      <h2>제12조 (데이터 보안 및 멀티테넌시)</h2>
      <ol>
        <li>
          회사는 각 도입 기업의 데이터를 논리적으로 분리하여 관리하며, 다른
          기업의 이용자가 접근할 수 없도록 합니다.
        </li>
        <li>
          도입 기업의 사내 문서 데이터는 서비스 해지 시 관련 법령에 따른 보존
          기간 경과 후 완전히 삭제됩니다.
        </li>
      </ol>

      <h2>제13조 (면책조항)</h2>
      <ol>
        <li>
          회사는 천재지변, 전쟁, 기간통신사업자의 서비스 중지 등 불가항력으로
          인해 서비스를 제공할 수 없는 경우 책임이 면제됩니다.
        </li>
        <li>
          회사는 이용자의 귀책사유로 인한 서비스 이용 장애에 대해 책임지지
          않습니다.
        </li>
        <li>
          회사는 이용자가 서비스를 통해 기대하는 수익을 얻지 못한 것에 대해
          책임지지 않습니다.
        </li>
      </ol>

      <h2>제14조 (분쟁해결)</h2>
      <ol>
        <li>
          회사와 이용자 간에 발생한 분쟁에 대해서는 대한민국 법률을 적용합니다.
        </li>
        <li>
          서비스 이용과 관련하여 발생한 분쟁은 「민사소송법」에 따른 관할법원에
          제소할 수 있습니다.
        </li>
      </ol>

      <h2>제15조 (기타)</h2>
      <ol>
        <li>
          본 약관에서 정하지 않은 사항은 「전자상거래 등에서의 소비자보호에 관한
          법률」, 「약관의 규제에 관한 법률」, 「개인정보 보호법」 등 관련 법령
          및 상관례에 따릅니다.
        </li>
        <li>
          본 약관의 일부 조항이 무효로 판정되더라도 나머지 조항은 유효합니다.
        </li>
      </ol>

      <hr />
      <blockquote>
        <strong>안내</strong>: 본 약관은 교육 프로젝트(BUILDERS LEAGUE 2026)
        목적으로 작성되었으며, 관련 법령을 참고하여 구성하였으나 법률 전문가의
        검토를 거치지 않았습니다. 실제 기업 서비스에 적용하기 위해서는 반드시
        법률 전문가의 검토를 받으시기 바랍니다.
      </blockquote>
      <hr />
      <p>
        <strong>부칙</strong>
      </p>
      <p>본 약관은 2026년 7월 15일부터 시행합니다.</p>
    </>
  );
}

/* 개인정보 처리방침 내용 */
function PrivacyContent() {
  return (
    <>
      <h1>WithBuddy 개인정보 처리방침</h1>
      <p>
        <strong>시행일: 2026년 7월 15일</strong>
      </p>
      <hr />
      <p>
        WithBuddy(이하 "회사")는 「개인정보 보호법」 제30조에 따라 이용자의
        개인정보를 보호하고 이와 관련한 고충을 신속하고 원활하게 처리할 수
        있도록 다음과 같이 개인정보 처리방침을 수립·공개합니다.
      </p>
      <hr />

      <h2>제1조 (개인정보의 처리 목적)</h2>
      <blockquote>「개인정보 보호법」 제30조 제1항 제1호</blockquote>
      <p>
        회사는 다음의 목적을 위하여 개인정보를 처리합니다. 처리하고 있는
        개인정보는 다음의 목적 이외의 용도로는 이용되지 않으며, 이용 목적이
        변경되는 경우에는 「개인정보 보호법」 제18조에 따라 별도의 동의를 받는
        등 필요한 조치를 이행할 예정입니다.
      </p>
      <ol>
        <li>
          <strong>이용자 관리</strong>: 이용자 식별, 계정 관리, 본인 확인, 부정
          이용 방지
        </li>
        <li>
          <strong>서비스 제공</strong>: AI 온보딩 에이전트 서비스 제공, 입사일
          기반 맞춤형 온보딩 안내(Buddy Nudge), 사내 문서 기반 질의응답(RAG
          Q&amp;A)
        </li>
        <li>
          <strong>유료 서비스</strong>: 요금 결제, 정산, 환불 처리 (도입 기업
          대상)
        </li>
        <li>
          <strong>고객 지원</strong>: 문의 및 불만 처리, 공지사항 전달
        </li>
        <li>
          <strong>서비스 개선</strong>: 서비스 이용 통계 분석, 신규 서비스 개발,
          AI 응답 품질 향상
        </li>
      </ol>

      <h2>제2조 (개인정보의 처리 및 보유 기간)</h2>
      <blockquote>「개인정보 보호법」 제30조 제1항 제2호</blockquote>
      <p>
        회사는 법령에 따른 개인정보 보유·이용 기간 또는 이용자로부터 개인정보를
        수집 시에 동의 받은 개인정보 보유·이용 기간 내에서 개인정보를
        처리·보유합니다.
      </p>
      <ol>
        <li>
          <strong>서비스 이용 기간</strong>: 계정 비활성화 또는 도입 기업의
          서비스 해지 시까지
        </li>
        <li>관련 법령에 따라 보존이 필요한 경우 아래 기간 동안 보관합니다.</li>
      </ol>
      <table>
        <thead>
          <tr>
            <th>보존 항목</th>
            <th>보존 기간</th>
            <th>근거 법령</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>계약 또는 청약철회 등에 관한 기록</td>
            <td>5년</td>
            <td>「전자상거래 등에서의 소비자보호에 관한 법률」 시행령 제6조</td>
          </tr>
          <tr>
            <td>대금결제 및 재화 등의 공급에 관한 기록</td>
            <td>5년</td>
            <td>「전자상거래 등에서의 소비자보호에 관한 법률」 시행령 제6조</td>
          </tr>
          <tr>
            <td>소비자의 불만 또는 분쟁처리에 관한 기록</td>
            <td>3년</td>
            <td>「전자상거래 등에서의 소비자보호에 관한 법률」 시행령 제6조</td>
          </tr>
          <tr>
            <td>표시·광고에 관한 기록</td>
            <td>6개월</td>
            <td>「전자상거래 등에서의 소비자보호에 관한 법률」 시행령 제6조</td>
          </tr>
          <tr>
            <td>접속에 관한 기록(로그기록)</td>
            <td>3개월 이상</td>
            <td>「통신비밀보호법」 시행령 제41조</td>
          </tr>
        </tbody>
      </table>
      <blockquote>
        ※ AI 질의응답 기록, 이용 로그, 도입 기업 업로드 문서 등 항목별 세부
        보유기간은 현재 정책이 확정되지 않아 별도 기재하지 않았습니다. 정책 확정
        시 본 조를 개정하여 반영합니다.
      </blockquote>

      <h2>제3조 (개인정보의 제3자 제공)</h2>
      <blockquote>「개인정보 보호법」 제30조 제1항 제3호</blockquote>
      <p>
        회사는 이용자의 개인정보를 제1조에서 명시한 범위 내에서만 처리하며,
        원칙적으로 제3자에게 제공하지 않습니다. 다만, 다음의 경우에는 예외로
        합니다.
      </p>
      <ol>
        <li>이용자가 사전에 동의한 경우</li>
        <li>
          법령의 규정에 의거하거나, 수사 목적으로 법령에 정해진 절차와 방법에
          따라 수사기관의 요구가 있는 경우
        </li>
      </ol>

      <h2>제4조 (개인정보의 국외 이전)</h2>
      <blockquote>「개인정보 보호법」 제28조의8</blockquote>
      <p>
        회사는 AI 기반 질의응답(RAG Q&amp;A) 서비스 제공을 위해 아래와 같이
        개인정보를 국외로 이전하고 있습니다.
      </p>
      <table>
        <thead>
          <tr>
            <th>구분</th>
            <th>내용</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>이전받는 자</td>
            <td>Anthropic, Inc. (미국)</td>
          </tr>
          <tr>
            <td>이전되는 개인정보 항목</td>
            <td>질문 내용(질의 원문), 이용자 이름, 입사일</td>
          </tr>
          <tr>
            <td>이전 일시 및 방법</td>
            <td>
              이용자가 AI에 질문을 입력하여 서비스를 이용할 때마다 네트워크를
              통해 실시간 전송
            </td>
          </tr>
          <tr>
            <td>이전받는 자의 이용 목적</td>
            <td>AI 질의응답 생성 처리 (서비스 제공을 위한 처리위탁)</td>
          </tr>
          <tr>
            <td>이전받는 자의 보유·이용 기간</td>
            <td>처리위탁 계약 및 이전받는 자의 정책에 따름</td>
          </tr>
        </tbody>
      </table>
      <p>
        사번은 원문이 아닌 회사 내부에서 발급한 식별자(userId)로 대체하여
        전송하며, 사번 원문은 국외로 이전되지 않습니다.
      </p>

      <h2>제5조 (개인정보의 파기절차 및 파기방법)</h2>
      <blockquote>「개인정보 보호법」 제30조 제1항 제3호의2</blockquote>
      <ol>
        <li>
          <strong>파기절차</strong>: 회사는 개인정보 보유 기간의 경과, 처리 목적
          달성 등 개인정보가 불필요하게 되었을 때에는 지체 없이 해당 개인정보를
          파기합니다. 다만, 다른 법령에 따라 보존하여야 하는 경우에는 제2조의
          표에 따릅니다.
        </li>
        <li>
          <strong>파기방법</strong>:
          <ul>
            <li>
              전자적 파일: 복구 및 재생이 불가능한 기술적 방법을 사용하여 완전히
              삭제
            </li>
            <li>종이 문서: 분쇄기로 분쇄하거나 소각</li>
          </ul>
        </li>
        <li>
          도입 기업이 서비스 이용을 해지한 경우, 해당 기업 소속 이용자의
          개인정보 및 사내 문서 데이터는 관련 법령에 따른 보존 기간 경과 후
          파기됩니다.
        </li>
      </ol>

      <h2>제6조 (개인정보처리의 위탁)</h2>
      <blockquote>「개인정보 보호법」 제30조 제1항 제4호</blockquote>
      <p>
        회사는 원활한 서비스 제공을 위해 개인정보 처리를 외부 업체에 위탁할 수
        있으며, 위탁 시 「개인정보 보호법」 제26조에 따라 위탁업무 수행 목적 외
        개인정보 처리 금지, 안전성 확보 조치, 재위탁 제한, 수탁자에 대한
        관리·감독 등을 문서로 정하고 이를 준수합니다.
      </p>
      <table>
        <thead>
          <tr>
            <th>수탁업체</th>
            <th>위탁 업무 내용</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Anthropic, Inc.</td>
            <td>AI 질의응답 생성 처리 (제4조 국외 이전 참조)</td>
          </tr>
          <tr>
            <td>(결제 대행사명)</td>
            <td>결제 처리 및 정산</td>
          </tr>
        </tbody>
      </table>
      <p>위탁업체가 변경될 경우 본 방침을 통해 공지합니다.</p>

      <h2>제7조 (정보주체와 법정대리인의 권리·의무 및 행사방법)</h2>
      <blockquote>「개인정보 보호법」 제30조 제1항 제5호</blockquote>
      <ol>
        <li>
          이용자(정보주체)는 회사에 대해 언제든지 다음의 권리를 행사할 수
          있습니다.
          <ul>
            <li>개인정보 열람 요구 (「개인정보 보호법」 제35조)</li>
            <li>개인정보 정정·삭제 요구 (「개인정보 보호법」 제36조)</li>
            <li>개인정보 처리정지 요구 (「개인정보 보호법」 제37조)</li>
            <li>동의 철회</li>
          </ul>
        </li>
        <li>
          위 권리 행사는 도입 기업의 관리자 또는 회사
          이메일(team@withbuddy.ai)을 통해 가능하며, 회사는 이에 대해 지체 없이
          조치하겠습니다.
        </li>
        <li>
          이용자가 개인정보의 오류 등에 대한 정정 또는 삭제를 요구한 경우 회사는
          정정 또는 삭제를 완료할 때까지 당해 개인정보를 이용하거나 제공하지
          않습니다.
        </li>
        <li>
          만 14세 미만 아동의 경우 법정대리인이 권리를 행사할 수 있습니다.
        </li>
      </ol>

      <h2>제8조 (개인정보 보호책임자)</h2>
      <blockquote>「개인정보 보호법」 제30조 제1항 제6호, 제31조</blockquote>
      <p>
        회사는 개인정보 처리에 관한 업무를 총괄해서 책임지고, 개인정보 처리와
        관련한 이용자의 불만 처리 및 피해 구제 등을 위하여 아래와 같이 개인정보
        보호책임자를 지정하고 있습니다.
      </p>
      <ul>
        <li>
          <strong>개인정보 보호책임자</strong>
          <ul>
            <li>담당자: (담당자명)</li>
            <li>이메일: team@withbuddy.ai</li>
          </ul>
        </li>
      </ul>
      <p>
        이용자는 서비스를 이용하면서 발생한 모든 개인정보 보호 관련 문의,
        불만처리, 피해구제 등에 관한 사항을 개인정보 보호책임자에게 문의하실 수
        있습니다.
      </p>

      <h2>
        제9조 (인터넷 접속정보파일 등 개인정보를 자동으로 수집하는 장치의
        설치·운영 및 그 거부에 관한 사항)
      </h2>
      <blockquote>「개인정보 보호법」 제30조 제1항 제7호</blockquote>
      <ol>
        <li>
          회사는 이용자에게 개별적인 맞춤 서비스를 제공하기 위해 이용 정보를
          저장하고 수시로 불러오는 '쿠키(cookie)'를 사용합니다.
        </li>
        <li>
          쿠키는 웹사이트를 운영하는 데 이용되는 서버가 이용자의 브라우저에게
          보내는 소량의 정보이며, 이용자의 컴퓨터 하드디스크에 저장됩니다.
        </li>
        <li>
          <strong>쿠키의 사용 목적</strong>: 이용자의 접속 빈도나 방문 시간 등을
          분석하여 서비스 개선에 활용합니다.
        </li>
        <li>
          <strong>Google Analytics 4(GA4) 이용 안내</strong>: 회사는 서비스 이용
          행태 분석 및 서비스 개선을 위해 Google Analytics 4를 이용합니다. 이
          과정에서 페이지 조회, 버튼 클릭 등 이벤트 발생 정보, 기기 및 브라우저
          정보 등이 처리될 수 있습니다. 회사는 이용자가 입력한 질문 원문, AI
          답변 원문, 이름, 사번 등 개인을 식별할 수 있는 정보를 GA4 분석
          이벤트로 전송하지 않습니다.
        </li>
        <li>
          <strong>쿠키 설치·운영 및 거부</strong>: 이용자는 웹 브라우저의 설정을
          통해 쿠키 저장을 거부할 수 있습니다. 다만, 쿠키 저장을 거부할 경우
          일부 서비스 이용에 어려움이 발생할 수 있습니다.
          <ul>
            <li>
              Chrome: 설정 → 개인정보 및 보안 → 쿠키 및 기타 사이트 데이터
            </li>
            <li>Edge: 설정 → 쿠키 및 사이트 권한</li>
          </ul>
        </li>
      </ol>

      <h2>제10조 (수집하는 개인정보 항목 및 수집 방법)</h2>
      <blockquote>
        「개인정보 보호법」 제30조 제1항 제8호, 시행령 제31조
      </blockquote>
      <h3>1. 수집 항목</h3>
      <p>
        <strong>필수 항목 (관리자가 계정 생성 시 입력)</strong>
      </p>
      <ul>
        <li>이름</li>
        <li>사번</li>
        <li>입사일</li>
      </ul>
      <p>
        <strong>자동 수집 항목</strong>
      </p>
      <ul>
        <li>접속 IP 주소, 쿠키, 접속 일시, 브라우저 종류, 운영체제 정보</li>
        <li>서비스 이용 기록, 접속 로그, AI 질의응답 기록</li>
      </ul>
      <p>
        <strong>유료 서비스 이용 시 (도입 기업 대상)</strong>
      </p>
      <ul>
        <li>
          결제 관련 정보 (결제 대행사를 통해 처리되며, 회사는 카드번호 등 민감한
          결제 정보를 직접 저장하지 않습니다)
        </li>
      </ul>
      <p>
        <strong>AI 서비스 처리 과정에서 국외로 이전되는 정보</strong>
      </p>
      <ul>
        <li>질문 내용, 이름, 입사일 (제4조 참조)</li>
      </ul>
      <h3>2. 수집 방법</h3>
      <ul>
        <li>도입 기업의 관리자가 계정 생성 시 직접 입력</li>
        <li>서비스 이용 과정에서 자동으로 생성 및 수집</li>
      </ul>

      <h2>제11조 (개인정보의 안전성 확보 조치)</h2>
      <blockquote>「개인정보 보호법」 제29조, 시행령 제30조</blockquote>
      <p>
        회사는 개인정보의 안전성 확보를 위해 다음과 같은 조치를 취하고 있습니다.
      </p>
      <ol>
        <li>
          <strong>관리적 조치</strong>: 내부 관리계획 수립·시행, 개인정보 취급
          직원 최소화 및 정기적인 교육 실시
        </li>
        <li>
          <strong>기술적 조치</strong>: 접근권한 관리, 인증 및 인가 체계 적용,
          개인정보 전송구간 보호, 접속기록의 보관 및 위·변조 방지, 도입 기업 간
          데이터 접근 분리(멀티테넌시)
        </li>
        <li>
          <strong>물리적 조치</strong>: 개인정보 보관 시설의 접근 통제
        </li>
      </ol>

      <h2>제12조 (AI 서비스 관련 개인정보 처리)</h2>
      <blockquote>
        개인정보보호위원회 「개인정보 처리방침 작성지침(2026.4.)」 생성형 AI
        부록
      </blockquote>
      <ol>
        <li>
          회사는 AI 서비스 제공 과정에서 이용자가 입력한 질문 데이터를 처리하며,
          이는 서비스 제공 목적으로만 사용됩니다. 질문 원문 및 관련
          개인정보(이름, 입사일)는 AI 응답 생성을 위해 국외 소재 AI 모델
          제공자(Anthropic, Inc.)로 전송됩니다. 자세한 사항은 제4조를 참조하시기
          바랍니다.
        </li>
        <li>
          <strong>AI 학습 활용 여부</strong>: 회사는 이용자의 질문 및 개인정보를
          WithBuddy 자체 AI 모델의 학습 데이터로 사용하지 않습니다. 다만 외부 AI
          API를 이용하는 경우, 해당 데이터의 처리(학습 활용 여부 포함)는 해당
          서비스 제공자와의 계약 및 데이터 처리 조건에 따릅니다.
        </li>
        <li>
          <strong>AI 학습 거부(Opt-out)</strong>: 이용자는 자신의 서비스 이용
          데이터가 서비스 개선 목적으로 활용되는 것을 거부할 수 있습니다. 거부를
          원하는 경우 개인정보 보호책임자 이메일(team@withbuddy.ai)로 요청하시면
          됩니다.
        </li>
        <li>
          <strong>부적절한 답변 신고</strong>: 이용자는 AI 응답이 부정확하거나
          부적절하다고 판단되는 경우 서비스 내 신고 기능 또는 개인정보
          보호책임자 이메일을 통해 이의를 제기할 수 있습니다.
        </li>
        <li>
          이용자의 AI 질의응답 기록은 서비스 품질 개선 및 온보딩 효과 분석
          목적으로 활용될 수 있으며, 이 경우 개인을 직접 식별하기 위한 목적으로
          이용하지 않습니다.
        </li>
      </ol>

      <h2>제13조 (권익침해 구제방법)</h2>
      <p>
        이용자는 아래의 기관에 대해 개인정보 침해에 대한 피해구제, 상담 등을
        문의하실 수 있습니다.
      </p>
      <ul>
        <li>
          <strong>개인정보침해 신고센터</strong> (한국인터넷진흥원 운영)
          <br />
          홈페이지:{" "}
          <a href="https://privacy.kisa.or.kr" target="_blank" rel="noreferrer">
            privacy.kisa.or.kr
          </a>{" "}
          / 전화: 국번없이 118
        </li>
        <li>
          <strong>개인정보 분쟁조정위원회</strong>
          <br />
          홈페이지:{" "}
          <a href="https://www.kopico.go.kr" target="_blank" rel="noreferrer">
            www.kopico.go.kr
          </a>{" "}
          / 전화: 1833-6972
        </li>
        <li>
          <strong>대검찰청 사이버수사과</strong>
          <br />
          홈페이지:{" "}
          <a href="https://www.spo.go.kr" target="_blank" rel="noreferrer">
            www.spo.go.kr
          </a>{" "}
          / 전화: 국번없이 1301
        </li>
        <li>
          <strong>경찰청 사이버수사국</strong>
          <br />
          홈페이지:{" "}
          <a href="https://ecrm.police.go.kr" target="_blank" rel="noreferrer">
            ecrm.police.go.kr
          </a>{" "}
          / 전화: 국번없이 182
        </li>
      </ul>

      <h2>제14조 (개인정보 처리방침의 변경)</h2>
      <p>
        본 개인정보 처리방침이 변경되는 경우 시행일자 최소 7일 전에 서비스 화면
        또는 이메일을 통해 변경 사유 및 내용을 공지합니다. 다만, 이용자 권리의
        중대한 변경이 있는 경우에는 최소 30일 전에 공지합니다.
      </p>

      <hr />
      <blockquote>
        <strong>안내</strong>: 본 개인정보 처리방침은 교육 프로젝트(BUILDERS
        LEAGUE 2026) 목적으로 작성되었으며, 「개인정보 보호법」 등 관련 법령을
        참고하여 구성하였으나 법률 전문가의 검토를 거치지 않았습니다. 실제 기업
        서비스에 적용하기 위해서는 반드시 법률 전문가의 검토를 받으시기
        바랍니다.
      </blockquote>
      <hr />
      <p>
        <strong>부칙</strong>
      </p>
      <p>본 개인정보 처리방침은 2026년 7월 15일부터 시행합니다.</p>
    </>
  );
}

export default LegalModal;
