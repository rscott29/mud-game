import { DestroyRef, Injectable, inject, signal } from '@angular/core';

/**
 * Tracks viewport breakpoints so HUD components can decide whether to render
 * the desktop multi-column shell or collapse into the mobile accordion.
 *
 * <p>Provided per {@code TerminalComponent} (not root) so each test fixture
 * gets a fresh instance reflecting the test's chosen window width.</p>
 */
@Injectable()
export class ResponsiveService {
  private static readonly COMPACT_BREAKPOINT_PX = 720;
  private readonly destroyRef = inject(DestroyRef);

  readonly isCompact = signal(this.computeCompact());

  constructor() {
    if (typeof window !== 'undefined') {
      const handler = () => this.isCompact.set(this.computeCompact());
      window.addEventListener('resize', handler);
      this.destroyRef.onDestroy(() => window.removeEventListener('resize', handler));
    }
  }

  private computeCompact(): boolean {
    if (typeof window === 'undefined') {
      return false;
    }
    return window.innerWidth <= ResponsiveService.COMPACT_BREAKPOINT_PX;
  }
}
