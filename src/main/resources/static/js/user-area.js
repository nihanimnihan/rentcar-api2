// user-area.js — auth guard and profile/bookings population
(async function(){
  async function fetchMe(){
    try{
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if(!res.ok) return null;
      const data = await res.json();
      if(!data || data.authenticated === false || !data.email) return null;
      return data;
    }catch(e){
      return null;
    }
  }

  function redirectToSignup(){
    const returnTo = window.location.pathname + window.location.search;
    window.location.href = '/signup.html?returnTo=' + encodeURIComponent(returnTo);
  }

  function populateUserFields(user){
    const firsts = document.querySelectorAll('[data-ua-firstName]');
    firsts.forEach(el => { if(el.tagName==='INPUT') el.value = user.firstName || ''; else el.textContent = user.firstName || ''; });
    const lasts = document.querySelectorAll('[data-ua-lastName]');
    lasts.forEach(el => { if(el.tagName==='INPUT') el.value = user.lastName || ''; else el.textContent = user.lastName || ''; });
    const emails = document.querySelectorAll('[data-ua-email]');
    emails.forEach(el => { if(el.tagName==='INPUT') el.value = user.email || ''; else el.textContent = user.email || ''; });
    const countries = document.querySelectorAll('[data-ua-country]');
    countries.forEach(el => {
      if(el.tagName==='SELECT' || el.tagName==='INPUT'){
        try{ el.value = user.country || ''; }catch(e){}
      }
    });
    const custEls = document.querySelectorAll('[data-ua-customerNumber]');
    custEls.forEach(el => { el.textContent = user.customerNumber || '—'; });

    const nameSpans = document.querySelectorAll('#rc-user-name, .rc-user-menu__button span');
    nameSpans.forEach(s => { s.textContent = user.firstName || ''; });
  }

  function setupDropdown(backendUser){
    const menu = document.querySelector('.rc-user-menu');
    if(!menu) return;
    const dropdown = menu.querySelector('.rc-user-menu__dropdown');
    if(!dropdown) return;
    // Replace dropdown content with the correct links for logged-in users
    dropdown.innerHTML = `
      <a href="/profile.html">Profile</a>
      <a href="/bookings.html">My bookings</a>
      <button data-ua-logout class="rc-logout">Logout</button>
    `;

    // Bind logout
    const logoutBtn = dropdown.querySelector('[data-ua-logout]');
    if(logoutBtn){
      logoutBtn.addEventListener('click', async function(){
        try{ await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }); }catch(e){}
        localStorage.removeItem('rentcarUser');
        // dark overlay to avoid white flash
        try{
          var overlay = document.getElementById('rc-dark-overlay');
          if(!overlay){ overlay = document.createElement('div'); overlay.id='rc-dark-overlay'; overlay.style.position='fixed'; overlay.style.top='0'; overlay.style.left='0'; overlay.style.right='0'; overlay.style.bottom='0'; overlay.style.background='#191B1E'; overlay.style.zIndex='2147483647'; overlay.style.pointerEvents='none'; document.documentElement.appendChild(overlay); }
        }catch(e){}
        window.location.href = '/index.html';
      });
    }

    const btn = menu.querySelector('.rc-user-menu__button');
    if(btn){ btn.addEventListener('click', ()=> menu.classList.toggle('is-open')); }
  }

  // Run guard and populate
  document.addEventListener('DOMContentLoaded', async function(){
    const me = await fetchMe();
    if(!me){ return redirectToSignup(); }
    populateUserFields(me);
    setupDropdown(me);
  });
})();
