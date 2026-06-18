import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ResponsiveService } from '../../services/responsive.service';
import { TerminalPresenterService } from '../../services/terminal-presenter.service';

@Component({
  selector: 'app-hud-sidebar',
  standalone: true,
  imports: [],
  templateUrl: './hud-sidebar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HudSidebarComponent {
  readonly view = inject(TerminalPresenterService);
  readonly responsive = inject(ResponsiveService);
}
