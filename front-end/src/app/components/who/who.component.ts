import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';

import { GameSocketService } from '../../services/game-socket.service';
import { WhoPlayerDto } from '../../models/game-message';

@Component({
  selector: 'app-who',
  standalone: true,
  imports: [],
  templateUrl: './who.component.html',
  styleUrl: './who.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WhoComponent {
  private readonly socketService = inject(GameSocketService);

  readonly players  = this.socketService.whoPlayers;
  readonly count    = computed(() => this.players().length);
  readonly isEmpty  = computed(() => this.players().length === 0);

  trackPlayer = (_: number, p: WhoPlayerDto) => p.name;

  close(): void {
    this.socketService.whoOpen.set(false);
  }
}
