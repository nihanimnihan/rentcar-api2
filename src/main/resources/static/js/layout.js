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

  var response = await fetch("/partials/car-filters.html");
  var html = await response.text();

  filtersContainer.innerHTML = html;

  if (typeof applyTranslations === "function") {
    applyTranslations(filtersContainer);
  }

  if (typeof initCarFilters === "function") {
    initCarFilters();
    markSelectedFilters();
  }
});

document.addEventListener("DOMContentLoaded", async function () {
  await loadPartial("preloader-placeholder", "partials/preloader.html");

  setTimeout(function () {
    var preloader = document.querySelector(".js-preloader");

    if (!preloader) return;

    preloader.style.opacity = "0";
    preloader.style.visibility = "hidden";
    preloader.style.pointerEvents = "none";

    setTimeout(function () {
      preloader.remove();
    }, 500);
  }, 800);
});

const minimalFooter = document.getElementById("minimal-footer-placeholder");

if (minimalFooter) {
    fetch("partials/footer-minimal.html")
        .then(response => response.text())
        .then(html => {
            minimalFooter.innerHTML = html;
        });
}
const minimalHeader = document.getElementById("minimal-header-placeholder");

if (minimalHeader) {
    fetch("partials/header-minimal.html")
        .then(response => response.text())
        .then(html => {
            minimalHeader.innerHTML = html;
        });
}