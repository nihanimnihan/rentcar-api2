(function () {
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
      'a[href="signup.html"][data-i18n="header.signIn"]'
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
      if (btn) btn.addEventListener('click', () => wrapper.classList.toggle('is-open'));
    });
  }

  document.addEventListener("DOMContentLoaded", renderAuthState);
})();