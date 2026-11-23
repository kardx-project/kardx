// Keep short inline code on one line (identifiers, options, paths up to 48 characters).
// Longer code may still wrap at spaces so it doesn't push the page sideways.
(function () {
  function mark() {
    document.querySelectorAll(".md-typeset :not(pre) > code").forEach(function (el) {
      if (el.textContent.length <= 48) el.classList.add("kx-nowrap");
    });
  }
  if (window.document$ && window.document$.subscribe) {
    window.document$.subscribe(mark); // Material instant navigation
  } else {
    document.addEventListener("DOMContentLoaded", mark);
  }
})();
