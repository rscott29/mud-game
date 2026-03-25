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
const SIMPLE_TAG_RE = /&lt;(\/?(?:em|i|b|strong|br|ul|ol|li|small|p|code|pre|section|article|header|footer|hr|h1|h2|h3|h4)\s*\/?)&gt;/g;
const CLASS_OPEN_SINGLE_RE = /&lt;(div|span|section|article|header|footer|p)\s+class='([^']*?)'\s*&gt;/g;
const CLASS_OPEN_DOUBLE_RE = /&lt;(div|span|section|article|header|footer|p)\s+class="([^"]*?)"\s*&gt;/g;
const CLASS_STYLE_WIDTH_OPEN_SINGLE_RE = /&lt;(div|span)\s+class='([^']*?)'\s+style='width:\s*([^']*?)'\s*&gt;/g;
const CLASS_STYLE_WIDTH_OPEN_DOUBLE_RE = /&lt;(div|span)\s+class="([^"]*?)"\s+style="width:\s*([^"]*?)"\s*&gt;/g;
const STYLE_WIDTH_CLASS_OPEN_SINGLE_RE = /&lt;(div|span)\s+style='width:\s*([^']*?)'\s+class='([^']*?)'\s*&gt;/g;
const STYLE_WIDTH_CLASS_OPEN_DOUBLE_RE = /&lt;(div|span)\s+style="width:\s*([^"]*?)"\s+class="([^"]*?)"\s*&gt;/g;
const CLASS_CLOSE_RE = /&lt;\/(div|span|section|article|header|footer|p)&gt;/g;

/**
 * Escapes the string for safe HTML insertion, then re-opens a curated
 * allowlist of presentational tags so game content can use light markup.
 * 
 * Allows semantic presentation tags plus div/span-style wrappers with class attributes.
 */
export function renderMarkup(str: string): string {
  return escapeHtml(str)
    .replace(SIMPLE_TAG_RE, '<$1>')
    .replace(CLASS_STYLE_WIDTH_OPEN_SINGLE_RE, (_, tag: string, className: string, width: string) =>
      renderClassAndWidth(tag, className, width)
    )
    .replace(CLASS_STYLE_WIDTH_OPEN_DOUBLE_RE, (_, tag: string, className: string, width: string) =>
      renderClassAndWidth(tag, className, width)
    )
    .replace(STYLE_WIDTH_CLASS_OPEN_SINGLE_RE, (_, tag: string, width: string, className: string) =>
      renderClassAndWidth(tag, className, width)
    )
    .replace(STYLE_WIDTH_CLASS_OPEN_DOUBLE_RE, (_, tag: string, width: string, className: string) =>
      renderClassAndWidth(tag, className, width)
    )
    .replace(CLASS_OPEN_SINGLE_RE, '<$1 class="$2">')
    .replace(CLASS_OPEN_DOUBLE_RE, '<$1 class="$2">')
    .replace(CLASS_CLOSE_RE, '</$1>');
}

function renderClassAndWidth(tag: string, className: string, width: string): string {
  const safeWidth = sanitizeWidthPercentage(width);
  if (safeWidth == null) {
    return `<${tag} class="${className}">`;
  }
  return `<${tag} class="${className}" style="width: ${safeWidth}%">`;
}

function sanitizeWidthPercentage(value: string): number | null {
  if (!/^\d{1,3}%?$/.test(value.trim())) {
    return null;
  }

  const numericValue = Number.parseInt(value, 10);
  if (!Number.isFinite(numericValue)) {
    return null;
  }

  return Math.max(0, Math.min(100, numericValue));
}
