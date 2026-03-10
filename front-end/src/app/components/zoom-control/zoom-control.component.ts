import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ZoomService } from '../../services/zoom.service';

@Component({
  selector: 'app-zoom-control',
  standalone: true,
  template: `
    <div class="zoom-controls">
      <button type="button" (click)="zoomService.decreaseZoom()" title="Decrease zoom" class="zoom-btn" aria-label="Decrease zoom">−</button>
      <span class="zoom-display">{{ zoomService.zoomLevel() }}%</span>
      <button type="button" (click)="zoomService.increaseZoom()" title="Increase zoom" class="zoom-btn" aria-label="Increase zoom">+</button>
    </div>
  `,
  styles: [`
    .zoom-controls {
      position: fixed;
      bottom: 16px;
      right: 16px;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      background: var(--bg-surface);
      border: 1px solid var(--border-surface);
      border-radius: 4px;
      z-index: 10000;
    }

    .zoom-btn {
      background: none;
      border: 1px solid var(--border-surface);
      color: var(--text-muted);
      padding: 6px 10px;
      font-size: 0.95rem;
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
        background: var(--bg-deep);
        color: var(--text-primary);
        border-color: var(--text-muted);
      }

      &:active {
        opacity: 0.7;
      }
    }

    .zoom-display {
      font-size: 0.8rem;
      color: var(--text-muted);
      min-width: 40px;
      text-align: center;
      font-family: var(--font-mono);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ZoomControlComponent {
  readonly zoomService = inject(ZoomService);
}
