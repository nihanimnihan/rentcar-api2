(function () {
  function closeHeaderDropdowns(except) {
    if (except !== "user") {
      document.querySelectorAll(".rc-user-menu.is-open").forEach(function (menu) {
        menu.classList.remove("is-open");
      });
    }

    if (except !== "lang") {
      document.querySelectorAll(".js-lang-menu.is-active, .js-lang-menu.is-open").forEach(function (menu) {
        menu.classList.remove("is-active");
        menu.classList.remove("is-open");
      });
    }
  }
  async function fetchUser() {
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (!res.ok) return null;
      const data = await res.json();
      if (data && data.authenticated === false) return null;
      return data;
    } catch (e) {
      return null;
    }
  }

  async function logout() {
    try {
      const res = await fetch('/api/auth/logout', { method: 'POST' });
      if (!res.ok) {
        console.error('Logout request failed', res.status, await res.text());
      }
    } catch (e) {
      console.error('Logout request error', e);
    }
    // Clear local state
    localStorage.removeItem("rentcarUser");

    // Prevent white flash during navigation: show a dark fullscreen overlay
    try {
      var overlay = document.getElementById('rc-dark-overlay');
      if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'rc-dark-overlay';
        overlay.style.position = 'fixed';
        overlay.style.top = '0';
        overlay.style.left = '0';
        overlay.style.right = '0';
        overlay.style.bottom = '0';
        overlay.style.background = '#191B1E';
        overlay.style.zIndex = '2147483647';
        overlay.style.pointerEvents = 'none';
        document.documentElement.appendChild(overlay);
      }
    } catch (e) {
      // ignore overlay failures
    }

    // Navigate to homepage
    window.location.href = "index.html";
  }

  async function renderAuthState() {
    const links = document.querySelectorAll(
      'a[href="signup.html"], a[href="/signup.html"]'
    );

    const backendUser = await fetchUser();
    let user = backendUser;
    // Do not use localStorage as source-of-truth for authentication. Only allow it as a visual fallback.
    if (!user) {
      try { const ls = JSON.parse(localStorage.getItem("rentcarUser")); if (ls && ls.firstName && ls.email) user = ls; } catch (_) { user = null; }
    }

    links.forEach(link => {
      // require a backend-authenticated user with email — otherwise don't render account menu
      if (!backendUser || !backendUser.email) return;

      const wrapper = document.createElement("div");
      wrapper.className = "rc-user-menu";
      wrapper.innerHTML = `
        <button type=\"button\" class=\"rc-user-menu__button\"> 
          <span>${backendUser.firstName || ''}</span>
        </button>
        <div class=\"rc-user-menu__dropdown\">
          <a href=\"/profile.html\">Profile</a>
          <a href=\"/bookings.html\">My bookings</a>
          <button type=\"button\" data-logout>Logout</button>
        </div>
      `;

      link.replaceWith(wrapper);

      const logoutBtn = wrapper.querySelector("[data-logout]");
      if (logoutBtn) logoutBtn.addEventListener("click", logout);
      const btn = wrapper.querySelector('.rc-user-menu__button');
      if (btn) {
        btn.addEventListener("click", function (e) {
          e.preventDefault();
          e.stopPropagation();

          const willOpen = !wrapper.classList.contains("is-open");
          closeHeaderDropdowns("user");
          wrapper.classList.toggle("is-open", willOpen);
        });
      }
    });
  }

  document.addEventListener("click", function () {
    closeHeaderDropdowns();
  });

  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") closeHeaderDropdowns();
  });

  document.addEventListener("DOMContentLoaded", renderAuthState);
})();