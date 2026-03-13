/**
 * Escapes HTML special characters to prevent XSS.
 */
export function escapeHtml(str: string): string {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/**
 * Regex patterns for re-opening safe HTML tags after escaping.
 */
const SIMPLE_TAG_RE = /&lt;(\/?(?:em|i|b|strong|br|ul|ol|li)\s*\/?)&gt;/g;
const DIV_SPAN_OPEN_RE = /&lt;(div|span)\s+class='([^']*?)'\s*&gt;/g;
const DIV_SPAN_CLOSE_RE = /&lt;\/(div|span)&gt;/g;

/**
 * Escapes the string for safe HTML insertion, then re-opens a curated
 * allowlist of presentational tags so game content can use light markup.
 * 
 * Allows: em, i, b, strong, br, ul, ol, li, div, span (with class attributes)
 */
export function renderMarkup(str: string): string {
  return escapeHtml(str)
    .replace(SIMPLE_TAG_RE, '<$1>')
    .replace(DIV_SPAN_OPEN_RE, '<$1 class="$2">')
    .replace(DIV_SPAN_CLOSE_RE, '</$1>');
}
