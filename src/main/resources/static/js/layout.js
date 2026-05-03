document.addEventListener("DOMContentLoaded", function () {
  fetch("partials/footer.html")
    .then(response => response.text())
    .then(html => {
      document.getElementById("footer-placeholder").innerHTML = html;
    })
    .catch(error => console.error("Footer could not be loaded:", error));
});

document.addEventListener("DOMContentLoaded", function () {
  loadPartial("language-modal-placeholder", "partials/language-modal.html");
});

function loadPartial(elementId, filePath) {
  const element = document.getElementById(elementId);

  if (!element) return;

  fetch(filePath)
    .then(response => response.text())
    .then(html => {
      element.innerHTML = html;
    })
    .catch(error => console.error("Partial could not be loaded:", error));
}