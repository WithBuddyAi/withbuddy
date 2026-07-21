// ===== Scroll Animations (Intersection Observer) =====
(function() {
  'use strict';

  var animatedElements = document.querySelectorAll('.animate-on-scroll');

  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function(entries) {
      entries.forEach(function(entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          observer.unobserve(entry.target);
        }
      });
    }, {
      threshold: 0.1,
      rootMargin: '0px 0px -50px 0px'
    });

    animatedElements.forEach(function(el) {
      observer.observe(el);
    });
  } else {
    // Fallback: show all elements
    animatedElements.forEach(function(el) {
      el.classList.add('visible');
    });
  }

  // ===== Chart bar animation =====
  var chartContainer = document.querySelector('.pricing-chart');
  if (chartContainer && 'IntersectionObserver' in window) {
    var chartObserver = new IntersectionObserver(function(entries) {
      entries.forEach(function(entry) {
        if (entry.isIntersecting) {
          setTimeout(function() {
            entry.target.classList.add('animated');
          }, 300);
          chartObserver.unobserve(entry.target);
        }
      });
    }, {
      threshold: 0.3
    });

    chartObserver.observe(chartContainer);
  }

  // ===== Counter animation for stat numbers =====
  var statNumbers = document.querySelectorAll('.stat-number');
  if (statNumbers.length && 'IntersectionObserver' in window) {
    var statObserver = new IntersectionObserver(function(entries) {
      entries.forEach(function(entry) {
        if (entry.isIntersecting) {
          animateCounter(entry.target);
          statObserver.unobserve(entry.target);
        }
      });
    }, { threshold: 0.5 });

    statNumbers.forEach(function(el) {
      statObserver.observe(el);
    });
  }

  function animateCounter(el) {
    var text = el.textContent;
    var match = text.match(/(\d+)/);
    if (!match) return;

    var target = parseInt(match[1], 10);
    var suffix = text.replace(match[1], '');
    var duration = 1500;
    var start = 0;
    var startTime = null;

    function step(timestamp) {
      if (!startTime) startTime = timestamp;
      var progress = Math.min((timestamp - startTime) / duration, 1);
      var eased = 1 - Math.pow(1 - progress, 3);
      var current = Math.round(eased * target);
      el.textContent = current + suffix;
      if (progress < 1) {
        requestAnimationFrame(step);
      }
    }

    requestAnimationFrame(step);
  }

  // ===== Parallax effect for hero section =====
  var heroSection = document.querySelector('.hero');
  if (heroSection) {
    window.addEventListener('scroll', function() {
      var scrolled = window.scrollY;
      if (scrolled < window.innerHeight) {
        var heroContent = heroSection.querySelector('.hero-content');
        var heroMockup = heroSection.querySelector('.hero-mockup');
        if (heroContent) {
          heroContent.style.transform = 'translateY(' + (scrolled * 0.1) + 'px)';
        }
        if (heroMockup) {
          heroMockup.style.transform = 'translateY(' + (scrolled * 0.05) + 'px)';
        }
      }
    });
  }
})();
