(function () {
  'use strict';

  const STORAGE_KEY = 'rentcar-lang';
  const DEFAULT_LANG = 'en';

  window.i18nTranslations = window.i18nTranslations || {};

  function getLanguage() {
    return localStorage.getItem(STORAGE_KEY) || DEFAULT_LANG;
  }

  function t(key, params) {
    var lang = getLanguage();
    var dict = window.i18nTranslations[lang] || window.i18nTranslations[DEFAULT_LANG] || {};
    var text = Object.prototype.hasOwnProperty.call(dict, key) ? dict[key] : key;
    if (params) {
      Object.keys(params).forEach(function (k) {
        text = text.replace(new RegExp('{' + k + '}', 'g'), params[k]);
      });
    }
    return text;
  }

  function applyTranslations(root) {
    root = root || document;
    root.querySelectorAll('[data-i18n]').forEach(function (el) {
      el.textContent = t(el.getAttribute('data-i18n'));
    });
    root.querySelectorAll('[data-i18n-placeholder]').forEach(function (el) {
      el.placeholder = t(el.getAttribute('data-i18n-placeholder'));
    });
    root.querySelectorAll('[data-i18n-html]').forEach(function (el) {
      el.innerHTML = t(el.getAttribute('data-i18n-html'));
    });
  }

  function updateLanguageButtons(lang) {
    var label = lang === 'es' ? t('header.spanish') : t('header.english');
    document.querySelectorAll('.js-language-mainTitle').forEach(function (el) {
      el.textContent = label;
    });
    // Also update review.html's plain span (no js-language-mainTitle class)
    document.querySelectorAll('[data-i18n-lang-label]').forEach(function (el) {
      el.textContent = label;
    });
    // Highlight active item in language modal
    document.querySelectorAll('.langMenu__item[data-lang]').forEach(function (el) {
      el.classList.toggle('is-active', el.getAttribute('data-lang') === lang);
    });
  }

  function setLanguage(lang) {
    localStorage.setItem(STORAGE_KEY, lang);
    updateLanguageButtons(lang);
    applyTranslations(document);
    document.dispatchEvent(new CustomEvent('languageChanged', { detail: { lang: lang } }));
  }

  // Wire up language-modal item clicks (delegated, works after partial loads)
  document.addEventListener('click', function (e) {
    var item = e.target.closest('[data-lang]');
    if (item && item.classList.contains('langMenu__item')) {
      setLanguage(item.getAttribute('data-lang'));
      // Close the modal
      var modal = item.closest('[data-x="lang"]');
      if (modal) modal.classList.add('is-hidden');
    }
  });

  // On page load: apply translations + update button label
  document.addEventListener('DOMContentLoaded', function () {
    applyTranslations(document);
    updateLanguageButtons(getLanguage());
  });

  window.t = t;
  window.setLanguage = setLanguage;
  window.getLanguage = getLanguage;
  window.applyTranslations = applyTranslations;
}());
