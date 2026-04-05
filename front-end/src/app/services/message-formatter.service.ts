import { Injectable, inject } from '@angular/core';

import { GameMessage, type TerminalMessageClass } from '../models/game-message';
import { type FormattedMessage } from './message-format.types';
import { TerminalCoreFormatterService } from './terminal-core-formatter.service';
import { TerminalProfileFormatterService } from './terminal-profile-formatter.service';
import { TerminalReferenceFormatterService } from './terminal-reference-formatter.service';
import { TerminalRoomFormatterService } from './terminal-room-formatter.service';

export type { FormattedMessage } from './message-format.types';

@Injectable({ providedIn: 'root' })
export class MessageFormatterService {
  private readonly coreFormatter = inject(TerminalCoreFormatterService);
  private readonly profileFormatter = inject(TerminalProfileFormatterService);
  private readonly referenceFormatter = inject(TerminalReferenceFormatterService);
  private readonly roomFormatter = inject(TerminalRoomFormatterService);

  formatSystem(text: string): FormattedMessage {
    return this.coreFormatter.formatSystem(text);
  }

  formatRoomDisplay(msg: GameMessage): FormattedMessage {
    return this.roomFormatter.formatRoomDisplay(msg);
  }

  formatAuthPrompt(message: string): FormattedMessage {
    return this.coreFormatter.formatAuthPrompt(message);
  }

  formatChatMessage(msg: GameMessage): FormattedMessage {
    return this.coreFormatter.formatChatMessage(msg);
  }

  formatWhoList(players: Parameters<TerminalProfileFormatterService['formatWhoList']>[0]): FormattedMessage {
    return this.profileFormatter.formatWhoList(players);
  }

  formatInventoryUpdate(
    items: Parameters<TerminalProfileFormatterService['formatInventoryUpdate']>[0]
  ): FormattedMessage {
    return this.profileFormatter.formatInventoryUpdate(items);
  }

  formatPlayerOverview(
    msg: Parameters<TerminalProfileFormatterService['formatPlayerOverview']>[0]
  ): FormattedMessage {
    return this.profileFormatter.formatPlayerOverview(msg);
  }

  formatHelpCard(isGod: boolean): FormattedMessage {
    return this.referenceFormatter.formatHelpCard(isGod);
  }

  formatNarrativeInlineMessage(message: string): FormattedMessage {
    return this.roomFormatter.formatNarrativeInlineMessage(message);
  }

  formatNarrativeEchoMessage(message: string): FormattedMessage {
    return this.roomFormatter.formatNarrativeEchoMessage(message);
  }

  formatRoomActionMessage(message: string): FormattedMessage {
    return this.roomFormatter.formatRoomActionMessage(message);
  }

  formatSocialActionMessage(message: string): FormattedMessage {
    return this.roomFormatter.formatSocialActionMessage(message);
  }

  formatModerationNotice(message: string): FormattedMessage {
    return this.coreFormatter.formatModerationNotice(message);
  }

  formatAmbientEventMessage(message: string): FormattedMessage {
    return this.roomFormatter.formatAmbientEventMessage(message);
  }

  formatCompanionDialogueMessage(message: string): FormattedMessage {
    return this.roomFormatter.formatCompanionDialogueMessage(message);
  }

  formatFallbackMessage(label: TerminalMessageClass, message: string): FormattedMessage {
    return this.roomFormatter.formatFallbackMessage(label, message);
  }

  formatRoomInlineFragment(msg: GameMessage): string {
    return this.roomFormatter.formatRoomInlineFragment(msg);
  }

  formatClassProgressionLoading(characterClass: string): FormattedMessage {
    return this.referenceFormatter.formatClassProgressionLoading(characterClass);
  }

  formatClassProgressionError(characterClass: string): FormattedMessage {
    return this.referenceFormatter.formatClassProgressionError(characterClass);
  }

  formatClassProgression(
    characterClass: string,
    playerLevel: number,
    maxLevel: number,
    skills: Parameters<TerminalReferenceFormatterService['formatClassProgression']>[3]
  ): FormattedMessage {
    return this.referenceFormatter.formatClassProgression(characterClass, playerLevel, maxLevel, skills);
  }
}
