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
    const phoneCountryCodes = document.querySelectorAll('[data-ua-phone-country-code]');
    phoneCountryCodes.forEach(el => {
      if(el.tagName==='SELECT' || el.tagName==='INPUT'){
        try{ el.value = user.phoneCountryCode || ''; }catch(e){}
      }
    });
    const phoneNumbers = document.querySelectorAll('[data-ua-phone-number]');
    phoneNumbers.forEach(el => {
      if(el.tagName==='INPUT') el.value = user.phoneNumber || '';
      else el.textContent = user.phoneNumber || '';
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

  // If user is admin or super admin, ensure admin link is visible in header (UX only)
  try{
    if (backendUser && (backendUser.role === 'ADMIN' || backendUser.role === 'SUPER_ADMIN')){
      // Find a stable container near header auth links
      var signupLink = document.querySelector('.rc-header-auth-link');
      if (signupLink) {
        var adminA = document.createElement('a');
        adminA.href = '/admin/bookings.html';
        adminA.className = 'rc-header-admin-link';
        adminA.textContent = 'Admin';
        adminA.style.marginLeft = '12px';
        adminA.style.color = 'var(--rc-amber)';
        signupLink.parentNode.insertBefore(adminA, signupLink.nextSibling);
      }
    }
  }catch(e){/* ignore UI enhancement errors */}

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

    // set active tab
    try{
      const p = window.location.pathname || '/';
      const tabProfile = document.getElementById('tab-profile');
      const tabBookings = document.getElementById('tab-bookings');
      if (p.startsWith('/profile')){
        if(tabProfile) tabProfile.classList.add('is-active-tab');
      } else if (p.startsWith('/bookings')){
        if(tabBookings) tabBookings.classList.add('is-active-tab');
      }
    }catch(e){}
  });
})();
