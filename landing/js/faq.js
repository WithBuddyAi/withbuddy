// ===== FAQ accordion — single-open =====
(function() {
  'use strict';

  var faqButtons = document.querySelectorAll('.faq-question');
  faqButtons.forEach(function(btn) {
    btn.addEventListener('click', function() {
      var item = btn.closest('.faq-item');
      var answer = item.querySelector('.faq-answer');
      var icon = btn.querySelector('.faq-icon');
      var isOpen = item.classList.contains('faq-item-open');

      // Close all
      document.querySelectorAll('.faq-item').forEach(function(fi) {
        fi.classList.remove('faq-item-open');
        fi.querySelector('.faq-answer').classList.remove('faq-answer-open');
        fi.querySelector('.faq-question').setAttribute('aria-expanded', 'false');
        fi.querySelector('.faq-icon').textContent = '+';
      });

      // Toggle current
      if (!isOpen) {
        item.classList.add('faq-item-open');
        answer.classList.add('faq-answer-open');
        btn.setAttribute('aria-expanded', 'true');
        icon.textContent = '−';
      }
    });
  });
})();
