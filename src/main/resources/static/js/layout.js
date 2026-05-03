document.addEventListener("DOMContentLoaded", function () {
  fetch("partials/footer.html")
    .then(response => response.text())
    .then(html => {
      document.getElementById("footer-placeholder").innerHTML = html;
    })
    .catch(error => console.error("Footer could not be loaded:", error));
});