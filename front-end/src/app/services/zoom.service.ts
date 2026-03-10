import { Injectable, effect, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ZoomService {
  private readonly MIN_ZOOM = 80;
  private readonly MAX_ZOOM = 200;

  readonly zoomLevel = signal(this.getSavedZoom());

  constructor() {
    // Save zoom level to localStorage whenever it changes
    effect(() => {
      localStorage.setItem('mudGameZoom', this.zoomLevel().toString());
    });
  }

  increaseZoom(): void {
    const current = this.zoomLevel();
    if (current < this.MAX_ZOOM) {
      this.zoomLevel.set(Math.min(current + 10, this.MAX_ZOOM));
    }
  }

  decreaseZoom(): void {
    const current = this.zoomLevel();
    if (current > this.MIN_ZOOM) {
      this.zoomLevel.set(Math.max(current - 10, this.MIN_ZOOM));
    }
  }

  private getSavedZoom(): number {
    const saved = localStorage.getItem('mudGameZoom');
    if (saved) {
      const zoom = parseInt(saved, 10);
      return isNaN(zoom) ? 100 : Math.max(this.MIN_ZOOM, Math.min(zoom, this.MAX_ZOOM));
    }
    return 100;
  }
}
