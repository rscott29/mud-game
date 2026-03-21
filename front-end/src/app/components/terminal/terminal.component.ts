import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgClass } from '@angular/common';
import { take } from 'rxjs';

import { GameSocketService } from '../../services/game-socket.service';
import { CommandBuilderService } from '../../services/command-builder.service';
import {
  FormattedMessage,
  MessageFormatterService,
} from '../../services/message-formatter.service';
import { CharacterCreationDto, GameMessage } from '../../models/game-message';
import { SafeHtmlPipe } from '../../pipes/safe-html.pipe';
import { CharacterCreationComponent } from '../character-creation/character-creation.component';
import { escapeHtml } from '../../utils/html';
import { ZoomService } from '../../services/zoom.service';
import { SkillProgressionService } from '../../services/skill-progression.service';

export interface DisplayMessage {
  id: number;
  cssClass: string;
  html: string;
  roomId?: string;
  roomMessage?: string;
  room?: GameMessage['room'];
  roomType?: string;
}

@Component({
  selector: 'app-terminal',
  standalone: true,
  imports: [FormsModule, SafeHtmlPipe, NgClass, CharacterCreationComponent],
  templateUrl: './terminal.component.html',
  styleUrl: './terminal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TerminalComponent {
  @ViewChild('logEl') private logEl?: ElementRef<HTMLDivElement>;

  private readonly socketService = inject(GameSocketService);
  private readonly commandBuilder = inject(CommandBuilderService);
  private readonly messageFormatter = inject(MessageFormatterService);
  private readonly skillProgression = inject(SkillProgressionService);
  private readonly zoomService = inject(ZoomService);
  private readonly destroyRef = inject(DestroyRef);

  private nextId = 0;
  private activeRoomMessageId: number | null = null;
  private activeRoomId: string | null = null;

  readonly messages = signal<DisplayMessage[]>([]);
  readonly inputValue = signal('');
  readonly passwordMode = signal(false);
  readonly status = this.socketService.status;
  readonly playerStats = this.socketService.playerStats;
  readonly characterCreationData = signal<CharacterCreationDto | null>(null);
  readonly zoomLevel = this.zoomService.zoomLevel;

  readonly inputType = computed(() => (this.passwordMode() ? 'password' : 'text'));
  readonly promptLabel = computed(() => (this.passwordMode() ? 'pass' : 'cmd'));

  readonly placeholder = computed(() =>
    this.passwordMode() ? 'Whisper your password...' : 'Speak a command...'
  );

  readonly statusLabel = computed(() => {
    switch (this.status()) {
      case 'connected':
        return 'Connected';
      case 'reconnecting':
        return 'Reconnecting...';
      default:
        return 'Disconnected';
    }
  });

  readonly statusClass = computed(() => ({
    connected: this.status() === 'connected',
    reconnecting: this.status() === 'reconnecting',
    disconnected: this.status() !== 'connected' && this.status() !== 'reconnecting',
  }));

  readonly classLabel = computed(() => {
    const value = this.playerStats()?.characterClass ?? '';
    return value
      .split(/[_\s-]+/)
      .filter(Boolean)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  });

  readonly levelLabel = computed(() => {
    const stats = this.playerStats();
    if (!stats) {
      return 'Awaiting profile';
    }

    if (stats.isGod || stats.maxLevel <= 0 || stats.level > stats.maxLevel) {
      return `Level ${stats.level}`;
    }

    return `Level ${stats.level} / ${stats.maxLevel}`;
  });

  readonly hudHealth = computed(() => this.formatResource('HP', this.playerStats()?.health, this.playerStats()?.maxHealth));
  readonly hudMana = computed(() => this.formatResource('MP', this.playerStats()?.mana, this.playerStats()?.maxMana));
  readonly hudMovement = computed(() => this.formatResource('MV', this.playerStats()?.movement, this.playerStats()?.maxMovement));
  readonly xpPercent = computed(() => {
    const stats = this.playerStats();
    if (!stats) {
      return 0;
    }
    if (stats.level >= stats.maxLevel || stats.xpForNextLevel <= 0) {
      return 100;
    }
    return Math.max(0, Math.min(100, Math.round((stats.xpProgress / stats.xpForNextLevel) * 100)));
  });
  readonly xpLabel = computed(() => {
    const stats = this.playerStats();
    if (!stats) {
      return 'XP offline';
    }
    if (stats.level >= stats.maxLevel) {
      return 'Max level';
    }
    return `${stats.xpProgress} / ${stats.xpForNextLevel} XP`;
  });

  constructor() {
    this.socketService.connect();

    this.socketService.messages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(msg => {
        const stateChanges = this.messageFormatter.getStateChanges(msg);
        if (stateChanges) {
          if (stateChanges.clearMessages) {
            this.messages.set([]);
            this.activeRoomMessageId = null;
            this.activeRoomId = null;
          }
          if (stateChanges.passwordMode !== undefined) {
            this.passwordMode.set(stateChanges.passwordMode);
          }
        }

        const action = this.messageFormatter.format(msg);
        switch (action.type) {
          case 'display':
            this.addFormattedMessage(action.message, msg);
            break;
          case 'character_creation':
            this.characterCreationData.set(msg.characterCreation ?? null);
            break;
          case 'class_progression':
            this.showClassProgression();
            break;
          case 'skip':
            break;
        }
      });

    this.socketService.systemMessages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(text => this.addFormattedMessage(this.messageFormatter.formatSystem(text)));

    effect(() => {
      this.messages();
      this.characterCreationData();
      setTimeout(() => this.scrollToBottom(), 40);
    });
  }

  @HostListener('window:beforeunload')
  onBeforeUnload(): void {
    this.socketService.disconnect();
  }

  ngOnDestroy(): void {
    this.socketService.disconnect();
  }

  send(): void {
    const raw = this.inputValue().trim();
    if (!raw) {
      return;
    }

    this.inputValue.set('');

    const { payload, echo, maskEcho } = this.commandBuilder.build(raw, this.passwordMode());
    this.addMsg('SENT', `&gt; ${maskEcho ? '********' : escapeHtml(echo)}`);
    this.socketService.sendRaw(payload);
  }

  onEnter(): void {
    this.send();
  }

  trackMessage = (_: number, msg: DisplayMessage) => msg.id;

  onCharacterCreationComplete(selection: string): void {
    this.characterCreationData.set(null);
    this.socketService.sendRaw(JSON.stringify({ input: selection }));
  }

  decreaseZoom(): void {
    this.zoomService.decreaseZoom();
  }

  increaseZoom(): void {
    this.zoomService.increaseZoom();
  }

  private showClassProgression(): void {
    const stats = this.playerStats();
    const characterClass = stats?.characterClass ?? '';

    if (!characterClass) {
      this.addFormattedMessage(
        this.messageFormatter.formatClassProgressionError('current class')
      );
      return;
    }

    this.addFormattedMessage(
      this.messageFormatter.formatClassProgressionLoading(characterClass)
    );

    this.skillProgression
      .getSkillsForClass(characterClass)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: response => {
          if (!response) {
            this.addFormattedMessage(
              this.messageFormatter.formatClassProgressionError(characterClass)
            );
            return;
          }

          const latestStats = this.playerStats();
          this.addFormattedMessage(
            this.messageFormatter.formatClassProgression(
              response.characterClass || characterClass,
              latestStats?.level ?? stats?.level ?? 1,
              latestStats?.maxLevel ?? stats?.maxLevel ?? 0,
              response.skills ?? []
            )
          );
        },
        error: () => {
          this.addFormattedMessage(
            this.messageFormatter.formatClassProgressionError(characterClass)
          );
        },
      });
  }

  private formatResource(label: string, current?: number, max?: number): string {
    if (current == null || max == null) {
      return `${label} --`;
    }

    if (this.playerStats()?.isGod) {
      return `${label} INF`;
    }

    return `${label} ${current}/${max}`;
  }

  private scrollToBottom(): void {
    const el = this.logEl?.nativeElement;
    if (!el) {
      return;
    }
    el.scrollTop = el.scrollHeight;
  }

  private addFormattedMessage(message: FormattedMessage, source?: GameMessage): void {
    if (source && this.isRoomDisplayMessage(source)) {
      this.upsertRoomMessage(source);
      return;
    }

    if (source && this.isRoomInlineMessage(source) && this.activeRoomMessageId !== null) {
      this.appendToActiveRoomMessage(source);
      return;
    }

    this.addMsg(message.cssClass, message.html);
  }

  private addMsg(cssClass: string, html: string): void {
    this.messages.update(list => [...list, { id: ++this.nextId, cssClass, html }]);
  }

  private upsertRoomMessage(source: GameMessage): void {
    const roomId = source.room?.id;
    if (!roomId) {
      const fallback = this.messageFormatter.format(source);
      if (fallback.type === 'display') {
        this.addMsg(fallback.message.cssClass, fallback.message.html);
      }
      return;
    }

    const shouldMergeIntoActiveRoom =
      this.activeRoomId === roomId
      && this.activeRoomMessageId !== null
      && !this.shouldStartFreshRoomMessage(source);

    this.messages.update(list => {
      if (!shouldMergeIntoActiveRoom) {
        const nextId = ++this.nextId;
        this.activeRoomId = roomId;
        this.activeRoomMessageId = nextId;
        const formatted = this.messageFormatter.format(source);
        if (formatted.type !== 'display') {
          return list;
        }
        return [
          ...list,
          {
            id: nextId,
            cssClass: formatted.message.cssClass,
            html: formatted.message.html,
            roomId,
            roomMessage: source.message ?? '',
            room: source.room,
            roomType: source.type,
          },
        ];
      }

      const targetIndex = list.findIndex(message => message.id === this.activeRoomMessageId);
      if (targetIndex === -1) {
        const nextId = ++this.nextId;
        this.activeRoomId = roomId;
        this.activeRoomMessageId = nextId;
        const formatted = this.messageFormatter.format(source);
        if (formatted.type !== 'display') {
          return list;
        }
        return [
          ...list,
          {
            id: nextId,
            cssClass: formatted.message.cssClass,
            html: formatted.message.html,
            roomId,
            roomMessage: source.message ?? '',
            room: source.room,
            roomType: source.type,
          },
        ];
      }

      const existing = list[targetIndex];
      const mergedSource: GameMessage = {
        ...source,
        message: this.joinRoomMessages(existing.roomMessage, source.message),
      };
      const formatted = this.messageFormatter.format(mergedSource);
      if (formatted.type !== 'display') {
        return list;
      }

      const updated = [...list];
      updated[targetIndex] = {
        ...existing,
        cssClass: formatted.message.cssClass,
        html: formatted.message.html,
        roomId,
        roomMessage: mergedSource.message ?? '',
        room: source.room,
        roomType: source.type,
      };
      return this.moveMessageToEnd(updated, targetIndex);
    });
  }

  private isRoomDisplayMessage(message: GameMessage): boolean {
    return (
      (message.type === 'ROOM_UPDATE' || message.type === 'ROOM_REFRESH' || message.type === 'WELCOME')
      && !!message.room
    );
  }

  private shouldStartFreshRoomMessage(message: GameMessage): boolean {
    return message.type === 'ROOM_REFRESH';
  }

  private isRoomInlineMessage(message: GameMessage): boolean {
    return message.type === 'NARRATIVE'
      || message.type === 'ROOM_ACTION'
      || message.type === 'AMBIENT_EVENT'
      || message.type === 'COMPANION_DIALOGUE';
  }

  private appendToActiveRoomMessage(source: GameMessage): void {
    this.messages.update(list => {
      const targetIndex = list.findIndex(message => message.id === this.activeRoomMessageId);
      if (targetIndex === -1) {
        const formatted = this.messageFormatter.format(source);
        if (formatted.type === 'display') {
          return [...list, { id: ++this.nextId, cssClass: formatted.message.cssClass, html: formatted.message.html }];
        }
        return list;
      }

      const existing = list[targetIndex];
      if (!existing.room) {
        const formatted = this.messageFormatter.format(source);
        if (formatted.type === 'display') {
          return [...list, { id: ++this.nextId, cssClass: formatted.message.cssClass, html: formatted.message.html }];
        }
        return list;
      }

      const mergedSource: GameMessage = {
        type: existing.roomType ?? 'ROOM_UPDATE',
        room: existing.room,
        message: this.joinRoomMessages(
          existing.roomMessage,
          this.messageFormatter.formatRoomInlineFragment(source)
        ),
      };
      const formatted = this.messageFormatter.format(mergedSource);
      if (formatted.type !== 'display') {
        return list;
      }

      const updated = [...list];
      updated[targetIndex] = {
        ...existing,
        cssClass: formatted.message.cssClass,
        html: formatted.message.html,
        roomMessage: mergedSource.message ?? '',
      };
      return this.moveMessageToEnd(updated, targetIndex);
    });
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

  private joinRoomMessages(existing?: string, incoming?: string): string {
    if (!existing?.trim()) {
      return incoming ?? '';
    }
    if (!incoming?.trim()) {
      return existing;
    }
    return `${existing}<br><br>${incoming}`;
  }
}
