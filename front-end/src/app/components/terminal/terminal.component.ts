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

import { GameSocketService } from '../../services/game-socket.service';
import { GameMessage } from '../../models/game-message';
import { SafeHtmlPipe } from '../../pipes/safe-html.pipe';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgClass } from '@angular/common';

export interface DisplayMessage {
  id: number;
  cssClass: string;
  html: string;
}

/**
 * Commands that can be recognized and sent directly as structured requests.
 * These are commands with no arguments, or where arguments don't semantically matter.
 */
const DIRECT_COMMANDS = new Set([
  'n', 's', 'e', 'w', 'u', 'd',
  'north', 'south', 'east', 'west', 'up', 'down',
  'go', 'move',
  'inventory', 'inv', 'i',
  'spawn',
  'deleteitem', 'delitem', 'deleteinv', 'destroyitem',
  'teleport', 'telport', 'teleprot', 'tp', 'warp', 'goto',
  'summon', 'call',
  'kick', 'remove',
  'help', '?',
  'logout', 'logoff', 'quit', 'exit',
  'who',
]);

/**
 * Commands that should ALWAYS be sent as natural language (input field),
 * even if recognized. This lets the AI resolver handle semantic understanding
 * of targets (items, NPCs, room features), typos, descriptions, etc.
 *
 * Examples: "look at the stone", "take shiny sword", "talk to the guard"
 */
const AI_RESOLVED_COMMANDS = new Set([
  'look', 'l', 'examine', 'x',
  'talk', 'greet',
  'take', 'get', 'pickup', 'pick', 'grab', 'snatch', 'lift', 'collect', 'steal',
  'drop', 'discard', 'toss', 'leave',
  'investigate', 'search',
]);

