document.addEventListener("DOMContentLoaded", function () {
  loadPartial("footer-placeholder", "partials/footer.html");
});

document.addEventListener("DOMContentLoaded", function () {
  loadPartial("language-modal-placeholder", "partials/language-modal.html");
});

function loadPartial(elementId, filePath) {
  const element = document.getElementById(elementId);

  if (!element) return Promise.resolve();

  return fetch(filePath)
    .then(response => response.text())
    .then(html => {
      element.innerHTML = html;
    })
    .catch(error => console.error("Partial could not be loaded:", error));
}

document.addEventListener("DOMContentLoaded", async () => {
  const filtersContainer = document.getElementById("car-filters-placeholder");
  if (!filtersContainer) return;

  const response = await fetch("/partials/car-filters.html");
  const html = await response.text();

  filtersContainer.innerHTML = html;

  if (typeof initCarFilters === "function") {
    initCarFilters();
    markSelectedFilters();
  }
});

document.addEventListener("DOMContentLoaded", async function () {
  await loadPartial("preloader-placeholder", "partials/preloader.html");

  setTimeout(function () {
    const preloader = document.querySelector(".js-preloader");

    if (!preloader) return;

    preloader.style.opacity = "0";
    preloader.style.visibility = "hidden";
    preloader.style.pointerEvents = "none";

    setTimeout(function () {
      preloader.remove();
    }, 500);
  }, 800);
});