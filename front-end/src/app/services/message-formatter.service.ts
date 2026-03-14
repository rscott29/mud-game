import { Injectable } from '@angular/core';
import { GameMessage } from '../models/game-message';
import { escapeHtml, renderMarkup } from '../utils/html';

export interface FormattedMessage {
  /** CSS class for message styling */
  cssClass: string;
  /** Rendered HTML content */
  html: string;
}

export type MessageAction =
  | { type: 'display'; message: FormattedMessage }
  | { type: 'clear' }
  | { type: 'skip' }
  | { type: 'character_creation' }
  | { type: 'set_password_mode'; enabled: boolean };

/**
 * Formats GameMessage payloads into displayable HTML content.
 * Handles room updates, chat messages, and general message formatting.
 */
@Injectable({ providedIn: 'root' })
export class MessageFormatterService {

  /**
   * Process a game message and return the appropriate action.
   * Some messages produce display output, others trigger state changes.
   */
  format(msg: GameMessage): MessageAction {
    const type = msg.type ?? 'MESSAGE';

    switch (type) {
      case 'WELCOME':
        return {
          type: 'display',
          message: { cssClass: 'ROOM_UPDATE', html: this.formatRoom(msg) },
        };

      case 'ROOM_UPDATE':
        return {
          type: 'display',
          message: { cssClass: 'ROOM_UPDATE', html: this.formatRoom(msg) },
        };

      case 'AUTH_PROMPT':
        return {
          type: 'display',
          message: { cssClass: 'AUTH_PROMPT', html: escapeHtml(msg.message ?? '') },
        };

      case 'CHAT_ROOM':
      case 'CHAT_WORLD':
      case 'CHAT_DM':
        return {
          type: 'display',
          message: { cssClass: type, html: this.formatChat(msg) },
        };

      case 'WHO_LIST':
      case 'INVENTORY_UPDATE':
      case 'HELP':
      case 'STAT_UPDATE':
      case 'CLASS_PROGRESSION':
        // Handled visually by their respective panels — no terminal output needed.
        return { type: 'skip' };

      case 'CHARACTER_CREATION':
        return { type: 'character_creation' };

      default:
        return {
          type: 'display',
          message: { cssClass: type, html: renderMarkup(msg.message ?? JSON.stringify(msg)) },
        };
    }
  }

  /**
   * Determine if a message should clear the terminal and/or set password mode.
   * Returns null if no special handling needed.
   */
  getStateChanges(msg: GameMessage): { clearMessages?: boolean; passwordMode?: boolean } | null {
    const type = msg.type ?? 'MESSAGE';

    if (type === 'WELCOME') {
      return { clearMessages: true, passwordMode: false };
    }

    if (type === 'AUTH_PROMPT') {
      const clearMessages = (msg.message ?? '').includes('username');
      return { clearMessages, passwordMode: msg.mask === true };
    }

    if (type === 'CHARACTER_CREATION') {
      return { clearMessages: true, passwordMode: false };
    }

    return null;
  }

  private formatChat(msg: GameMessage): string {
    const type = msg.type!;
    const badgeKey =
      type === 'CHAT_ROOM' ? 'room' :
      type === 'CHAT_WORLD' ? 'world' :
      'dm';

    const label =
      type === 'CHAT_ROOM' ? 'Room' :
      type === 'CHAT_WORLD' ? 'World' :
      'DM';

    return `
      <span class="chat-badge ${badgeKey}">${label}</span>
      <span class="chat-from">${escapeHtml(msg.from ?? '')}</span>
      ${escapeHtml(msg.message ?? '')}
    `;
  }

  private formatRoom(msg: GameMessage): string {
    const r = msg.room;
    if (!r) {
      return escapeHtml(msg.message ?? '');
    }

    const exitChips = (r.exits ?? []).length
      ? r.exits.map(e => `<span class="chip chip-exit">${escapeHtml(e)}</span>`).join('')
      : `<span class="chip-none">none</span>`;

    const itemChips = (r.items ?? []).length
      ? r.items.map(i => `<span class="chip chip-item rarity-${escapeHtml(i.rarity)}">${escapeHtml(i.name)}</span>`).join('')
      : `<span class="chip-none">none</span>`;

    const npcChips = (r.npcs ?? []).length
      ? r.npcs.map(n => `<span class="chip ${n.sentient ? 'chip-npc-sentient' : 'chip-npc'}">${escapeHtml(n.name)}</span>`).join('')
      : `<span class="chip-none">none</span>`;

    const playerChips = (r.players ?? []).length
      ? r.players.map(p => `<span class="chip chip-player">${escapeHtml(p)}</span>`).join('')
      : `<span class="chip-none">none</span>`;

    return `
      ${msg.message ? `<div class="room-msg">${escapeHtml(msg.message)}</div>` : ''}
      <div class="room-name">${escapeHtml(r.name)}</div>
      <div class="room-desc">${escapeHtml(r.description)}</div>
      <div class="room-meta">
        <div class="meta-group"><span class="meta-label">Exits</span>${exitChips}</div>
        <div class="meta-group"><span class="meta-label">Items</span>${itemChips}</div>
        <div class="meta-group"><span class="meta-label">NPCs</span>${npcChips}</div>
        <div class="meta-group"><span class="meta-label">Players</span>${playerChips}</div>
      </div>
    `;
  }
}
