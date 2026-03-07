import { Component, inject } from '@angular/core';
import { TerminalComponent } from './components/terminal/terminal.component';
import { InventoryComponent } from './components/inventory/inventory.component';
import { WhoComponent } from './components/who/who.component';
import { HelpComponent } from './components/help/help.component';
import { GameSocketService } from './services/game-socket.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [TerminalComponent, InventoryComponent, WhoComponent, HelpComponent],
  template: `
    <div class="game-layout">
      <app-terminal class="game-terminal" />
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
    :host { display: block; height: 100dvh; }
    .game-layout {
      display: flex;
      height: 100%;
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
}
