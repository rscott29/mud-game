import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';

import { GameSocketService } from '../../services/game-socket.service';
import { ZoomService } from '../../services/zoom.service';

@Component({
  selector: 'app-player-stats',
  standalone: true,
  template: `
    <div class="player-stats" [class.god-mode]="isGod()">
      <div class="stat-row">
        <div class="stat-block">
          <div class="stat-name">Health</div>
          <div class="stat-display health" [class.is-god-stat]="isGod()">{{ displayHealth() }}</div>
        </div>
        <div class="stat-block">
          <div class="stat-name">Mana</div>
          <div class="stat-display mana" [class.is-god-stat]="isGod()">{{ displayMana() }}</div>
        </div>
        <div class="stat-block">
          <div class="stat-name">Movement</div>
          <div class="stat-display movement" [class.is-god-stat]="isGod()">{{ displayMovement() }}</div>
        </div>
        <div class="stat-block xp-block">
          <div class="stat-name">Level {{ currentLevel() }}@if (!isMaxLevel()) { / {{ maxLevel() }} }</div>
          <div class="xp-bar-container" [class.max-level]="isMaxLevel()">
            <div class="xp-bar-fill" [style.width.%]="xpPercentage()"></div>
          </div>
          @if (isMaxLevel()) {
            <div class="xp-text max">MAX LEVEL</div>
          } @else {
            <div class="xp-text">{{ xpProgress() }} / {{ xpForNextLevel() }} XP</div>
            <div class="xp-total-text">Total XP: {{ totalXp() }}</div>
          }
        </div>
        @if (isGod()) {
          <div class="god-indicator">
            <span class="bolt">⚡</span>
            <span>GOD MODE</span>
          </div>
        }
      </div>
      <div class="zoom-controls">
        <button type="button" (click)="zoomService.decreaseZoom()" title="Decrease zoom" class="zoom-btn" aria-label="Decrease zoom">−</button>
        <span class="zoom-display">{{ zoomService.zoomLevel() }}%</span>
        <button type="button" (click)="zoomService.increaseZoom()" title="Increase zoom" class="zoom-btn" aria-label="Increase zoom">+</button>
      </div>
    </div>
  `,
  styles: [`
    .player-stats {
      padding: 6px 12px;
      background: var(--bg-deep);
      border-top: 1px solid var(--border-surface);
      border-bottom: 1px solid var(--border-surface);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
    }

    .stat-row {
      display: flex;
      gap: 32px;
      justify-content: flex-start;
      align-items: baseline;
      flex: 1;
    }

    .stat-block {
      display: flex;
      flex-direction: column;
      gap: 1px;
    }

    .stat-name {
      font-size: 0.65em;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.06em;
      line-height: 1;
    }

    .stat-display {
      font-size: 1.1em;
      font-weight: 600;
      font-family: var(--font-mono);
      letter-spacing: 0.02em;
      line-height: 1.2;
      display: flex;
      align-items: center;
      min-height: 1.4em;
    }

    .stat-display.is-god-stat {
      font-size: 1.6em;
      line-height: 1;
      margin-top: -2px;
    }

    .stat-display.health {
      color: var(--accent-red-lt);
    }

    .stat-display.mana {
      color: var(--accent-blue-lt);
    }

    .stat-display.movement {
      color: var(--accent-green);
    }

    .xp-block {
      min-width: 140px;
    }

    .xp-bar-container {
      width: 100%;
      height: 8px;
      background: var(--bg-surface);
      border: 1px solid var(--border-surface);
      border-radius: 4px;
      overflow: hidden;
      margin: 2px 0;
    }

    .xp-bar-container.max-level {
      border-color: var(--accent-gold, #ffd700);
      box-shadow: 0 0 4px var(--accent-gold, #ffd700);
    }

    .xp-bar-fill {
      height: 100%;
      background: linear-gradient(90deg, 
        var(--accent-purple, #9575cd) 0%, 
        var(--accent-purple-lt, #b39ddb) 100%);
      border-radius: 3px;
      transition: width 0.3s ease-out;
      box-shadow: 0 0 6px var(--accent-purple, #9575cd);
    }

    .xp-bar-container.max-level .xp-bar-fill {
      background: linear-gradient(90deg, 
        var(--accent-gold, #ffd700) 0%, 
        #ffeb3b 50%,
        var(--accent-gold, #ffd700) 100%);
      box-shadow: 0 0 8px var(--accent-gold, #ffd700);
    }

    .xp-text {
      font-size: 0.7em;
      font-family: var(--font-mono);
      color: var(--accent-purple-lt, #b39ddb);
      text-align: center;
      line-height: 1;
    }

    .xp-total-text {
      margin-top: 2px;
      font-size: 0.62em;
      font-family: var(--font-mono);
      color: var(--text-muted);
      text-align: center;
      line-height: 1;
    }

    .xp-text.max {
      color: var(--accent-gold, #ffd700);
      font-weight: 700;
      letter-spacing: 0.05em;
    }

    .god-mode {
      background: linear-gradient(90deg, 
        rgba(26, 26, 36, 1) 0%, 
        rgba(40, 35, 15, 0.4) 30%, 
        rgba(50, 42, 10, 0.6) 50%, 
        rgba(40, 35, 15, 0.4) 70%, 
        rgba(26, 26, 36, 1) 100%);
      border-top: 1px solid rgba(255, 215, 0, 0.2);
    }

    .god-indicator {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75em;
      font-weight: 800;
      color: #FFD700;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      padding: 4px 14px;
      margin-left: 20px;
      background: rgba(255, 215, 0, 0.05);
      border: 1px solid rgba(255, 215, 0, 0.2);
      border-radius: 4px;
      box-shadow: 0 0 10px rgba(255, 215, 0, 0.1), inset 0 0 5px rgba(255, 215, 0, 0.05);
      animation: godPulse 3s ease-in-out infinite;
    }

    .god-indicator .bolt {
      font-size: 1.2em;
      margin-right: -2px;
      filter: drop-shadow(0 0 2px rgba(255, 215, 0, 0.8));
    }

    @keyframes godPulse {
      0%, 100% { 
        opacity: 0.8;
        box-shadow: 0 0 10px rgba(255, 215, 0, 0.1), inset 0 0 5px rgba(255, 215, 0, 0.05);
        border-color: rgba(255, 215, 0, 0.15);
      }
      50% { 
        opacity: 1;
        box-shadow: 0 0 15px rgba(255, 215, 0, 0.2), inset 0 0 8px rgba(255, 215, 0, 0.1);
        border-color: rgba(255, 215, 0, 0.4);
      }
    }

    .zoom-controls {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
      padding-left: 12px;
      border-left: 1px solid var(--border-surface);
    }

    .zoom-btn {
      background: none;
      border: 1px solid var(--border-surface);
      color: var(--text-muted);
      padding: 6px 10px;
      font-size: 0.95em;
      font-weight: 600;
      cursor: pointer;
      border-radius: 2px;
      transition: all 0.2s ease;
      min-width: 36px;
      min-height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;

      &:hover {
        background: var(--bg-surface);
        color: var(--text-primary);
        border-color: var(--text-muted);
      }

      &:active {
        opacity: 0.7;
      }
    }

    .zoom-display {
      font-size: 0.8em;
      color: var(--text-muted);
      min-width: 40px;
      text-align: center;
      font-family: var(--font-mono);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayerStatsComponent {
  private readonly socketService = inject(GameSocketService);
  readonly zoomService = inject(ZoomService);

  readonly stats = this.socketService.playerStats;
  readonly hasStats = computed(() => this.stats() !== null);
  readonly isGod = computed(() => this.stats()?.isGod ?? false);

  readonly currentHealth = computed(() => this.stats()?.health ?? '--');
  readonly maxHealth = computed(() => this.stats()?.maxHealth ?? '--');

  readonly currentMana = computed(() => this.stats()?.mana ?? '--');
  readonly maxMana = computed(() => this.stats()?.maxMana ?? '--');

  readonly currentMovement = computed(() => this.stats()?.movement ?? '--');
  readonly maxMovement = computed(() => this.stats()?.maxMovement ?? '--');

  readonly displayHealth = computed(() => 
    this.isGod() ? '∞' : `${this.currentHealth()} / ${this.maxHealth()}`
  );

  readonly displayMana = computed(() => 
    this.isGod() ? '∞' : `${this.currentMana()} / ${this.maxMana()}`
  );

  readonly displayMovement = computed(() => 
    this.isGod() ? '∞' : `${this.currentMovement()} / ${this.maxMovement()}`
  );

  readonly currentLevel = computed(() => this.stats()?.level ?? 1);
  readonly maxLevel = computed(() => this.stats()?.maxLevel ?? 70);
  readonly isMaxLevel = computed(() => this.currentLevel() >= this.maxLevel());
  readonly xpProgress = computed(() => this.stats()?.xpProgress ?? 0);
  readonly xpForNextLevel = computed(() => this.stats()?.xpForNextLevel ?? 100);
  readonly totalXp = computed(() => this.stats()?.totalXp ?? 0);
  readonly xpPercentage = computed(() => {
    if (this.isMaxLevel()) return 100;
    const xp = this.xpProgress();
    const needed = this.xpForNextLevel();
    if (needed <= 0) return 100;
    return Math.min(100, Math.round((xp / needed) * 100));
  });
}
