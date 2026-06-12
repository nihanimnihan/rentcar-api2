(function () {
  async function fetchUser() {
    try {
      const res = await fetch('/api/auth/me');
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
    if (!user) {
      try { user = JSON.parse(localStorage.getItem("rentcarUser")); } catch (_) { user = null; }
    }

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