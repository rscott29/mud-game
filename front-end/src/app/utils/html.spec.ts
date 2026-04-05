import { describe, expect, it } from 'vitest';

import { renderMarkup } from './html';

describe('renderMarkup', () => {
  it('preserves safe class wrappers', () => {
    const html = renderMarkup(
      "<div class='quest-available'><strong>Quest</strong></div>"
    );

    expect(html).toContain('<div class="quest-available"><strong>Quest</strong></div>');
  });

  it('preserves multi-class span wrappers for status effects', () => {
    const html = renderMarkup(
      "<span class='term-effect term-effect--negative'>Bitter Numbness</span>"
    );

    expect(html).toContain('<span class="term-effect term-effect--negative">Bitter Numbness</span>');
  });

  it('preserves safe width styles for combat status fills', () => {
    const html = renderMarkup(
      "<span class='combat-status-fill' style='width: 6%'></span>"
    );

    expect(html).toContain('<span class="combat-status-fill" style="width: 6%"></span>');
  });

  it('drops non-percentage width styles', () => {
    const html = renderMarkup(
      "<span class='combat-status-fill' style='width: calc(100% - 4px)'></span>"
    );

    expect(html).toContain('<span class="combat-status-fill"></span>');
    expect(html).not.toContain('calc(100% - 4px)');
  });

  it('does not allow attribute injection through class markup', () => {
    const html = renderMarkup(
      `<div class='combat-status-fill" onclick="alert(1)'>trap</div>`
    );

    expect(html).not.toContain('<div class="combat-status-fill" onclick="alert(1)">');
    expect(html).toContain('&lt;div class=&#39;combat-status-fill&quot; onclick=&quot;alert(1)&#39;&gt;trap</div>');
  });

  it('collapses double-escaped quote entities from already-rendered fragments', () => {
    const once = renderMarkup(
      `<span class='term-inline-event__text'><strong>"Obi's ball"</strong></span>`
    );
    const twice = renderMarkup(once);

    expect(twice).toContain('&quot;Obi&#39;s ball&quot;');
    expect(twice).not.toContain('&amp;quot;');
    expect(twice).not.toContain('&amp;#39;');
  });
});
