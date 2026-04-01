import { Injectable } from '@angular/core';

import {
  GAME_MESSAGE_TYPES,
  GameMessage,
  TERMINAL_MESSAGE_CLASSES,
} from '../models/game-message';
import { escapeHtml } from '../utils/html';
import { type FormattedMessage } from './message-format.types';

@Injectable({ providedIn: 'root' })
export class TerminalCoreFormatterService {
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
        <pre class="term-copy term-copy--preformatted">${escapeHtml(message)}</pre>
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

  private renderChat(msg: GameMessage): string {
    const type = msg.type ?? GAME_MESSAGE_TYPES.CHAT_ROOM;
    const label =
      type === GAME_MESSAGE_TYPES.CHAT_ROOM ? 'room' :
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
}
