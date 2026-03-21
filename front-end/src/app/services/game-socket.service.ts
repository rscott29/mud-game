import { Injectable, NgZone, signal } from '@angular/core';
import { Subject } from 'rxjs';
import {
  CONNECTION_STATUSES,
  GAME_MESSAGE_TYPES,
  GameMessage,
  ConnectionStatus,
  PlayerStatsDto,
} from '../models/game-message';

const TOKEN_KEY = 'mudReconnectToken';
const RECONNECT_BASE_MS = 1_000;
const RECONNECT_MAX_MS = 30_000;
const RECONNECT_MAX_TRIES = 10;

type OutboundPayload = Record<string, unknown>;

@Injectable({ providedIn: 'root' })
export class GameSocketService {
  private socket: WebSocket | null = null;
  private reconnectAttempt = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private intentionalClose = false;

  readonly messages$ = new Subject<GameMessage>();
  readonly systemMessages$ = new Subject<string>();

  readonly status = signal<ConnectionStatus>(CONNECTION_STATUSES.DISCONNECTED);
  readonly playerStats = signal<PlayerStatsDto | null>(null);

  constructor(private readonly zone: NgZone) {}

  private get wsUrl(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/game`;
  }

  connect(): void {
    if (this.isSocketActive()) return;

    this.intentionalClose = false;
    this.clearReconnectTimer();
    this.openSocket();
  }

  disconnect(): void {
    this.intentionalClose = true;
    this.clearReconnectTimer();
    this.reconnectAttempt = 0;

    this.socket?.close();
    this.socket = null;
    this.status.set(CONNECTION_STATUSES.DISCONNECTED);
  }

  sendRaw(payload: string): void {
    if (!this.isSocketOpen()) return;
    this.socket!.send(payload);
  }

  sendJson(payload: OutboundPayload): void {
    this.sendRaw(JSON.stringify(payload));
  }

  sendCommand(command: string): void {
    this.sendJson({ command });
  }

  private openSocket(): void {
    const socket = new WebSocket(this.wsUrl);
    this.socket = socket;

    socket.addEventListener('open', this.handleOpen);
    socket.addEventListener('message', this.handleMessage);
    socket.addEventListener('close', this.handleClose);
    socket.addEventListener('error', this.handleError);
  }

  private readonly handleOpen = (): void => {
    this.zone.run(() => {
      this.reconnectAttempt = 0;
      this.status.set(CONNECTION_STATUSES.CONNECTED);
      this.systemMessages$.next('Connected');

      this.tryReconnectWithStoredToken();
    });
  };

  private readonly handleMessage = (event: MessageEvent<string>): void => {
    this.zone.run(() => {
      const message = this.parseMessage(event.data);

      if (!message) {
        this.systemMessages$.next(`Non-JSON: ${event.data}`);
        return;
      }

      if (this.handleSessionToken(message)) {
        return;
      }

      this.applySharedState(message);
      this.dispatchMessage(message);
      this.messages$.next(message);
    });
  };

  private readonly handleClose = (): void => {
    this.zone.run(() => {
      this.socket = null;
      this.status.set(CONNECTION_STATUSES.DISCONNECTED);

      if (this.intentionalClose) {
        return;
      }

      this.systemMessages$.next('Connection lost.');
      this.scheduleReconnect();
    });
  };

  private readonly handleError = (): void => {
    this.zone.run(() => {
      this.systemMessages$.next('WebSocket error.');
    });
  };

  private parseMessage(raw: string): GameMessage | null {
    try {
      return JSON.parse(raw) as GameMessage;
    } catch {
      return null;
    }
  }

  private handleSessionToken(message: GameMessage): boolean {
    if (message.type !== GAME_MESSAGE_TYPES.SESSION_TOKEN || !message.token) {
      return false;
    }

    localStorage.setItem(TOKEN_KEY, message.token);
    return true;
  }

  /**
   * State that can arrive alongside many message types.
   */
  private applySharedState(message: GameMessage): void {
    if (message.playerStats != null) {
      this.playerStats.set(message.playerStats);
    }
  }

  /**
   * Type-specific UI reactions.
   */
  private dispatchMessage(message: GameMessage): void {
    switch (message.type) {
      case GAME_MESSAGE_TYPES.AUTH_PROMPT:
        // Only reset UI state when we're back at the login screen (username prompt)
        if ((message.message ?? '').toLowerCase().includes('username')) {
          this.playerStats.set(null);
        }
        break;

      default:
        break;
    }
  }

  private tryReconnectWithStoredToken(): void {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return;

    localStorage.removeItem(TOKEN_KEY);
    this.sendJson({ reconnectToken: token });
  }

  private scheduleReconnect(): void {
    if (this.intentionalClose) return;

    if (this.reconnectAttempt >= RECONNECT_MAX_TRIES) {
      this.systemMessages$.next(
        'Could not reconnect after several attempts. Refresh to try again.'
      );
      return;
    }

    const nextAttempt = this.reconnectAttempt + 1;
    const delay = this.getReconnectDelay(this.reconnectAttempt);

    this.reconnectAttempt = nextAttempt;
    this.status.set(CONNECTION_STATUSES.RECONNECTING);
    this.systemMessages$.next(
      `Reconnecting in ${(delay / 1000).toFixed(1)}s… (attempt ${nextAttempt}/${RECONNECT_MAX_TRIES})`
    );

    this.reconnectTimer = setTimeout(() => {
      this.zone.run(() => this.connect());
    }, delay);
  }

  private getReconnectDelay(attempt: number): number {
    const exponentialBackoff = Math.min(
      RECONNECT_BASE_MS * 2 ** attempt,
      RECONNECT_MAX_MS
    );
    const jitter = Math.random() * 1_000;
    return Math.round(exponentialBackoff + jitter);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer == null) return;

    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
  }

  private isSocketOpen(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
  }

  private isSocketActive(): boolean {
    return (
      this.socket?.readyState === WebSocket.OPEN ||
      this.socket?.readyState === WebSocket.CONNECTING
    );
  }
}
