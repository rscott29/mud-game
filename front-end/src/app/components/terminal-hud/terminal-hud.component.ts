import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';

import { ResponsiveService } from '../../services/responsive.service';
import { TerminalPresenterService } from '../../services/terminal-presenter.service';

/**
 * Mobile-only HUD accordion. The desktop layout uses {@link HudSidebarComponent}
 * and {@link HudPanelsComponent} positioned in the three-column shell.
 */
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
  readonly responsive = inject(ResponsiveService);
  readonly hudExpanded = signal(false);

  toggleHudExpanded(): void {
    this.hudExpanded.update(value => !value);
  }
}
