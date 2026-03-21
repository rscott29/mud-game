import { Injectable, computed, inject } from '@angular/core';

import {
  CONNECTION_STATUSES,
  type ConnectionStatus,
  type PlayerStatsDto,
} from '../models/game-message';
import { GameSocketService } from './game-socket.service';
import { TerminalMessageStore } from './terminal-message-store.service';
import { ZoomService } from './zoom.service';

@Injectable()
export class TerminalPresenterService {
  private readonly socketService = inject(GameSocketService);
  private readonly store = inject(TerminalMessageStore);
  private readonly zoomService = inject(ZoomService);

  readonly messages = this.store.messages;
  readonly playerStats = this.socketService.playerStats;
  readonly characterCreationData = this.store.characterCreationData;
  readonly zoomLevel = this.zoomService.zoomLevel;

  readonly inputType = computed(() => (this.store.passwordMode() ? 'password' : 'text'));
  readonly promptLabel = computed(() => (this.store.passwordMode() ? 'pass' : 'cmd'));
  readonly placeholder = computed(() =>
    this.store.passwordMode() ? 'Whisper your password...' : 'Speak a command...'
  );
  readonly statusLabel = computed(() => {
    switch (this.socketService.status()) {
      case CONNECTION_STATUSES.CONNECTED:
        return 'Connected';
      case CONNECTION_STATUSES.RECONNECTING:
        return 'Reconnecting...';
      default:
        return 'Disconnected';
    }
  });
  readonly statusClass = computed<Record<ConnectionStatus, boolean>>(() => ({
    [CONNECTION_STATUSES.CONNECTED]: this.socketService.status() === CONNECTION_STATUSES.CONNECTED,
    [CONNECTION_STATUSES.RECONNECTING]: this.socketService.status() === CONNECTION_STATUSES.RECONNECTING,
    [CONNECTION_STATUSES.DISCONNECTED]:
      this.socketService.status() !== CONNECTION_STATUSES.CONNECTED
      && this.socketService.status() !== CONNECTION_STATUSES.RECONNECTING,
  }));
  readonly classLabel = computed(() => this.formatClassName(this.playerStats()?.characterClass ?? ''));
  readonly levelLabel = computed(() => {
    const stats = this.playerStats();
    if (!stats) {
      return 'Awaiting profile';
    }

    if (stats.isGod || stats.maxLevel <= 0 || stats.level > stats.maxLevel) {
      return `Level ${stats.level}`;
    }

    return `Level ${stats.level} / ${stats.maxLevel}`;
  });
  readonly hudHealth = computed(() =>
    this.formatResource('HP', this.playerStats(), stats => stats.health, stats => stats.maxHealth)
  );
  readonly hudMana = computed(() =>
    this.formatResource('MP', this.playerStats(), stats => stats.mana, stats => stats.maxMana)
  );
  readonly hudMovement = computed(() =>
    this.formatResource('MV', this.playerStats(), stats => stats.movement, stats => stats.maxMovement)
  );
  readonly xpPercent = computed(() => {
    const stats = this.playerStats();
    if (!stats) {
      return 0;
    }
    if (stats.level >= stats.maxLevel || stats.xpForNextLevel <= 0) {
      return 100;
    }
    return Math.max(0, Math.min(100, Math.round((stats.xpProgress / stats.xpForNextLevel) * 100)));
  });
  readonly xpLabel = computed(() => {
    const stats = this.playerStats();
    if (!stats) {
      return 'XP offline';
    }
    if (stats.level >= stats.maxLevel) {
      return 'Max level';
    }
    return `${stats.xpProgress} / ${stats.xpForNextLevel} XP`;
  });

  decreaseZoom(): void {
    this.zoomService.decreaseZoom();
  }

  increaseZoom(): void {
    this.zoomService.increaseZoom();
  }

  private formatClassName(value: string): string {
    return value
      .split(/[_\s-]+/)
      .filter(Boolean)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  private formatResource(
    label: string,
    stats: PlayerStatsDto | null,
    current: (stats: PlayerStatsDto) => number,
    max: (stats: PlayerStatsDto) => number
  ): string {
    if (!stats) {
      return `${label} --`;
    }

    if (stats.isGod) {
      return `${label} INF`;
    }

    return `${label} ${current(stats)}/${max(stats)}`;
  }
}
