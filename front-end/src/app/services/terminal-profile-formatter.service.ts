import { Injectable } from '@angular/core';

import {
  CombatStatsDto,
  GameMessage,
  ItemDto,
  TERMINAL_MESSAGE_CLASSES,
  WhoPlayerDto,
} from '../models/game-message';
import { escapeHtml, renderMarkup } from '../utils/html';
import { type FormattedMessage } from './message-format.types';

const PLAYER_OVERVIEW_SLOTS = [
  { id: 'main_weapon', label: 'Main weapon' },
  { id: 'off_hand', label: 'Off hand / shield' },
  { id: 'head', label: 'Head' },
  { id: 'chest', label: 'Chest' },
  { id: 'hands', label: 'Hands' },
  { id: 'legs', label: 'Legs' },
] as const;

@Injectable({ providedIn: 'root' })
export class TerminalProfileFormatterService {
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

  formatPlayerOverview(msg: GameMessage): FormattedMessage {
    return {
      cssClass: TERMINAL_MESSAGE_CLASSES.PLAYER_OVERVIEW,
      html: this.renderPlayerOverview(msg),
    };
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
                    ${item.equipped ? `<span class="term-pill term-pill--equipped">Equipped${item.equippedSlot ? `: ${escapeHtml(item.equippedSlot)}` : ''}</span>` : ''}
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

  private renderPlayerOverview(msg: GameMessage): string {
    const stats = msg.playerStats;
    const combat = msg.combatStats;
    const items = msg.inventory ?? [];
    const playerName = msg.message?.trim() || 'Traveler';
    const slots = PLAYER_OVERVIEW_SLOTS.map(slot => {
      const equippedItem = this.findEquippedItem(items, slot.id, slot.label);
      return {
        ...slot,
        itemName: equippedItem?.name ?? 'Empty',
        rarityLabel: equippedItem ? this.toTitleCase(equippedItem.rarity) : null,
        isEmpty: equippedItem == null,
      };
    });
    const equippedCount = slots.filter(slot => !slot.isEmpty).length;
    const progressPercent = !stats
      ? 0
      : stats.level >= stats.maxLevel || stats.xpForNextLevel <= 0
        ? 100
        : Math.max(0, Math.min(100, Math.round((stats.xpProgress / stats.xpForNextLevel) * 100)));
    const classLabel = this.formatClassName(stats?.characterClass ?? 'adventurer');
    const levelLabel = !stats
      ? 'Level unknown'
      : stats.isGod || stats.maxLevel <= 0 || stats.level > stats.maxLevel
        ? `Level ${stats.level}`
        : `Level ${stats.level} / ${stats.maxLevel}`;
    const progressLabel = !stats
      ? 'XP unknown'
      : stats.level >= stats.maxLevel || stats.xpForNextLevel <= 0
        ? 'Max level'
        : `${stats.xpProgress} / ${stats.xpForNextLevel} XP`;

    return `
      <section class="term-card term-card--player-overview">
        <div class="term-card__header">
          <div>
            <div class="term-card__eyebrow">Character sheet</div>
            <h2 class="term-card__title">${escapeHtml(playerName)}</h2>
          </div>
          <span class="term-badge">${escapeHtml(String(equippedCount))}/${escapeHtml(String(slots.length))} readied</span>
        </div>
        <div class="term-columns">
          <section class="term-section">
            <div class="term-section__title">Condition</div>
            <div class="term-meta-row">
              <span class="term-label">Calling</span>
              <span class="term-value">${escapeHtml(classLabel)}</span>
            </div>
            <div class="term-meta-row">
              <span class="term-label">Level</span>
              <span class="term-value">${escapeHtml(levelLabel)}</span>
            </div>
            <div class="term-meta-row">
              <span class="term-label">Health</span>
              <span class="term-value${stats?.isGod ? ' term-value--infinite' : ''}">${this.renderSheetStatResourceValue(stats?.health, stats?.maxHealth, stats?.isGod ?? false)}</span>
            </div>
            <div class="term-meta-row">
              <span class="term-label">Mana</span>
              <span class="term-value${stats?.isGod ? ' term-value--infinite' : ''}">${this.renderSheetStatResourceValue(stats?.mana, stats?.maxMana, stats?.isGod ?? false)}</span>
            </div>
            <div class="term-meta-row">
              <span class="term-label">Movement</span>
              <span class="term-value${stats?.isGod ? ' term-value--infinite' : ''}">${this.renderSheetStatResourceValue(stats?.movement, stats?.maxMovement, stats?.isGod ?? false)}</span>
            </div>
            ${stats ? `
              <div class="term-meta-row">
                <span class="term-label">Total XP</span>
                <span class="term-value">${escapeHtml(String(stats.totalXp))}</span>
              </div>
              <div class="term-meta-row">
                <span class="term-label">Gold</span>
                <span class="term-value">${escapeHtml(String(stats.gold ?? 0))}</span>
              </div>
            ` : ''}
            <div class="term-progress">
              <div class="term-progress__bar">
                <div class="term-progress__fill" style="width: ${progressPercent}%"></div>
              </div>
              <div class="term-progress__meta">
                <span>Progress</span>
                <span>${escapeHtml(progressLabel)}</span>
              </div>
            </div>
          </section>
          <section class="term-section">
            <div class="term-section__title">Combat profile</div>
            <div class="term-meta-row">
              <span class="term-label">Armor</span>
              <span class="term-value">${escapeHtml(this.formatArmorValue(combat))}</span>
            </div>
            <div class="term-meta-row">
              <span class="term-label">Attack damage</span>
              <span class="term-value">${escapeHtml(this.formatDamageRange(combat))}</span>
            </div>
            <div class="term-meta-row">
              <span class="term-label">Hit chance</span>
              <span class="term-value">${escapeHtml(this.formatPercent(combat?.hitChance))}</span>
            </div>
            <div class="term-meta-row">
              <span class="term-label">Critical hit</span>
              <span class="term-value">${escapeHtml(this.formatPercent(combat?.critChance))}</span>
            </div>
          </section>
          <section class="term-section">
            <div class="term-section__title">Readied gear</div>
            <div class="term-profile-slots">
              ${slots.map(slot => `
                <div class="term-profile-slot${slot.isEmpty ? ' is-empty' : ''}">
                  <div class="term-profile-slot__label">${escapeHtml(slot.label)}</div>
                  <div class="term-profile-slot__item">${escapeHtml(slot.itemName)}</div>
                  ${slot.rarityLabel ? `<div class="term-profile-slot__meta">${escapeHtml(slot.rarityLabel)}</div>` : ''}
                </div>
              `).join('')}
            </div>
          </section>
        </div>
        <div class="term-footnote">Carrying ${escapeHtml(String(items.length))} item${items.length === 1 ? '' : 's'} in total.</div>
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
              <li class="term-list__item${player.isGod ? ' term-list__item--god' : ''}">
                <div class="term-list__header">
                  <span class="term-list__title">${escapeHtml(player.name)}</span>
                  <div class="term-list__badges">
                    ${player.isGod ? '<span class="term-pill term-pill--god-presence">Divine</span>' : ''}
                    <span class="term-pill${player.isGod ? ' term-pill--god-level' : ''}">Level ${escapeHtml(String(player.level))}</span>
                  </div>
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

  private findEquippedItem(items: ItemDto[], slotId: string, slotLabel: string): ItemDto | undefined {
    return items.find(item =>
      item.equipped
      && this.normalizeSlotName(item.equippedSlot) === this.normalizeSlotName(slotId)
    ) ?? items.find(item =>
      item.equipped
      && this.normalizeSlotName(item.equippedSlot) === this.normalizeSlotName(slotLabel)
    );
  }

  private normalizeSlotName(value?: string | null): string {
    return (value ?? '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');
  }

  private formatClassName(value: string): string {
    return this.toTitleCase(value.replace(/[_-]+/g, ' '));
  }

  private renderSheetStatResourceValue(
    current: number | undefined,
    max: number | undefined,
    isGod: boolean
  ): string {
    if (isGod) {
      return '<span class="term-infinity" aria-label="Infinite">&infin;</span>';
    }
    if (current == null || max == null) {
      return '--';
    }
    return escapeHtml(`${current}/${max}`);
  }

  private formatArmorValue(combat?: CombatStatsDto): string {
    if (!combat) {
      return '--';
    }
    return String(combat.armor);
  }

  private formatDamageRange(combat?: CombatStatsDto): string {
    if (!combat) {
      return '--';
    }
    return combat.minDamage === combat.maxDamage
      ? String(combat.minDamage)
      : `${combat.minDamage}-${combat.maxDamage}`;
  }

  private formatPercent(value?: number): string {
    if (value == null) {
      return '--';
    }
    return `${value}%`;
  }

  private toTitleCase(value: string): string {
    return value
      .split(/\s+/)
      .filter(Boolean)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }
}
