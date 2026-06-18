import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { MiniMapComponent } from '../mini-map/mini-map.component';
import { ResponsiveService } from '../../services/responsive.service';
import { TerminalPresenterService } from '../../services/terminal-presenter.service';

@Component({
  selector: 'app-hud-panels',
  standalone: true,
  imports: [MiniMapComponent],
  templateUrl: './hud-panels.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HudPanelsComponent {
  readonly view = inject(TerminalPresenterService);
  readonly responsive = inject(ResponsiveService);
}
