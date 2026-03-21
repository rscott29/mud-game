import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { GameSocketService } from './game-socket.service';
import { TerminalMessageInterpreterService } from './terminal-message-interpreter.service';
import { TerminalMessageStore } from './terminal-message-store.service';
import { TerminalClassProgressionService } from './terminal-class-progression.service';
import { GameMessage } from '../models/game-message';

@Injectable()
export class TerminalFacade {
  private readonly socketService = inject(GameSocketService);
  private readonly interpreter = inject(TerminalMessageInterpreterService);
  private readonly store = inject(TerminalMessageStore);
  private readonly classProgression = inject(TerminalClassProgressionService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.socketService.connect();

    this.socketService.messages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(message => this.handleGameMessage(message));

    this.socketService.systemMessages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(text => this.store.addDisplayMessage(this.interpreter.interpretSystem(text)));
  }

  disconnect(): void {
    this.socketService.disconnect();
  }

  private handleGameMessage(message: GameMessage): void {
    const interpretation = this.interpreter.interpret(message);
    this.store.applyStateChanges(interpretation.stateChanges);

    switch (interpretation.kind) {
      case 'display':
        this.store.addDisplayMessage(interpretation.message);
        break;
      case 'room_display':
        this.store.upsertRoomMessage(interpretation.source);
        break;
      case 'room_inline':
        this.store.appendToActiveRoomMessage(
          interpretation.source,
          interpretation.inlineFragment,
          interpretation.fallback
        );
        break;
      case 'character_creation':
        this.store.setCharacterCreationData(interpretation.data);
        break;
      case 'class_progression':
        this.showClassProgression();
        break;
      case 'skip':
        break;
    }
  }

  private showClassProgression(): void {
    this.classProgression
      .request(this.socketService.playerStats())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(message => this.store.addDisplayMessage(message));
  }
}
