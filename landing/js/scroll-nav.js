// ===== Scroll navigation buttons =====
(function() {
  'use strict';

  var scrollNavBtns = document.getElementById('scrollNavBtns');
  var scrollUpBtn = document.getElementById('scrollUpBtn');
  var scrollDownBtn = document.getElementById('scrollDownBtn');

  if (scrollNavBtns && scrollUpBtn && scrollDownBtn) {
    // Scroll to top
    scrollUpBtn.addEventListener('click', function() {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });

    // Scroll to bottom
    scrollDownBtn.addEventListener('click', function() {
      window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
    });
  }
})();
