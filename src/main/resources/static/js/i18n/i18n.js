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

  // Reads stored language directly from localStorage and updates every
  // [data-current-language-label] element.  Safe to call at any time,
  // including right after a partial is injected into the DOM.
  function updateCurrentLanguageLabels() {
    var lang  = localStorage.getItem(STORAGE_KEY) || DEFAULT_LANG;
    var label = lang === 'es' ? 'Español' : 'English';
    document.querySelectorAll('[data-current-language-label]').forEach(function (el) {
      el.textContent = label;
    });
  }

  function setLanguage(lang) {
    localStorage.setItem(STORAGE_KEY, lang);
    updateLanguageButtons(lang);
    updateCurrentLanguageLabels();
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
    updateCurrentLanguageLabels();
  });

  window.t = t;
  window.setLanguage = setLanguage;
  window.getLanguage = getLanguage;
  window.applyTranslations = applyTranslations;
  window.updateLanguageButtons = updateLanguageButtons;
  window.updateCurrentLanguageLabels = updateCurrentLanguageLabels;

  // Translate enum/API values: tEnum('transmission', 'AUTOMATIC') → 'Automático'
  window.tEnum = function (type, value) {
    if (!value) return '';
    var key = 'enum.' + type + '.' + value;
    var result = t(key);
    // If key not found, fall back to the raw value (escaped)
    return result === key ? (typeof escapeHtml === 'function' ? escapeHtml(value) : value) : result;
  };

  // Translate addon name from the addon object returned by API
  // Falls back to English name if no Spanish translation is stored in DB
  window.localAddonName = function (addon) {
    if (!addon) return '';
    if (getLanguage() === 'es') return addon.nameEs || addon.name || '';
    return addon.name || '';
  };

  // Translate addon description from the addon object
  window.localAddonDesc = function (addon) {
    if (!addon) return '';
    if (getLanguage() === 'es') return addon.descriptionEs || addon.description || '';
    return addon.description || '';
  };

  // Legacy code-based helpers kept as no-ops that fall back to DB values
  // (no longer needed but kept to avoid errors if called from old cached pages)
  window.tAddonName = function (code, fallback) { return fallback || code || ''; };
  window.tAddonDesc = function (code, fallback) { return fallback || ''; };
}());
