/**
 * Escapes HTML special characters to prevent XSS.
 */
export function escapeHtml(str: string): string {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Regex patterns for re-opening safe HTML tags after escaping.
 */
const SIMPLE_TAG_RE = /&lt;(\/?(?:em|i|b|strong|br|ul|ol|li|small|p|code|pre|section|article|header|footer|hr|h1|h2|h3|h4)\s*\/?)&gt;/g;
const CLASS_OPEN_SINGLE_RE = /&lt;(div|span|section|article|header|footer|p)\s+class=&#39;([^&<>]*?)&#39;\s*&gt;/g;
const CLASS_OPEN_DOUBLE_RE = /&lt;(div|span|section|article|header|footer|p)\s+class=&quot;([^&<>]*?)&quot;\s*&gt;/g;
const CLASS_STYLE_WIDTH_OPEN_SINGLE_RE = /&lt;(div|span)\s+class=&#39;([^&<>]*?)&#39;\s+style=&#39;width:\s*([^&<>]*?)&#39;\s*&gt;/g;
const CLASS_STYLE_WIDTH_OPEN_DOUBLE_RE = /&lt;(div|span)\s+class=&quot;([^&<>]*?)&quot;\s+style=&quot;width:\s*([^&<>]*?)&quot;\s*&gt;/g;
const STYLE_WIDTH_CLASS_OPEN_SINGLE_RE = /&lt;(div|span)\s+style=&#39;width:\s*([^&<>]*?)&#39;\s+class=&#39;([^&<>]*?)&#39;\s*&gt;/g;
const STYLE_WIDTH_CLASS_OPEN_DOUBLE_RE = /&lt;(div|span)\s+style=&quot;width:\s*([^&<>]*?)&quot;\s+class=&quot;([^&<>]*?)&quot;\s*&gt;/g;
const CLASS_CLOSE_RE = /&lt;\/(div|span|section|article|header|footer|p)&gt;/g;
const SAFE_CLASS_TOKEN_RE = /^[A-Za-z0-9_-]+$/;

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
    .replace(CLASS_OPEN_SINGLE_RE, (_, tag: string, className: string) =>
      renderClassOnly(tag, className)
    )
    .replace(CLASS_OPEN_DOUBLE_RE, (_, tag: string, className: string) =>
      renderClassOnly(tag, className)
    )
    .replace(CLASS_CLOSE_RE, '</$1>');
}

function renderClassOnly(tag: string, className: string): string {
  const safeClassName = sanitizeClassNames(className);
  if (!safeClassName) {
    return `<${tag}>`;
  }
  return `<${tag} class="${safeClassName}">`;
}

function renderClassAndWidth(tag: string, className: string, width: string): string {
  const safeClassName = sanitizeClassNames(className);
  const safeWidth = sanitizeWidthPercentage(width);
  if (!safeClassName) {
    return `<${tag}>`;
  }
  if (safeWidth == null) {
    return `<${tag} class="${safeClassName}">`;
  }
  return `<${tag} class="${safeClassName}" style="width: ${safeWidth}%">`;
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

function sanitizeClassNames(value: string): string {
  return value
    .split(/\s+/)
    .map(token => token.trim())
    .filter(token => token !== '' && SAFE_CLASS_TOKEN_RE.test(token))
    .join(' ');
}
