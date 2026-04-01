import { Injectable, inject } from '@angular/core';

import { TERMINAL_MESSAGE_CLASSES, type TerminalMessageClass } from '../models/game-message';
import { escapeHtml } from '../utils/html';
import { CommandCatalogService } from './command-catalog.service';
import { type FormattedMessage } from './message-format.types';
import { SkillDto } from './skill-progression.service';

@Injectable({ providedIn: 'root' })
export class TerminalReferenceFormatterService {
  private readonly commandCatalog = inject(CommandCatalogService);

  constructor() {
    this.commandCatalog.load();
  }

  formatHelpCard(isGod: boolean): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.HELP,
      html: this.renderHelp(isGod),
    };
  }

  formatClassProgressionLoading(characterClass: string): FormattedMessage {
    return this.notice(
      TERMINAL_MESSAGE_CLASSES.CLASS_PROGRESSION,
      'Unfurling the skill ledger',
      `Drawing the ${escapeHtml(this.formatClassName(characterClass))} arts into the ledger...`
    );
  }

  formatClassProgressionError(characterClass: string): FormattedMessage {
    return this.notice(
      TERMINAL_MESSAGE_CLASSES.ERROR,
      'The skill ledger is quiet',
      `The ${escapeHtml(this.formatClassName(characterClass))} arts could not be drawn into view just now.`
    );
  }

  formatClassProgression(
    characterClass: string,
    playerLevel: number,
    maxLevel: number,
    skills: SkillDto[]
  ): FormattedMessage {
    const unlocked = skills
      .filter(skill => skill.unlockLevel <= playerLevel)
      .sort((left, right) => left.unlockLevel - right.unlockLevel || left.name.localeCompare(right.name));
    const locked = skills
      .filter(skill => skill.unlockLevel > playerLevel)
      .sort((left, right) => left.unlockLevel - right.unlockLevel || left.name.localeCompare(right.name));
    const progressPercent = skills.length === 0 ? 0 : Math.round((unlocked.length / skills.length) * 100);

    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.CLASS_PROGRESSION,
      html: `
        <section class="term-card term-card--progression">
          <div class="term-card__header">
            <div>
              <div class="term-card__eyebrow">Path of mastery</div>
              <h2 class="term-card__title">${escapeHtml(this.formatClassName(characterClass))} arts</h2>
            </div>
            <span class="term-badge">${escapeHtml(String(unlocked.length))}/${escapeHtml(String(skills.length))} unlocked</span>
          </div>
          <div class="term-progress">
            <div class="term-progress__bar">
              <div class="term-progress__fill" style="width: ${progressPercent}%"></div>
            </div>
            <div class="term-progress__meta">
              <span>Level ${escapeHtml(String(playerLevel))}${maxLevel > 0 ? ` / ${escapeHtml(String(maxLevel))}` : ''}</span>
              <span>${escapeHtml(String(progressPercent))}% awakened</span>
            </div>
          </div>
          ${skills.length === 0 ? `
            <div class="term-empty">No skills found for this class.</div>
          ` : `
            <div class="term-columns">
              <section class="term-section">
                <div class="term-section__title">Awakened</div>
                ${this.formatSkillList(unlocked, playerLevel, false)}
              </section>
              <section class="term-section">
                <div class="term-section__title">Awaiting</div>
                ${this.formatSkillList(locked, playerLevel, true)}
              </section>
            </div>
          `}
        </section>
      `,
    };
  }

  private renderHelp(isGod: boolean): string {
    const categories = this.commandCatalog.helpCategories(isGod);
    const tips = this.commandCatalog.helpTips();

    return `
      <section class="term-card term-card--help">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">Field notes</div>
            <h2 class="term-card__title">Traveler's handbook</h2>
          </div>
          <span class="term-badge">${isGod ? "warden's sight" : "traveler's sight"}</span>
        </div>
        <div class="term-help">
          <div class="term-help__body">
            ${categories.length === 0 ? `
              <div class="term-empty">Command reference is still loading.</div>
            ` : `
              <div class="term-columns">
                ${categories.map(category => `
                  <section class="term-section">
                    <div class="term-section__title">${escapeHtml(category.title)}</div>
                    <div class="term-kv-list">
                      ${category.entries.map(entry => `
                        <div class="term-kv">
                          <div class="term-kv__key">${escapeHtml(entry.cmd)}</div>
                          <div class="term-kv__value">${escapeHtml(entry.desc)}</div>
                          ${entry.aliasesText ? `<div class="term-copy term-copy--muted">Also: ${escapeHtml(entry.aliasesText)}</div>` : ''}
                          ${entry.example ? `<div class="term-copy term-copy--muted">Try: ${escapeHtml(entry.example)}</div>` : ''}
                        </div>
                      `).join('')}
                    </div>
                  </section>
                `).join('')}
              </div>
            `}
          </div>
          <div class="term-footnote term-help__footer">
            Natural language still works. Plain speech is welcome when a formal command feels clumsy.
            ${tips.length > 0 ? `<br>${escapeHtml(tips.join(' '))}` : ''}
          </div>
        </div>
      </section>
    `;
  }

  private formatSkillList(skills: SkillDto[], playerLevel: number, locked: boolean): string {
    if (skills.length === 0) {
      return `<div class="term-empty">${locked ? 'Everything in this column has already awakened.' : 'No arts have awakened yet.'}</div>`;
    }

    return `
      <ul class="term-list">
        ${skills.map(skill => `
          <li class="term-list__item${locked ? ' is-locked' : ''}">
            <div class="term-list__header">
              <span class="term-list__title">${escapeHtml(skill.name)}</span>
              <span class="term-pill">${locked ? `Level ${escapeHtml(String(skill.unlockLevel))}` : 'Awakened'}</span>
            </div>
            <div class="term-meta-row">
              <span class="term-label">Type</span>
              <span class="term-value">${escapeHtml(skill.type)}</span>
            </div>
            ${skill.passiveBonuses ? `
              <div class="term-bonus-list">
                ${this.formatBonuses(skill).map(bonus => `
                  <span class="term-bonus">${escapeHtml(bonus.label)} +${escapeHtml(String(bonus.value))}</span>
                `).join('')}
              </div>
            ` : `
              <div class="term-copy term-copy--muted">
                ${locked ? `Ripens in ${escapeHtml(String(skill.unlockLevel - playerLevel))} level${skill.unlockLevel - playerLevel === 1 ? '' : 's'}.` : 'No passive blessing is recorded here.'}
              </div>
            `}
          </li>
        `).join('')}
      </ul>
    `;
  }

  private formatBonuses(skill: SkillDto): Array<{ label: string; value: number }> {
    const bonuses = skill.passiveBonuses;
    if (!bonuses) {
      return [];
    }

    const rows: Array<{ label: string; value: number }> = [];
    if (bonuses.minDamageBonus) rows.push({ label: 'Min damage', value: bonuses.minDamageBonus });
    if (bonuses.maxDamageBonus) rows.push({ label: 'Max damage', value: bonuses.maxDamageBonus });
    if (bonuses.hitChanceBonus) rows.push({ label: 'Hit chance', value: bonuses.hitChanceBonus });
    if (bonuses.armorBonus) rows.push({ label: 'Armor', value: bonuses.armorBonus });
    return rows;
  }

  private notice(cssClass: TerminalMessageClass, title: string, body: string): FormattedMessage {
    return {
      cssClass,
      html: `
        <section class="term-card term-card--notice">
          <div class="term-card__header">
            <div>
              <div class="term-card__eyebrow">Lantern</div>
              <h2 class="term-card__title">${escapeHtml(title)}</h2>
            </div>
          </div>
          <div class="term-copy">${body}</div>
        </section>
      `,
    };
  }

  private formatClassName(value: string): string {
    return this.toTitleCase(value.replace(/[_-]+/g, ' '));
  }

  private toTitleCase(value: string): string {
    return value
      .split(/\s+/)
      .filter(Boolean)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }
}
