import { Injectable, inject, signal } from '@angular/core';

import { GameSocketService } from './game-socket.service';
import { CommandBuilderService } from './command-builder.service';
import { TerminalMessageStore } from './terminal-message-store.service';
import { escapeHtml } from '../utils/html';
import { TERMINAL_MESSAGE_CLASSES } from '../models/game-message';

@Injectable()
export class TerminalInputService {
  private readonly socketService = inject(GameSocketService);
  private readonly commandBuilder = inject(CommandBuilderService);
  private readonly store = inject(TerminalMessageStore);

  readonly inputValue = signal('');

  send(): void {
    const raw = this.inputValue().trim();
    if (!raw) {
      return;
    }

    this.inputValue.set('');

    const { payload, echo, maskEcho } = this.commandBuilder.build(raw, this.store.passwordMode());
    this.store.addDisplayMessage({
      cssClass: TERMINAL_MESSAGE_CLASSES.SENT,
      html: `&gt; ${maskEcho ? '********' : escapeHtml(echo)}`,
    });
    this.socketService.sendRaw(payload);
  }

  completeCharacterCreation(selection: string): void {
    this.store.clearCharacterCreation();
    this.socketService.sendRaw(JSON.stringify({ input: selection }));
  }
}
