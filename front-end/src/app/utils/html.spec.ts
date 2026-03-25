import { describe, expect, it } from 'vitest';

import { renderMarkup } from './html';

describe('renderMarkup', () => {
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
});