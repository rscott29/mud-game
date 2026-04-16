import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  inject,
  signal,
} from '@angular/core';

import { TerminalPresenterService } from '../../services/terminal-presenter.service';

@Component({
  selector: 'app-terminal-hud',
  standalone: true,
  imports: [],
  templateUrl: './terminal-hud.component.html',
  styleUrl: './terminal-hud.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TerminalHudComponent {
  readonly view = inject(TerminalPresenterService);
  readonly isCompactHud = signal(false);
  readonly hudExpanded = signal(true);

  constructor() {
    this.syncResponsiveHud();
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.syncResponsiveHud();
  }

  toggleHudExpanded(): void {
    if (!this.isCompactHud()) {
      return;
    }

    this.hudExpanded.update(value => !value);
  }

  private syncResponsiveHud(): void {
    if (typeof window === 'undefined') {
      this.isCompactHud.set(false);
      this.hudExpanded.set(true);
      return;
    }

    const compactHud = window.innerWidth <= 720;
    const wasCompactHud = this.isCompactHud();

    this.isCompactHud.set(compactHud);

    if (compactHud !== wasCompactHud) {
      this.hudExpanded.set(!compactHud);
    }
  }
}
