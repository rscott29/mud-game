import { Component, inject } from '@angular/core';
import { TerminalComponent } from './components/terminal/terminal.component';
import { InventoryComponent } from './components/inventory/inventory.component';
import { WhoComponent } from './components/who/who.component';
import { HelpComponent } from './components/help/help.component';
import { PlayerStatsComponent } from './components/player-stats/player-stats.component';
import { ZoomService } from './services/zoom.service';
import { GameSocketService } from './services/game-socket.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [TerminalComponent, InventoryComponent, WhoComponent, HelpComponent, PlayerStatsComponent],
  template: `
    <div class="game-layout" [style.fontSize.%]="zoomService.zoomLevel()">
      <div class="game-main">
        <app-terminal class="game-terminal" />
        <app-player-stats />
      </div>
      @if (socketService.inventoryOpen()) {
        <app-inventory class="game-inventory" />
      }
      @if (socketService.whoOpen()) {
        <app-who class="game-who" />
      }
      @if (socketService.helpOpen()) {
        <app-help />
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      height: 100dvh;
      overflow: hidden;
    }

    .game-layout {
      display: flex;
      height: 100%;
    }

    .game-main {
      flex: 1 1 0;
      min-width: 0;
      display: flex;
      flex-direction: column;
    }

    .game-terminal {
      flex: 1 1 0;
      min-width: 0;
    }

    .game-who {
      flex: 0 0 260px;
      width: 260px;
    }
  `],
})
export class App {
  readonly socketService = inject(GameSocketService);
  readonly zoomService = inject(ZoomService);
}
