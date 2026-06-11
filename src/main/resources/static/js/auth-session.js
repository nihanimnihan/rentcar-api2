(function () {
  function getUser() {
    try {
      return JSON.parse(localStorage.getItem("rentcarUser"));
    } catch (_) {
      return null;
    }
  }

  function logout() {
    localStorage.removeItem("rentcarUser");
    window.location.href = "index.html";
  }

  function renderAuthState() {
    const user = getUser();
    const links = document.querySelectorAll(
      'a[href="signup.html"][data-i18n="header.signIn"]'
    );

    links.forEach(link => {
      if (!user || !user.firstName) return;

      const wrapper = document.createElement("div");
      wrapper.className = "rc-user-menu";
      wrapper.innerHTML = `
        <button type="button" class="rc-user-menu__button">
          <i class="icon-user rc-user-menu__avatar"></i>
          <span>${user.firstName}</span>
        </button>
        <div class="rc-user-menu__dropdown">
          <a href="manage-booking.html">My bookings</a>
          <a href="signup.html">Personal details</a>
          <button type="button" data-logout>Logout</button>
        </div>
      `;

      link.replaceWith(wrapper);

      wrapper.querySelector("[data-logout]").addEventListener("click", logout);
      wrapper.querySelector(".rc-user-menu__button").addEventListener("click", () => {
        wrapper.classList.toggle("is-open");
      });
    });
  }

  document.addEventListener("DOMContentLoaded", renderAuthState);
})();