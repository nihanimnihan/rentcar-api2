Frontend Google OAuth click tests

This file describes manual frontend tests to verify the Google OAuth click fix.

Prerequisites
- Run the app and open the site in Chrome
- Open DevTools → Network and click Clear

Tests

1) Default returnTo
- Open: /signup.html
- Click: "Continue with Google"
- Expected: The first network request (or navigation target) is:
  /oauth2/authorize?returnTo=%2Findex.html&provider=google

2) Preserved query params
- Open: /signup.html?returnTo=%2Fcars.html%3FpickupLocation%3DBCN
- Click: "Continue with Google"
- Expected: Request contains encoded returnTo with query params preserved, e.g.:
  /oauth2/authorize?returnTo=%2Fcars.html%3FpickupLocation%3DBCN&provider=google

3) Missing optional elements
- Remove or hide optional elements (simulate by editing DOM in DevTools) such as #firstName, #lastName, #country or the verify/profile steps
- Reload /signup.html
- Click: "Continue with Google"
- Expected: No uncaught exceptions in console; request to /oauth2/authorize is made (see test 1)

4) Unsafe returnTo falls back to /index.html
- Open: /signup.html?returnTo=//evil.com  (or other examples: https://evil.com, javascript:alert(1))
- Click: "Continue with Google"
- Expected: Redirect/request uses /index.html as returnTo (encoded) and does not use the unsafe value.

Manual verification tips
- If navigation occurs quickly, look for the first request in Network or reproduce by setting a breakpoint in auth.js (goProfileFromGoogle) and stepping.
- No noisy console.log should appear from auth.js. Only real errors may be logged.

Notes
- The JS file src/main/resources/static/js/auth.js was hardened so missing DOM elements do not cause failures that prevent the Google button from binding.
