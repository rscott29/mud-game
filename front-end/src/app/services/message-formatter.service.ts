import { Injectable, inject } from '@angular/core';

import {
  GAME_MESSAGE_TYPES,
  GameMessage,
  ItemDto,
  TERMINAL_MESSAGE_CLASSES,
  type TerminalMessageClass,
  WhoPlayerDto,
} from '../models/game-message';
import { escapeHtml, renderMarkup } from '../utils/html';
import { CommandCatalogService } from './command-catalog.service';
import { SkillDto } from './skill-progression.service';

export interface FormattedMessage {
  cssClass: TerminalMessageClass;
  html: string;
}

@Injectable({ providedIn: 'root' })
export class MessageFormatterService {
  private readonly commandCatalog = inject(CommandCatalogService);

  constructor() {
    this.commandCatalog.load();
  }

  formatSystem(text: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.SYSTEM,
      html: `
        <div class="term-inline term-inline--system">
          <span class="term-tag">realm</span>
          <span class="term-inline__text">${escapeHtml(text)}</span>
        </div>
      `,
    };
  }

  formatRoomDisplay(msg: GameMessage): FormattedMessage {
    return {
      cssClass: msg.type ?? GAME_MESSAGE_TYPES.ROOM_UPDATE,
      html: this.renderRoom(msg),
    };
  }

  formatAuthPrompt(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.AUTH_PROMPT,
      html: this.renderPrompt(message),
    };
  }

  formatChatMessage(msg: GameMessage): FormattedMessage {
    return {
      cssClass: msg.type ?? GAME_MESSAGE_TYPES.CHAT_ROOM,
      html: this.renderChat(msg),
    };
  }

  formatWhoList(players: WhoPlayerDto[]): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.WHO_LIST,
      html: this.renderWho(players),
    };
  }

  formatInventoryUpdate(items: ItemDto[]): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.INVENTORY_UPDATE,
      html: this.renderInventory(items),
    };
  }

  formatHelpCard(isGod: boolean): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.HELP,
      html: this.renderHelp(isGod),
    };
  }

  formatNarrativeInlineMessage(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.NARRATIVE,
      html: this.renderInlineNarrative(message),
    };
  }

  formatRoomActionMessage(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.ROOM_ACTION,
      html: this.wrapInlineNarrative(
        this.formatInlineEventContent('Traveler', message, 'term-inline-event--player-action')
      ),
    };
  }

  formatSocialActionMessage(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.SOCIAL_ACTION,
      html: this.wrapInlineNarrative(
        this.formatInlineEventContent('Social', message, 'term-inline-event--social-action')
      ),
    };
  }

  formatModerationNotice(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.MODERATION_NOTICE,
      html: `
        <section class="term-card term-card--moderation">
          <div class="term-card__header">
            <div>
              <div class="term-card__eyebrow">Moderation</div>
              <h2 class="term-card__title">Message withheld</h2>
            </div>
            <span class="term-badge term-badge--warning">broadcast blocked</span>
          </div>
          <div class="term-callout term-callout--moderation">${escapeHtml(message)}</div>
          <div class="term-copy term-copy--muted">
            Nothing was sent to the room, world chat, whispers, or emote feed.
          </div>
        </section>
      `,
    };
  }

  formatAmbientEventMessage(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.AMBIENT_EVENT,
      html: this.renderNarrative('Whispers', message),
    };
  }

  formatCompanionDialogueMessage(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.COMPANION_DIALOGUE,
      html: this.renderNarrative('Companion voice', message),
    };
  }

  formatFallbackMessage(label: TerminalMessageClass, message: string): FormattedMessage {
    return {
      cssClass: label,
      html: this.renderNarrative(label, message),
    };
  }

  formatRoomInlineFragment(msg: GameMessage): string {
    if ((msg.message ?? '').trim() === '') {
      return '';
    }

    switch (msg.type) {
      case GAME_MESSAGE_TYPES.ROOM_ACTION:
        return this.formatInlineEventContent(
          'Traveler',
          msg.message ?? '',
          'term-inline-event--player-action'
        );
      case GAME_MESSAGE_TYPES.SOCIAL_ACTION:
        return this.formatInlineEventContent(
          'Social',
          msg.message ?? '',
          'term-inline-event--social-action'
        );
      case GAME_MESSAGE_TYPES.AMBIENT_EVENT:
      case GAME_MESSAGE_TYPES.COMPANION_DIALOGUE:
        return renderMarkup(msg.message ?? '');
      default:
        return msg.message ?? '';
    }
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

  private renderPrompt(message: string): string {
    const authScreen = this.parseAuthWelcome(message);
    if (authScreen) {
      return `
        <section class="term-card term-card--prompt term-card--prompt-auth">
          <div class="term-auth-layout">
            <pre class="term-copy term-copy--preformatted term-auth-banner">${escapeHtml(authScreen.banner)}</pre>
            <div class="term-auth-sidebar">
              <div class="term-auth-kicker">Sign in</div>
              <div class="term-auth-prompt">${escapeHtml(authScreen.prompt)}</div>
              <div class="term-auth-note">New usernames will guide you through creating a character.</div>
            </div>
          </div>
        </section>
      `;
    }

    return `
      <section class="term-card term-card--prompt">
        <pre class="term-copy term-copy--preformatted">${this.formatPlainText(message)}</pre>
      </section>
    `;
  }

  private parseAuthWelcome(message: string): { banner: string; prompt: string } | null {
    const normalized = message.replace(/\r\n/g, '\n').trimEnd();
    const segments = normalized.split(/\n{2,}/).filter(segment => segment.trim() !== '');
    if (segments.length < 2) {
      return null;
    }

    const prompt = segments.at(-1)?.trim() ?? '';
    const banner = segments.slice(0, -1).join('\n\n').replace(/\n+$/, '');
    if (!banner || !prompt) {
      return null;
    }

    return { banner, prompt };
  }

  private renderNarrative(label: string, message: string): string {
    return `
      <section class="term-card term-card--narrative">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">${escapeHtml(label)}</div>
            <h2 class="term-card__title">Story thread</h2>
          </div>
        </div>
        <div class="term-copy">${renderMarkup(message)}</div>
      </section>
    `;
  }

  private renderInlineNarrative(message: string): string {
    return this.wrapInlineNarrative(renderMarkup(message));
  }

  private formatPlainText(message: string): string {
    return escapeHtml(message);
  }

  private wrapInlineNarrative(content: string): string {
    return `<div class="term-inline-narrative">${content}</div>`;
  }

  private formatInlineEventContent(label: string, message: string, variantClass: string): string {
    return `
      <span class="term-inline-event ${variantClass}">
        <span class="term-inline-event__label">${label}</span>
        <span class="term-inline-event__text">${message}</span>
      </span>
    `;
  }

  private renderChat(msg: GameMessage): string {
    const type = msg.type ?? GAME_MESSAGE_TYPES.CHAT_ROOM;
    const label =
      type === GAME_MESSAGE_TYPES.CHAT_ROOM ? 'hall' :
      type === GAME_MESSAGE_TYPES.CHAT_WORLD ? 'realm' :
      'whisper';

    return `
      <div class="term-inline term-inline--chat">
        <span class="term-tag term-tag--${label}">${escapeHtml(label)}</span>
        <span class="term-inline__from">${escapeHtml(msg.from ?? 'unknown')}</span>
        <span class="term-inline__text">${escapeHtml(msg.message ?? '')}</span>
      </div>
    `;
  }

  private renderRoom(msg: GameMessage): string {
    const room = msg.room;
    if (!room) {
      return this.renderNarrative(msg.type ?? GAME_MESSAGE_TYPES.NARRATIVE, msg.message ?? '');
    }

    const fullMessage = msg.message ?? '';
    const splitIndex = fullMessage.indexOf('<br><br>');
    const leadText = splitIndex === -1 ? fullMessage : fullMessage.slice(0, splitIndex);
    const trailingText = splitIndex === -1 ? '' : fullMessage.slice(splitIndex + 8);

    return `
      <section class="term-card term-card--room">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">${this.roomEyebrow(msg.type)}</div>
            <h2 class="term-card__title">${escapeHtml(room.name)}</h2>
          </div>
        </div>
        <div class="term-copy">${renderMarkup(room.description)}</div>
        <div class="term-columns">
          <section class="term-section">
            <div class="term-section__title">Paths</div>
            ${this.formatChipList(room.exits ?? [], 'term-chip--exit')}
          </section>
          <section class="term-section">
            <div class="term-section__title">Treasures</div>
            ${this.formatChipList((room.items ?? []).map(item => this.formatItemChip(item.name, item.rarity)), 'term-chip--item', true)}
          </section>
          <section class="term-section">
            <div class="term-section__title">Folk</div>
            ${this.formatChipList((room.npcs ?? []).map(npc => this.formatNpcChip(npc.name, npc.sentient)), 'term-chip--npc', true)}
          </section>
          <section class="term-section">
            <div class="term-section__title">Travelers</div>
            ${this.formatChipList(room.players ?? [], 'term-chip--player')}
          </section>
        </div>
        ${leadText ? `<div class="term-inline-narrative">${renderMarkup(leadText)}</div>` : ''}
        ${trailingText ? `<div class="term-inline-narrative">${renderMarkup(trailingText)}</div>` : ''}
      </section>
    `;
  }

  private renderInventory(items: ItemDto[]): string {
    return `
      <section class="term-card term-card--inventory">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">Satchel</div>
            <h2 class="term-card__title">Travel pack</h2>
          </div>
          <span class="term-badge">${escapeHtml(String(items.length))} item${items.length === 1 ? '' : 's'}</span>
        </div>
        ${items.length === 0 ? `
          <div class="term-empty">Your satchel is light.</div>
        ` : `
          <ul class="term-list">
            ${items.map(item => `
              <li class="term-list__item">
                <div class="term-list__header">
                  <span class="term-list__title">${escapeHtml(item.name)}</span>
                  <div class="term-list__badges">
                    <span class="term-pill term-pill--rarity term-pill--${escapeHtml(item.rarity)}">${escapeHtml(this.toTitleCase(item.rarity))}</span>
                    ${item.equipped ? '<span class="term-pill term-pill--equipped">Equipped</span>' : ''}
                  </div>
                </div>
                <div class="term-copy term-copy--muted">${renderMarkup(item.description ?? '')}</div>
              </li>
            `).join('')}
          </ul>
        `}
      </section>
    `;
  }

  private renderWho(players: WhoPlayerDto[]): string {
    return `
      <section class="term-card term-card--who">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">Lantern census</div>
            <h2 class="term-card__title">Across the realm</h2>
          </div>
          <span class="term-badge">${escapeHtml(String(players.length))} online</span>
        </div>
        ${players.length === 0 ? `
          <div class="term-empty">The roads are quiet.</div>
        ` : `
          <ul class="term-list">
            ${players.map(player => `
              <li class="term-list__item">
                <div class="term-list__header">
                  <span class="term-list__title">${escapeHtml(player.name)}</span>
                  <span class="term-pill">Level ${escapeHtml(String(player.level))}</span>
                </div>
                <div class="term-copy term-copy--muted">${escapeHtml(player.title)}</div>
                <div class="term-meta-row">
                  <span class="term-label">Location</span>
                  <span class="term-value">${escapeHtml(player.location)}</span>
                </div>
              </li>
            `).join('')}
          </ul>
        `}
      </section>
    `;
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

  private formatChipList(entries: string[], variant: string, entriesAreMarkup = false): string {
    if (entries.length === 0) {
      return '<div class="term-empty term-empty--inline">None</div>';
    }

    return `
      <div class="term-chip-grid">
        ${entries.map(entry => `
          <span class="term-chip ${variant}">
            ${entriesAreMarkup ? entry : escapeHtml(entry)}
          </span>
        `).join('')}
      </div>
    `;
  }

  private formatItemChip(name: string, rarity: string): string {
    return `
      <span class="term-chip__body">${escapeHtml(name)}</span>
      <span class="term-chip__meta">${escapeHtml(this.toTitleCase(rarity))}</span>
    `;
  }

  private formatNpcChip(name: string, sentient: boolean): string {
    return `
      <span class="term-chip__body">${escapeHtml(name)}</span>
      <span class="term-chip__meta">${sentient ? 'sentient' : 'creature'}</span>
    `;
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

  private roomEyebrow(type?: string): string {
    switch (type) {
      case GAME_MESSAGE_TYPES.WELCOME:
        return 'The road opens';
      case GAME_MESSAGE_TYPES.ROOM_REFRESH:
        return 'You look around';
      default:
        return 'You arrive';
    }
  }

  private toTitleCase(value: string): string {
    return value
      .split(/\s+/)
      .filter(Boolean)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }
}
