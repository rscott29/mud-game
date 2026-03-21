import { Injectable } from '@angular/core';

import {
  GameMessage,
  ItemDto,
  WhoPlayerDto,
} from '../models/game-message';
import { escapeHtml, renderMarkup } from '../utils/html';
import { SkillDto } from './skill-progression.service';

interface HelpEntry {
  cmd: string;
  desc: string;
}

interface HelpCategory {
  title: string;
  entries: HelpEntry[];
}

const BASE_HELP_CATEGORIES: HelpCategory[] = [
  {
    title: 'Movement',
    entries: [
      { cmd: 'look / l', desc: 'Describe your current surroundings.' },
      { cmd: 'look <target>', desc: 'Examine an item, NPC, or room feature.' },
      { cmd: 'go <direction>', desc: 'Move north, south, east, west, up, or down.' },
      { cmd: 'n / s / e / w / u / d', desc: 'Short directional commands.' },
    ],
  },
  {
    title: 'Exploration',
    entries: [
      { cmd: 'investigate / search', desc: 'Search the room for hidden exits or secrets.' },
      { cmd: 'quest / quests', desc: 'Review your active quest log.' },
      { cmd: 'accept <quest>', desc: 'Accept a quest offered by an NPC.' },
      { cmd: 'give <item> to <npc>', desc: 'Turn in quest items or make an offering.' },
    ],
  },
  {
    title: 'Items',
    entries: [
      { cmd: 'take <item> / get <item>', desc: 'Pick up an item from the room.' },
      { cmd: 'drop <item>', desc: 'Drop an item from your inventory.' },
      { cmd: 'inventory / inv / i', desc: 'List what you are carrying.' },
      { cmd: 'equip <item>', desc: 'Equip a weapon from your pack.' },
    ],
  },
  {
    title: 'Communication',
    entries: [
      { cmd: 'talk <npc>', desc: 'Talk to a sentient NPC in the room.' },
      { cmd: '/speak <message>', desc: 'Chat with players in your room.' },
      { cmd: '/world <message>', desc: 'Broadcast to all online players.' },
      { cmd: '/dm <player> <msg>', desc: 'Send a private message.' },
      { cmd: 'who / /who', desc: 'List online players and their locations.' },
    ],
  },
  {
    title: 'Social',
    entries: [
      { cmd: '/em <action>', desc: 'Perform a custom emote.' },
      { cmd: 'wave, smile, nod, bow', desc: 'Built-in social actions.' },
      { cmd: 'laugh, cheer, dance', desc: 'More socials, with optional targets.' },
    ],
  },
  {
    title: 'General',
    entries: [
      { cmd: 'help / ?', desc: 'Open the command reference.' },
      { cmd: 'skills / sk', desc: 'View your class progression track.' },
      { cmd: 'logout', desc: 'Log out of the game safely.' },
    ],
  },
];

const GOD_HELP_CATEGORY: HelpCategory = {
  title: 'God Commands',
  entries: [
    { cmd: 'spawn <item_id> [inv]', desc: 'Spawn an item in the room or inventory.' },
    { cmd: 'deleteitem <item>', desc: 'Delete an item from your inventory.' },
    { cmd: 'teleport / tp <target>', desc: 'Teleport to a player or NPC.' },
    { cmd: 'summon <player>', desc: 'Pull a player to your location.' },
    { cmd: 'kick <player>', desc: 'Kick a player from the game.' },
    { cmd: 'setlevel [player] <level>', desc: 'Set a player level directly.' },
    { cmd: 'resetquest <player> <quest>', desc: 'Reset quest state for a player.' },
  ],
};

export interface FormattedMessage {
  cssClass: string;
  html: string;
}

export type MessageAction =
  | { type: 'display'; message: FormattedMessage }
  | { type: 'skip' }
  | { type: 'character_creation' }
  | { type: 'class_progression' };

@Injectable({ providedIn: 'root' })
export class MessageFormatterService {
  format(msg: GameMessage): MessageAction {
    const type = msg.type ?? 'MESSAGE';

    switch (type) {
      case 'WELCOME':
        return {
          type: 'display',
          message: { cssClass: 'WELCOME', html: this.formatRoom(msg) },
        };

      case 'ROOM_UPDATE':
        return {
          type: 'display',
          message: { cssClass: 'ROOM_UPDATE', html: this.formatRoom(msg) },
        };

      case 'ROOM_REFRESH':
        return {
          type: 'display',
          message: { cssClass: 'ROOM_REFRESH', html: this.formatRoom(msg) },
        };

      case 'AUTH_PROMPT':
        return {
          type: 'display',
          message: {
            cssClass: 'AUTH_PROMPT',
            html: this.formatPrompt(msg.message ?? ''),
          },
        };

      case 'CHAT_ROOM':
      case 'CHAT_WORLD':
      case 'CHAT_DM':
        return {
          type: 'display',
          message: { cssClass: type, html: this.formatChat(msg) },
        };

      case 'WHO_LIST':
        return {
          type: 'display',
          message: {
            cssClass: 'WHO_LIST',
            html: this.formatWho(msg.whoPlayers ?? []),
          },
        };

      case 'INVENTORY_UPDATE':
        return {
          type: 'display',
          message: {
            cssClass: 'INVENTORY_UPDATE',
            html: this.formatInventory(msg.inventory ?? []),
          },
        };

      case 'HELP':
        return {
          type: 'display',
          message: {
            cssClass: 'HELP',
            html: this.formatHelp((msg.message ?? '').trim().toLowerCase() === 'god'),
          },
        };

      case 'STAT_UPDATE':
        return { type: 'skip' };

      case 'CLASS_PROGRESSION':
        return { type: 'class_progression' };

      case 'CHARACTER_CREATION':
        return { type: 'character_creation' };

      case 'NARRATIVE':
        return {
          type: 'display',
          message: {
            cssClass: 'NARRATIVE',
            html: this.formatInlineNarrative(msg.message ?? ''),
          },
        };

      case 'ROOM_ACTION':
        return {
          type: 'display',
          message: {
            cssClass: 'ROOM_ACTION',
            html: this.wrapInlineNarrative(
              this.formatInlineEventContent('Traveler', msg.message ?? '', 'term-inline-event--player-action')
            ),
          },
        };

      case 'AMBIENT_EVENT':
        return {
          type: 'display',
          message: {
            cssClass: 'AMBIENT_EVENT',
            html: this.formatNarrative('Whispers', msg.message ?? ''),
          },
        };

      case 'COMPANION_DIALOGUE':
        return {
          type: 'display',
          message: {
            cssClass: 'COMPANION_DIALOGUE',
            html: this.formatNarrative('Companion voice', msg.message ?? ''),
          },
        };

      default:
        return {
          type: 'display',
          message: {
            cssClass: type,
            html: this.formatNarrative(type, msg.message ?? JSON.stringify(msg)),
          },
        };
    }
  }

