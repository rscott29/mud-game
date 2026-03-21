import { Component, inject } from '@angular/core';
import { TerminalComponent } from './components/terminal/terminal.component';
import { CommandCatalogService } from './services/command-catalog.service';
import { ZoomService } from './services/zoom.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [TerminalComponent],
  template: `
    <main class="game-layout" [style.fontSize.%]="zoomService.zoomLevel()">
      <app-terminal class="game-terminal" />
    </main>
  `,
  styles: [`
    :host {
      display: block;
      height: 100dvh;
      overflow: hidden;
    }

    .game-layout {
      height: 100%;
      padding: clamp(10px, 1.6vw, 18px);
    }

    .game-terminal {
      display: block;
      height: 100%;
    }
  `],
})
export class App {
  private readonly commandCatalog = inject(CommandCatalogService);
  readonly zoomService = inject(ZoomService);

  constructor() {
    this.commandCatalog.load();
  }
}
