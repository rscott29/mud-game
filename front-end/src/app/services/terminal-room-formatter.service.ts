import { Injectable } from '@angular/core';

import {
  GAME_MESSAGE_TYPES,
  GameMessage,
  ShopDto,
  TERMINAL_MESSAGE_CLASSES,
  type TerminalMessageClass,
} from '../models/game-message';
import { escapeHtml, renderMarkup } from '../utils/html';
import { type FormattedMessage } from './message-format.types';

@Injectable({ providedIn: 'root' })
export class TerminalRoomFormatterService {
  formatRoomDisplay(msg: GameMessage): FormattedMessage {
    return {
      cssClass: msg.type ?? GAME_MESSAGE_TYPES.ROOM_UPDATE,
      html: this.renderRoom(msg),
    };
  }

  formatNarrativeInlineMessage(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.NARRATIVE,
      html: this.isCombatMarkup(message)
        ? this.renderCombatNarrative(message)
        : this.renderInlineNarrative(message),
    };
  }

  formatNarrativeEchoMessage(message: string): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.NARRATIVE_ECHO,
      html: this.renderNarrativeEcho(message),
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
      case GAME_MESSAGE_TYPES.NARRATIVE_ECHO:
        return this.renderNarrativeEcho(msg.message ?? '');
      default:
        return msg.message ?? '';
    }
  }

  private renderNarrative(label: string, message: string): string {
    if (this.isCombatMarkup(message)) {
      return this.renderCombatNarrative(message);
    }

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

  private renderCombatNarrative(message: string): string {
    return `
      <section class="term-card term-card--combat">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">Combat</div>
            <h2 class="term-card__title">Combat feed</h2>
          </div>
          <span class="term-badge term-badge--warning">live</span>
        </div>
        <div class="term-copy term-copy--combat">${renderMarkup(message)}</div>
      </section>
    `;
  }

  private renderInlineNarrative(message: string): string {
    return this.wrapInlineNarrative(renderMarkup(message));
  }

  private renderNarrativeEcho(message: string): string {
    return `<div class="message--narrative-echo">${renderMarkup(message)}</div>`;
  }

  private isCombatMarkup(message: string): boolean {
    return message.includes('combat-log') || message.includes('combat-line');
  }

  private wrapInlineNarrative(content: string): string {
    return `<div class="term-inline-narrative">${content}</div>`;
  }

  private formatInlineEventContent(label: string, message: string, variantClass: string): string {
    return `
      <span class="term-inline-event ${variantClass}">
        <span class="term-inline-event__label">${escapeHtml(label)}</span>
        <span class="term-inline-event__text">${renderMarkup(message)}</span>
      </span>
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
            ${this.formatChipList((room.npcs ?? []).map(npc => this.formatNpcChip(npc.name, npc.sentient, npc.hasAvailableQuest ?? false)), 'term-chip--npc', true)}
          </section>
          <section class="term-section">
            <div class="term-section__title">Travelers</div>
            ${this.formatChipList(room.players ?? [], 'term-chip--player')}
          </section>
        </div>
        ${this.renderShop(room.shop ?? null)}
        ${leadText ? `<div class="term-inline-narrative">${renderMarkup(leadText)}</div>` : ''}
        ${trailingText ? `<div class="term-inline-narrative">${renderMarkup(trailingText)}</div>` : ''}
      </section>
    `;
  }

  private renderShop(shop: ShopDto | null): string {
    if (!shop || (shop.listings?.length ?? 0) === 0) {
      return '';
    }

    return `
      <section class="term-shop" aria-label="Merchant inventory">
        <div class="term-shop__header">
          <div>
            <div class="term-card__eyebrow">Merchant</div>
            <h3 class="term-shop__title">${escapeHtml(shop.merchantName)}</h3>
          </div>
          <span class="term-badge">${escapeHtml(String(shop.listings.length))} wares</span>
        </div>
        <div class="term-shop__grid">
          ${shop.listings.map(listing => `
            <article class="term-shop__item">
              <div class="term-shop__item-header">
                <div>
                  <div class="term-shop__item-title">${escapeHtml(listing.name)}</div>
                  <div class="term-shop__item-price">${escapeHtml(String(listing.price))} gold</div>
                </div>
                <span class="term-pill term-pill--rarity term-pill--${escapeHtml(listing.rarity)}">${escapeHtml(this.toTitleCase(listing.rarity))}</span>
              </div>
              <div class="term-copy term-copy--muted">${renderMarkup(listing.description ?? '')}</div>
              <a href="#" class="term-shop__buy" title="${escapeHtml(`buy ${listing.itemId}`)}">Buy now</a>
            </article>
          `).join('')}
        </div>
      </section>
    `;
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

  private formatNpcChip(name: string, sentient: boolean, hasAvailableQuest: boolean): string {
    const meta = hasAvailableQuest
      ? `${sentient ? 'sentient' : 'creature'} - quest ready`
      : (sentient ? 'sentient' : 'creature');

    return `
      ${hasAvailableQuest ? '<span class="term-chip__marker term-chip__marker--quest" aria-hidden="true">!</span>' : ''}
      <span class="term-chip__body">${escapeHtml(name)}</span>
      <span class="term-chip__meta">${escapeHtml(meta)}</span>
    `;
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
