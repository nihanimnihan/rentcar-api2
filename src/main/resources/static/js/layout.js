document.addEventListener("DOMContentLoaded", function () {
  loadPartial("footer-placeholder", "partials/footer.html");
});

document.addEventListener("DOMContentLoaded", function () {
  loadPartial("language-modal-placeholder", "partials/language-modal.html");
});

function loadPartial(elementId, filePath) {
  var element = document.getElementById(elementId);

  if (!element) return Promise.resolve();

  return fetch(filePath)
    .then(function (response) { return response.text(); })
    .then(function (html) {
      element.innerHTML = html;
      if (typeof applyTranslations === "function") {
        applyTranslations(element);
      }
    })
    .catch(function (error) { console.error("Partial could not be loaded:", error); });
}

document.addEventListener("DOMContentLoaded", async function () {
  var filtersContainer = document.getElementById("car-filters-placeholder");
  if (!filtersContainer) return;

  var response = await fetch("/partials/car-filters.html?v=20260619-filter-fix");
  var html = await response.text();

  filtersContainer.innerHTML = html;

  if (typeof applyTranslations === "function") {
    applyTranslations(filtersContainer);
  }

  if (typeof initCarFilters === "function") {
    initCarFilters();
    markSelectedFilters();
  }

  // Trigger car search after partial load so #carsList is guaranteed to exist.
  if (typeof loadCars === "function") {
    loadCars();
  }
});

// Preloader removed to prevent white flash on theme pages. Previously injected partial and removed after 800ms.

document.addEventListener("DOMContentLoaded", function () {
    var minimalFooter = document.getElementById("minimal-footer-placeholder");
    if (!minimalFooter) return;
    fetch("partials/footer-minimal.html")
        .then(function (r) { return r.text(); })
        .then(function (html) {
            minimalFooter.innerHTML = html;
            if (typeof applyTranslations === "function") {
                applyTranslations(minimalFooter);
            }
        });
});
const minimalHeader = document.getElementById("minimal-header-placeholder");

if (minimalHeader) {
    fetch("partials/header-minimal.html")
        .then(response => response.text())
        .then(html => {
            minimalHeader.innerHTML = html;
            if (typeof applyTranslations === "function") {
                applyTranslations(minimalHeader);
            }
            if (typeof updateCurrentLanguageLabels === "function") {
                updateCurrentLanguageLabels();
            }
        });
}

// ── Language dropdown ─────────────────────────────────────────────────────
(function () {
    function getLangMenu() {
        return document.querySelector('.langMenu');
    }

    function getLangContent() {
        var dd = getLangMenu();
        return dd ? dd.querySelector('.langMenu__content') : null;
    }

    function positionUnder(btn) {
        var content = getLangContent();
        if (!content) return;
        var rect = btn.getBoundingClientRect();
        content.style.top   = (rect.bottom + 8) + 'px';
        content.style.right = (window.innerWidth - rect.right) + 'px';
        content.style.left  = 'auto';
    }

    function openDropdown(btn) {
        var dd = getLangMenu();
        if (!dd) return;
        positionUnder(btn);
        dd.classList.remove('is-hidden');
    }

    function closeDropdown() {
        var dd = getLangMenu();
        if (dd) dd.classList.add('is-hidden');
    }

    document.addEventListener('click', function (e) {
        var btn = e.target.closest('.js-lang-toggle');
        if (btn) {
            var dd = getLangMenu();
            if (dd && !dd.classList.contains('is-hidden')) {
                closeDropdown();
            } else {
                openDropdown(btn);
            }
            return;
        }

        // Click-outside: close if the click lands outside the dropdown content
        var content = getLangContent();
        var dd = getLangMenu();
        if (dd && !dd.classList.contains('is-hidden')) {
            if (!content || !content.contains(e.target)) {
                closeDropdown();
            }
        }
    });
}());
