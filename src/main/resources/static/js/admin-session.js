document.querySelectorAll('.admin-db__nav-link--logout').forEach((link) => {
  link.addEventListener('click', async (event) => {
    event.preventDefault();
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'same-origin',
      });
    } finally {
      window.location.href = '/admin-login.html?logout=true';
    }
  });
});
