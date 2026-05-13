/**
 * escape-html.js — shared XSS escaping utilities
 *
 * Must be loaded BEFORE any JS file that builds HTML strings from API data.
 *
 * Two functions are exposed globally:
 *
 *   escapeHtml(str)          — safe for HTML text content AND attribute values
 *   safeSrc(url, fallback)   — safe URL for src/href attributes; blocks javascript: URIs
 */

/**
 * Escapes a string for safe insertion into HTML text content or attribute values.
 *
 * Covers: & < > " '
 * Note: the naive createTextNode/div.innerHTML approach does NOT escape double-quotes,
 * making it unsafe in attribute context (e.g. alt="${value}"). This implementation
 * is safe in both contexts.
 */
function escapeHtml(str) {
  if (str == null) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#x27;");
}

/**
 * Returns a URL safe for use in src/href attributes.
 *
 * Rejects javascript: URIs (protocol-relative or whitespace-padded variants included),
 * then HTML-escapes the result to prevent attribute injection.
 * Falls back to `fallback` when the URL is falsy or blocked.
 */
function safeSrc(url, fallback) {
  if (!url) return fallback || "";
  const s = String(url);
  // Block javascript: URIs (all case/whitespace variants)
  if (/^\s*javascript:/i.test(s)) return fallback || "";
  // Block data: URIs that are not safe images (e.g. data:text/html is an XSS vector)
  if (/^\s*data:/i.test(s) && !/^\s*data:image\/(png|jpe?g|gif|webp|svg\+xml|avif)/i.test(s)) return fallback || "";
  return escapeHtml(s);
}