function esc(str: string): string {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/**
 * Escapes the string for safe HTML insertion, then re-opens a curated
 * allowlist of presentational tags so game content can use light markup.
 * Only <em>, <i>, <b>, <strong>, and <br> (including self-closing) are
 * permitted; everything else stays escaped.
 */
const MARKUP_RE = /&lt;(\/?(?:em|i|b|strong|br|ul|ol|li)\s*\/?)&gt;/g;
function renderMarkup(str: string): string {
  return esc(str).replace(MARKUP_RE, '<$1>');
}

@Component({
  selector: 'app-terminal',
  standalone: true,
  imports: [FormsModule, SafeHtmlPipe, NgClass],
  templateUrl: './terminal.component.html',
  styleUrl: './terminal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TerminalComponent {
  @ViewChild('logEl') private logEl?: ElementRef<HTMLDivElement>;

  private readonly socketService = inject(GameSocketService);
  private readonly destroyRef = inject(DestroyRef);

  private nextId = 0;

  readonly messages = signal<DisplayMessage[]>([]);
  readonly inputValue = signal('');
  readonly passwordMode = signal(false);
  readonly status = this.socketService.status;

  readonly inputType = computed(() => this.passwordMode() ? 'password' : 'text');

  readonly placeholder = computed(() =>
    this.passwordMode()
      ? 'Enter password…'
      : 'Type a command or speak naturally…'
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
      .subscribe(msg => this.renderMessage(msg));

    this.socketService.systemMessages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(text => this.addMsg('SYSTEM', esc(text)));

    effect(() => {
      this.messages();
      queueMicrotask(() => this.scrollToBottom());
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

    const { payload, echo, maskEcho } = this.buildPayload(raw);
    this.addMsg('SENT', `&gt; ${maskEcho ? '••••••••' : esc(echo)}`);
    this.socketService.send(payload);
  }

  onEnter(): void {
    this.send();
  }

  trackMessage = (_: number, msg: DisplayMessage) => msg.id;

  private scrollToBottom(): void {
    const el = this.logEl?.nativeElement;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }

  private buildPayload(raw: string): { payload: string; echo: string; maskEcho: boolean } {
    if (this.passwordMode()) {
      return {
        payload: JSON.stringify({ input: raw }),
        echo: raw,
        maskEcho: true,
      };
    }

    if (raw.startsWith('{')) {
      return { payload: raw, echo: raw, maskEcho: false };
    }

    if (raw.startsWith('/')) {
      const [command, ...args] = raw.trim().split(/\s+/);
      const cmd = command.toLowerCase();
      return {
        payload: JSON.stringify(args.length ? { command: cmd, args } : { command: cmd }),
        echo: raw,
        maskEcho: false,
      };
    }

    const [first = '', ...args] = raw.split(/\s+/);
    const cmd = first.toLowerCase();

    // Commands that target items/NPCs should always use AI resolution
    if (AI_RESOLVED_COMMANDS.has(cmd)) {
      return {
        payload: JSON.stringify({ input: raw }),
        echo: raw,
        maskEcho: false,
      };
    }

    // Simple direct commands
    if (DIRECT_COMMANDS.has(cmd)) {
      return {
        payload: JSON.stringify(args.length ? { command: cmd, args } : { command: cmd }),
        echo: raw,
        maskEcho: false,
      };
    }

    // Unknown command → natural language
    return {
      payload: JSON.stringify({ input: raw }),
      echo: raw,
      maskEcho: false,
    };
  }

  private renderMessage(msg: GameMessage): void {
    const type = msg.type ?? 'MESSAGE';

    switch (type) {
      case 'WELCOME':
        this.messages.set([]);
        this.passwordMode.set(false);
        this.renderRoomish(msg);
        return;

      case 'ROOM_UPDATE':
        this.renderRoomish(msg);
        return;

      case 'AUTH_PROMPT':
        // Clear messages on the initial username prompt to avoid duplicate welcome screens on reconnect
        if ((msg.message ?? '').includes('username')) {
          this.messages.set([]);
        }
        this.addMsg('AUTH_PROMPT', esc(msg.message ?? ''));
        this.passwordMode.set(msg.mask === true);
        return;

      case 'CHAT_ROOM':
      case 'CHAT_WORLD':
      case 'CHAT_DM':
        this.renderChat(msg);
        return;

      case 'WHO_LIST':
      case 'INVENTORY_UPDATE':
      case 'HELP':
        // Handled visually by their respective panels — no terminal output needed.
        return;

      default:
        this.addMsg(type, renderMarkup(msg.message ?? JSON.stringify(msg)));
    }
  }

  private renderChat(msg: GameMessage): void {
    const type = msg.type!;
    const badgeKey =
      type === 'CHAT_ROOM' ? 'room' :
      type === 'CHAT_WORLD' ? 'world' :
      'dm';

    const label =
      type === 'CHAT_ROOM' ? 'Room' :
      type === 'CHAT_WORLD' ? 'World' :
      'DM';

    const html = `
      <span class="chat-badge ${badgeKey}">${label}</span>
      <span class="chat-from">${esc(msg.from ?? '')}</span>
      ${esc(msg.message ?? '')}
    `;

    this.addMsg(type, html);
  }

  private renderRoomish(msg: GameMessage): void {
    const r = msg.room;
    if (!r) {
      this.addMsg(msg.type ?? 'MESSAGE', esc(msg.message ?? ''));
      return;
    }

    const exitChips = (r.exits ?? []).length
      ? (r.exits).map(e => `<span class="chip chip-exit">${esc(e)}</span>`).join('')
      : `<span class="chip-none">none</span>`;

    const itemChips = (r.items ?? []).length
      ? (r.items).map(i => `<span class="chip chip-item rarity-${esc(i.rarity)}">${esc(i.name)}</span>`).join('')
      : `<span class="chip-none">none</span>`;

    const npcChips = (r.npcs ?? []).length
      ? (r.npcs).map(n => `<span class="chip ${n.sentient ? 'chip-npc-sentient' : 'chip-npc'}">${esc(n.name)}</span>`).join('')
      : `<span class="chip-none">none</span>`;

    const playerChips = (r.players ?? []).length
      ? (r.players).map(p => `<span class="chip chip-player">${esc(p)}</span>`).join('')
      : `<span class="chip-none">none</span>`;

    const html = `
      ${msg.message ? `<div class="room-msg">${esc(msg.message)}</div>` : ''}
      <div class="room-name">${esc(r.name)}</div>
      <div class="room-desc">${esc(r.description)}</div>
      <div class="room-meta">
        <span class="meta-label">Exits</span>${exitChips}
        <span class="meta-sep">|</span>
        <span class="meta-label">Items</span>${itemChips}
        <span class="meta-sep">|</span>
        <span class="meta-label">NPCs</span>${npcChips}
        <span class="meta-sep">|</span>
        <span class="meta-label">Players</span>${playerChips}
      </div>
    `;

    this.addMsg(msg.type ?? 'ROOM_UPDATE', html);
  }

  private addMsg(cssClass: string, html: string): void {
    this.messages.update(list => [
      ...list,
      { id: ++this.nextId, cssClass, html },
    ]);
  }
}
