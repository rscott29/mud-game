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
    <div class="player-stats">
      <div class="stat-row">
        <div class="stat-block">
          <div class="stat-name">Health</div>
          <div class="stat-display health">{{ currentHealth() }} / {{ maxHealth() }}</div>
        </div>
        <div class="stat-block">
          <div class="stat-name">Mana</div>
          <div class="stat-display mana">{{ currentMana() }} / {{ maxMana() }}</div>
        </div>
        <div class="stat-block">
          <div class="stat-name">Movement</div>
          <div class="stat-display movement">{{ currentMovement() }} / {{ maxMovement() }}</div>
        </div>
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
      font-size: 1em;
      font-weight: 600;
      font-family: var(--font-mono);
      letter-spacing: 0.01em;
      line-height: 1.2;
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

  readonly currentHealth = computed(() => this.stats().health);
  readonly maxHealth = computed(() => this.stats().maxHealth);

  readonly currentMana = computed(() => this.stats().mana);
  readonly maxMana = computed(() => this.stats().maxMana);

  readonly currentMovement = computed(() => this.stats().movement);
  readonly maxMovement = computed(() => this.stats().maxMovement);
}
