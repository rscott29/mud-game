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

import { GameSocketService } from '../../services/game-socket.service';
import { CommandBuilderService } from '../../services/command-builder.service';
import { MessageFormatterService } from '../../services/message-formatter.service';
import { CharacterCreationDto } from '../../models/game-message';
import { SafeHtmlPipe } from '../../pipes/safe-html.pipe';
import { CharacterCreationComponent } from '../character-creation/character-creation.component';
import { escapeHtml } from '../../utils/html';

export interface DisplayMessage {
  id: number;
  cssClass: string;
  html: string;
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
  private readonly destroyRef = inject(DestroyRef);

  private nextId = 0;

  readonly messages = signal<DisplayMessage[]>([]);
  readonly inputValue = signal('');
  readonly passwordMode = signal(false);
  readonly status = this.socketService.status;
  readonly characterCreationData = signal<CharacterCreationDto | null>(null);

  readonly inputType = computed(() => this.passwordMode() ? 'password' : 'text');

  readonly placeholder = computed(() =>
    this.passwordMode()
      ? 'Enter password…'
      : 'Type a command…'
  );

  readonly statusLabel = computed(() => {
    switch (this.status()) {
      case 'connected': return 'Connected';
      case 'reconnecting': return 'Reconnecting…';
      default: return 'Disconnected';
    }
  });

  readonly statusClass = computed(() => ({
    connected: this.status() === 'connected',
    reconnecting: this.status() === 'reconnecting',
    disconnected: this.status() !== 'connected' && this.status() !== 'reconnecting',
  }));

  constructor() {
    this.socketService.connect();

    this.socketService.messages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(msg => {
        // Handle state changes (clear messages, password mode)
        const stateChanges = this.messageFormatter.getStateChanges(msg);
        if (stateChanges) {
          if (stateChanges.clearMessages) {
            this.messages.set([]);
          }
          if (stateChanges.passwordMode !== undefined) {
            this.passwordMode.set(stateChanges.passwordMode);
          }
        }

        // Format and display the message
        const action = this.messageFormatter.format(msg);
        switch (action.type) {
          case 'display':
            this.addMsg(action.message.cssClass, action.message.html);
            break;
          case 'character_creation':
            this.characterCreationData.set(msg.characterCreation ?? null);
            break;
          case 'skip':
            // No terminal output needed
            break;
        }
      });

    this.socketService.systemMessages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(text => this.addMsg('SYSTEM', escapeHtml(text)));

    effect(() => {
      this.messages();
      setTimeout(() => this.scrollToBottom(), 50);
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
    if (!raw) return;

    this.inputValue.set('');

    const { payload, echo, maskEcho } = this.commandBuilder.build(raw, this.passwordMode());
    this.addMsg('SENT', `&gt; ${maskEcho ? '••••••••' : escapeHtml(echo)}`);
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

  private scrollToBottom(): void {
    const el = this.logEl?.nativeElement;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }

  private addMsg(cssClass: string, html: string): void {
    this.messages.update(list => [
      ...list,
      { id: ++this.nextId, cssClass, html },
    ]);
  }
}
