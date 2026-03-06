import { Injectable, NgZone, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { GameMessage, ConnectionStatus } from '../models/game-message';

const TOKEN_KEY            = 'mudReconnectToken';
const RECONNECT_BASE_MS    = 1_000;
const RECONNECT_MAX_MS     = 30_000;
const RECONNECT_MAX_TRIES  = 10;

@Injectable({ providedIn: 'root' })
export class GameSocketService {

  private ws: WebSocket | null = null;
  private reconnectAttempt = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private intentionalClose = false;

  readonly messages$       = new Subject<GameMessage>();
  readonly systemMessages$ = new Subject<string>();
  readonly status          = signal<ConnectionStatus>('disconnected');

  constructor(private zone: NgZone) {}

  private get wsUrl(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/game`;
  }

  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) return;
    clearTimeout(this.reconnectTimer!);

    this.ws = new WebSocket(this.wsUrl);

    this.ws.addEventListener('open', () => {
      this.zone.run(() => {
        this.reconnectAttempt = 0;
        this.status.set('connected');
        this.systemMessages$.next('Connected');

        const token = localStorage.getItem(TOKEN_KEY);
        if (token) {
          localStorage.removeItem(TOKEN_KEY);
          this.send(JSON.stringify({ reconnectToken: token }));
        }
      });
    });

    this.ws.addEventListener('message', (evt) => {
      this.zone.run(() => {
        try {
          const msg = JSON.parse(evt.data) as GameMessage;
          if (msg.type === 'SESSION_TOKEN' && msg.token) {
            localStorage.setItem(TOKEN_KEY, msg.token);
            return;
          }
          this.messages$.next(msg);
        } catch {
          this.systemMessages$.next('Non-JSON: ' + evt.data);
        }
      });
    });

    this.ws.addEventListener('close', () => {
      this.zone.run(() => {
        this.status.set('disconnected');
        if (this.intentionalClose) return;
        this.systemMessages$.next('Connection lost.');
        this.scheduleReconnect();
      });
    });

    this.ws.addEventListener('error', () => {
      this.zone.run(() => {
        this.systemMessages$.next('WebSocket error.');
      });
    });
  }

  send(payload: string): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(payload);
    }
  }

  disconnect(): void {
    this.intentionalClose = true;
    this.ws?.close();
  }

  private scheduleReconnect(): void {
    if (this.intentionalClose) return;

    if (this.reconnectAttempt >= RECONNECT_MAX_TRIES) {
      this.systemMessages$.next('Could not reconnect after several attempts. Refresh to try again.');
      return;
    }

    const base  = Math.min(RECONNECT_BASE_MS * 2 ** this.reconnectAttempt, RECONNECT_MAX_MS);
    const jitter = Math.random() * 1_000;
    const delay  = Math.round(base + jitter);
    this.reconnectAttempt++;

    this.status.set('reconnecting');
    this.systemMessages$.next(
      `Reconnecting in ${(delay / 1000).toFixed(1)}s… (attempt ${this.reconnectAttempt}/${RECONNECT_MAX_TRIES})`
    );
    this.reconnectTimer = setTimeout(() => this.zone.run(() => this.connect()), delay);
  }
}
