import { Injectable, inject } from '@angular/core';

import { CharacterCreationDto, GameMessage, GAME_MESSAGE_TYPES } from '../models/game-message';
import { FormattedMessage, MessageFormatterService } from './message-formatter.service';
import { TerminalStateChanges } from './terminal-message-store.service';

export type TerminalMessageInterpretation =
  | {
      kind: 'display';
      stateChanges: TerminalStateChanges | null;
      message: FormattedMessage;
    }
  | {
      kind: 'help';
      stateChanges: TerminalStateChanges | null;
      isGod: boolean;
    }
  | {
      kind: 'room_display';
      stateChanges: TerminalStateChanges | null;
      source: GameMessage;
    }
  | {
      kind: 'room_inline';
      stateChanges: TerminalStateChanges | null;
      source: GameMessage;
      inlineFragment: string;
      fallback: FormattedMessage;
    }
  | {
      kind: 'character_creation';
      stateChanges: TerminalStateChanges | null;
      data: CharacterCreationDto | null;
    }
  | {
      kind: 'class_progression';
      stateChanges: TerminalStateChanges | null;
    }
  | {
      kind: 'skip';
      stateChanges: TerminalStateChanges | null;
    };

@Injectable()
export class TerminalMessageInterpreterService {
  private readonly formatter = inject(MessageFormatterService);

  interpret(message: GameMessage): TerminalMessageInterpretation {
    const type = message.type ?? GAME_MESSAGE_TYPES.NARRATIVE;
    const stateChanges = this.getStateChanges(message);

    switch (type) {
      case GAME_MESSAGE_TYPES.WELCOME:
      case GAME_MESSAGE_TYPES.ROOM_UPDATE:
      case GAME_MESSAGE_TYPES.ROOM_REFRESH:
        return { kind: 'room_display', stateChanges, source: message };

      case GAME_MESSAGE_TYPES.AUTH_PROMPT:
        return {
          kind: 'display',
          stateChanges,
          message: this.formatter.formatAuthPrompt(message.message ?? ''),
        };

      case GAME_MESSAGE_TYPES.CHAT_ROOM:
      case GAME_MESSAGE_TYPES.CHAT_WORLD:
      case GAME_MESSAGE_TYPES.CHAT_DM:
        return {
          kind: 'display',
          stateChanges,
          message: this.formatter.formatChatMessage(message),
        };

      case GAME_MESSAGE_TYPES.WHO_LIST:
        return {
          kind: 'display',
          stateChanges,
          message: this.formatter.formatWhoList(message.whoPlayers ?? []),
        };

      case GAME_MESSAGE_TYPES.INVENTORY_UPDATE:
        return {
          kind: 'display',
          stateChanges,
          message: this.formatter.formatInventoryUpdate(message.inventory ?? []),
        };

      case GAME_MESSAGE_TYPES.HELP:
        return {
          kind: 'help',
          stateChanges,
          isGod: (message.message ?? '').trim().toLowerCase() === 'god',
        };

      case GAME_MESSAGE_TYPES.STAT_UPDATE:
        return { kind: 'skip', stateChanges };

      case GAME_MESSAGE_TYPES.CLASS_PROGRESSION:
        return { kind: 'class_progression', stateChanges };

      case GAME_MESSAGE_TYPES.CHARACTER_CREATION:
        return {
          kind: 'character_creation',
          stateChanges,
          data: message.characterCreation ?? null,
        };

      case GAME_MESSAGE_TYPES.MODERATION_NOTICE:
        return {
          kind: 'display',
          stateChanges,
          message: this.formatter.formatModerationNotice(message.message ?? ''),
        };

      case GAME_MESSAGE_TYPES.NARRATIVE:
        return {
          kind: 'room_inline',
          stateChanges,
          source: message,
          inlineFragment: this.formatter.formatRoomInlineFragment(message),
          fallback: this.formatter.formatNarrativeInlineMessage(message.message ?? ''),
        };

      case GAME_MESSAGE_TYPES.ROOM_ACTION:
        return {
          kind: 'room_inline',
          stateChanges,
          source: message,
          inlineFragment: this.formatter.formatRoomInlineFragment(message),
          fallback: this.formatter.formatRoomActionMessage(message.message ?? ''),
        };

      case GAME_MESSAGE_TYPES.SOCIAL_ACTION:
        return {
          kind: 'room_inline',
          stateChanges,
          source: message,
          inlineFragment: this.formatter.formatRoomInlineFragment(message),
          fallback: this.formatter.formatSocialActionMessage(message.message ?? ''),
        };

      case GAME_MESSAGE_TYPES.AMBIENT_EVENT:
        return {
          kind: 'room_inline',
          stateChanges,
          source: message,
          inlineFragment: this.formatter.formatRoomInlineFragment(message),
          fallback: this.formatter.formatAmbientEventMessage(message.message ?? ''),
        };

      case GAME_MESSAGE_TYPES.COMPANION_DIALOGUE:
        return {
          kind: 'room_inline',
          stateChanges,
          source: message,
          inlineFragment: this.formatter.formatRoomInlineFragment(message),
          fallback: this.formatter.formatCompanionDialogueMessage(message.message ?? ''),
        };

      default:
        return {
          kind: 'display',
          stateChanges,
          message: this.formatter.formatFallbackMessage(type, message.message ?? JSON.stringify(message)),
        };
    }
  }

  interpretSystem(text: string): FormattedMessage {
    return this.formatter.formatSystem(text);
  }

  private getStateChanges(msg: GameMessage): TerminalStateChanges | null {
    const type = msg.type ?? GAME_MESSAGE_TYPES.NARRATIVE;

    if (type === GAME_MESSAGE_TYPES.WELCOME) {
      return { clearMessages: true, passwordMode: false };
    }

    if (type === GAME_MESSAGE_TYPES.AUTH_PROMPT) {
      const clearMessages = (msg.message ?? '').toLowerCase().includes('username');
      return { clearMessages, passwordMode: msg.mask === true };
    }

    if (type === GAME_MESSAGE_TYPES.CHARACTER_CREATION) {
      return { clearMessages: true, passwordMode: false };
    }

    return null;
  }
}