  getStateChanges(msg: GameMessage): { clearMessages?: boolean; passwordMode?: boolean } | null {
    const type = msg.type ?? 'MESSAGE';

    if (type === 'WELCOME') {
      return { clearMessages: true, passwordMode: false };
    }

    if (type === 'AUTH_PROMPT') {
      const clearMessages = (msg.message ?? '').toLowerCase().includes('username');
      return { clearMessages, passwordMode: msg.mask === true };
    }

    if (type === 'CHARACTER_CREATION') {
      return { clearMessages: true, passwordMode: false };
    }

    return null;
  }

  formatSystem(text: string): FormattedMessage {
    return {
      cssClass: 'SYSTEM',
      html: `
        <div class="term-inline term-inline--system">
          <span class="term-tag">realm</span>
          <span class="term-inline__text">${escapeHtml(text)}</span>
        </div>
      `,
    };
  }

  formatRoomInlineFragment(msg: GameMessage): string {
    if ((msg.message ?? '').trim() === '') {
      return '';
    }

    switch (msg.type) {
      case 'ROOM_ACTION':
        return this.formatInlineEventContent(
          'Traveler',
          msg.message ?? '',
          'term-inline-event--player-action'
        );
      default:
        return msg.message ?? '';
    }
  }

  formatClassProgressionLoading(characterClass: string): FormattedMessage {
    return this.notice(
      'CLASS_PROGRESSION',
      'Unfurling the skill ledger',
      `Drawing the ${escapeHtml(this.formatClassName(characterClass))} arts into the ledger...`
    );
  }

  formatClassProgressionError(characterClass: string): FormattedMessage {
    return this.notice(
      'ERROR',
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
      cssClass: 'CLASS_PROGRESSION',
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

  private formatPrompt(message: string): string {
    return `
      <section class="term-card term-card--prompt">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">Wayfarer entry</div>
            <h2 class="term-card__title">The gatehouse asks</h2>
          </div>
        </div>
        <div class="term-copy term-copy--preformatted">${this.formatPlainText(message)}</div>
      </section>
    `;
  }

  private formatNarrative(label: string, message: string): string {
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

  private formatInlineNarrative(message: string): string {
    return this.wrapInlineNarrative(renderMarkup(message));
  }

  private formatPlainText(message: string): string {
    return escapeHtml(message).replace(/\r?\n/g, '<br>');
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

  private formatChat(msg: GameMessage): string {
    const type = msg.type ?? 'CHAT_ROOM';
    const label =
      type === 'CHAT_ROOM' ? 'hall' :
      type === 'CHAT_WORLD' ? 'realm' :
      'whisper';

    return `
      <div class="term-inline term-inline--chat">
        <span class="term-tag term-tag--${label}">${escapeHtml(label)}</span>
        <span class="term-inline__from">${escapeHtml(msg.from ?? 'unknown')}</span>
        <span class="term-inline__text">${escapeHtml(msg.message ?? '')}</span>
      </div>
    `;
  }

  private formatRoom(msg: GameMessage): string {
    const room = msg.room;
    if (!room) {
      return this.formatNarrative(msg.type ?? 'MESSAGE', msg.message ?? '');
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

  private formatInventory(items: ItemDto[]): string {
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

  private formatWho(players: WhoPlayerDto[]): string {
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

  private formatHelp(isGod: boolean): string {
    const categories = isGod ? [...BASE_HELP_CATEGORIES, GOD_HELP_CATEGORY] : BASE_HELP_CATEGORIES;

    return `
      <section class="term-card term-card--help">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">Field notes</div>
            <h2 class="term-card__title">Traveler's handbook</h2>
          </div>
          <span class="term-badge">${isGod ? "warden's sight" : "traveler's sight"}</span>
        </div>
        <div class="term-columns">
          ${categories.map(category => `
            <section class="term-section">
              <div class="term-section__title">${escapeHtml(category.title)}</div>
              <div class="term-kv-list">
                ${category.entries.map(entry => `
                  <div class="term-kv">
                    <div class="term-kv__key">${escapeHtml(entry.cmd)}</div>
                    <div class="term-kv__value">${escapeHtml(entry.desc)}</div>
                  </div>
                `).join('')}
              </div>
            </section>
          `).join('')}
        </div>
        <div class="term-footnote">Natural language still works. Plain speech is welcome when a formal command feels clumsy.</div>
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

  private notice(cssClass: string, title: string, body: string): FormattedMessage {
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
      case 'WELCOME':
        return 'The road opens';
      case 'ROOM_REFRESH':
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
