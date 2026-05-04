document.addEventListener("DOMContentLoaded", function () {
  initCarFilters();
  markSelectedFilters();
});

function initCarFilters() {
  document.querySelectorAll(".js-car-filter").forEach(filter => {
    filter.addEventListener("change", function () {
      const params = new URLSearchParams(window.location.search);
      const filterName = this.dataset.filter;

      params.delete(filterName);

      document
        .querySelectorAll(`.js-car-filter[data-filter="${filterName}"]:checked`)
        .forEach(selected => {
          params.append(filterName, selected.value);
        });

      const newUrl = "/cars.html?" + params.toString();

      // URL değişsin ama sayfa refresh olmasın
      window.history.pushState({}, "", newUrl);

      // Arabaları tekrar yükle
      if (typeof loadCars === "function") {
        loadCars();
      }
    });
  });
}

function markSelectedFilters() {
  const params = new URLSearchParams(window.location.search);

  document.querySelectorAll(".js-car-filter").forEach(filter => {
    const values = params.getAll(filter.dataset.filter);

    if (values.includes(filter.value)) {
      filter.checked = true;
    }
  });
}