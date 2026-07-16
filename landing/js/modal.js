// ===== MODAL =====
document.addEventListener('DOMContentLoaded', function () {
  const overlays = document.querySelectorAll('.modal-overlay');
  const closeButtons = document.querySelectorAll('.modal-close');

  // 모달 열기
  function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;
    modal.classList.add('active');
    document.body.classList.add('modal-open');
    // display: flex가 적용된 후에 스크롤 초기화
    var modalBody = modal.querySelector('.modal-body');
    if (modalBody) {
      requestAnimationFrame(function () {
        modalBody.scrollTop = 0;
      });
    }
  }

  // 모달 닫기
  function closeModal(overlay) {
    overlay.classList.remove('active');
    document.body.classList.remove('modal-open');
  }

  // 이용약관 링크
  const termsLink = document.getElementById('termsLink');
  if (termsLink) {
    termsLink.addEventListener('click', function (e) {
      e.preventDefault();
      openModal('termsModal');
    });
  }

  // 개인정보처리방침 링크
  const privacyLink = document.getElementById('privacyLink');
  if (privacyLink) {
    privacyLink.addEventListener('click', function (e) {
      e.preventDefault();
      openModal('privacyModal');
    });
  }

  // 닫기 버튼 클릭
  closeButtons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      const overlay = btn.closest('.modal-overlay');
      closeModal(overlay);
    });
  });

  // 오버레이(배경) 클릭 시 닫기
  overlays.forEach(function (overlay) {
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) {
        closeModal(overlay);
      }
    });
  });

  // ESC 키로 닫기
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      overlays.forEach(function (overlay) {
        if (overlay.classList.contains('active')) {
          closeModal(overlay);
        }
      });
    }
  });
});
