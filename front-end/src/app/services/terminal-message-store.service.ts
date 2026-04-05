import { Injectable, effect, inject, signal, untracked } from '@angular/core';

import {
  CharacterCreationDto,
  GameMessage,
  GAME_MESSAGE_TYPES,
  type TerminalMessageClass,
} from '../models/game-message';
import { FormattedMessage, MessageFormatterService } from './message-formatter.service';
import { CommandCatalogService } from './command-catalog.service';

export interface DisplayMessage {
  id: number;
  cssClass: TerminalMessageClass;
  html: string;
  helpIsGod?: boolean;
  roomId?: string;
  roomMessage?: string;
  room?: GameMessage['room'];
  roomType?: GameMessage['type'];
}

export interface TerminalStateChanges {
  clearMessages?: boolean;
  passwordMode?: boolean;
}

@Injectable()
export class TerminalMessageStore {
  private readonly formatter = inject(MessageFormatterService);
  private readonly commandCatalog = inject(CommandCatalogService);

  private nextId = 0;
  private activeRoomMessageId: number | null = null;
  private activeRoomId: string | null = null;

  readonly messages = signal<DisplayMessage[]>([]);
  readonly passwordMode = signal(false);
  readonly characterCreationData = signal<CharacterCreationDto | null>(null);

  constructor() {
    effect(() => {
      this.commandCatalog.catalogVersion();
      untracked(() => this.refreshHelpMessages());
    });
  }

  applyStateChanges(stateChanges: TerminalStateChanges | null): void {
    if (!stateChanges) {
      return;
    }

    if (stateChanges.clearMessages) {
      this.clearMessages();
    }

    if (stateChanges.passwordMode !== undefined) {
      this.passwordMode.set(stateChanges.passwordMode);
    }
  }

  setCharacterCreationData(data: CharacterCreationDto | null): void {
    this.characterCreationData.set(data);
  }

  clearCharacterCreation(): void {
    this.characterCreationData.set(null);
  }

  addDisplayMessage(message: FormattedMessage): void {
    this.messages.update(list => [...list, { id: ++this.nextId, cssClass: message.cssClass, html: message.html }]);
  }

  addHelpMessage(isGod: boolean): void {
    const formatted = this.formatter.formatHelpCard(isGod);
    this.messages.update(list => [
      ...list,
      {
        id: ++this.nextId,
        cssClass: formatted.cssClass,
        html: formatted.html,
        helpIsGod: isGod,
      },
    ]);
  }

  upsertRoomMessage(source: GameMessage): void {
    const roomId = source.room?.id;
    if (!roomId) {
      this.addDisplayMessage(this.formatter.formatRoomDisplay(source));
      return;
    }

    const shouldMergeIntoActiveRoom =
      this.activeRoomId === roomId
      && this.activeRoomMessageId !== null
      && !this.shouldStartFreshRoomMessage(source);

    this.messages.update(list => {
      if (!shouldMergeIntoActiveRoom) {
        return [...list, this.createRoomMessage(roomId, source)];
      }

      const targetIndex = list.findIndex(message => message.id === this.activeRoomMessageId);
      if (targetIndex === -1) {
        return [...list, this.createRoomMessage(roomId, source)];
      }

      const existing = list[targetIndex];
      const mergedSource: GameMessage = {
        ...source,
        message: this.joinRoomMessages(existing.roomMessage, source.message),
      };
      const formatted = this.formatter.formatRoomDisplay(mergedSource);

      const updated = [...list];
      updated[targetIndex] = {
        ...existing,
        cssClass: formatted.cssClass,
        html: formatted.html,
        roomId,
        roomMessage: mergedSource.message ?? '',
        room: source.room,
        roomType: source.type,
      };
      return this.moveMessageToEnd(updated, targetIndex);
    });
  }

  appendToActiveRoomMessage(source: GameMessage, inlineFragment: string, fallback: FormattedMessage): void {
    if (this.activeRoomMessageId === null) {
      this.addDisplayMessage(fallback);
      return;
    }

    this.messages.update(list => {
      const targetIndex = list.findIndex(message => message.id === this.activeRoomMessageId);
      if (targetIndex === -1) {
        return [...list, { id: ++this.nextId, cssClass: fallback.cssClass, html: fallback.html }];
      }

      const existing = list[targetIndex];
      if (!existing.room) {
        return [...list, { id: ++this.nextId, cssClass: fallback.cssClass, html: fallback.html }];
      }

      const mergedSource: GameMessage = {
        type: existing.roomType ?? GAME_MESSAGE_TYPES.ROOM_UPDATE,
        room: existing.room,
        message: this.joinRoomMessages(
          existing.roomMessage,
          inlineFragment,
          this.inlineSeparatorFor(source)
        ),
      };
      const formatted = this.formatter.formatRoomDisplay(mergedSource);

      const updated = [...list];
      updated[targetIndex] = {
        ...existing,
        cssClass: formatted.cssClass,
        html: formatted.html,
        roomMessage: mergedSource.message ?? '',
      };
      return this.moveMessageToEnd(updated, targetIndex);
    });
  }

  private clearMessages(): void {
    this.messages.set([]);
    this.activeRoomMessageId = null;
    this.activeRoomId = null;
  }

  private refreshHelpMessages(): void {
    this.messages.update(list => {
      let changed = false;
      const updated = list.map(message => {
        if (message.cssClass !== GAME_MESSAGE_TYPES.HELP || message.helpIsGod === undefined) {
          return message;
        }

        changed = true;
        const formatted = this.formatter.formatHelpCard(message.helpIsGod);
        return {
          ...message,
          cssClass: formatted.cssClass,
          html: formatted.html,
        };
      });

      return changed ? updated : list;
    });
  }

  private createRoomMessage(roomId: string, source: GameMessage): DisplayMessage {
    const nextId = ++this.nextId;
    this.activeRoomId = roomId;
    this.activeRoomMessageId = nextId;

    const formatted = this.formatter.formatRoomDisplay(source);
    return {
      id: nextId,
      cssClass: formatted.cssClass,
      html: formatted.html,
      roomId,
      roomMessage: source.message ?? '',
      room: source.room,
      roomType: source.type,
    };
  }

  private shouldStartFreshRoomMessage(message: GameMessage): boolean {
    return message.type === GAME_MESSAGE_TYPES.ROOM_REFRESH;
  }

  private moveMessageToEnd(list: DisplayMessage[], index: number): DisplayMessage[] {
    if (index < 0 || index >= list.length || index === list.length - 1) {
      return list;
    }

    const updated = [...list];
    const [entry] = updated.splice(index, 1);
    updated.push(entry);
    return updated;
  }

  private inlineSeparatorFor(message: GameMessage): string {
    return message.type === GAME_MESSAGE_TYPES.NARRATIVE_ECHO ? '<br>' : '<br><br>';
  }

  private joinRoomMessages(existing?: string, incoming?: string, separator = '<br><br>'): string {
    if (!existing?.trim()) {
      return incoming ?? '';
    }
    if (!incoming?.trim()) {
      return existing;
    }
    return `${existing}${separator}${incoming}`;
  }
}
