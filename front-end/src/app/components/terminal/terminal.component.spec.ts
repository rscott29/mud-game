import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { GameMessage, ConnectionStatus } from '../../models/game-message';
import { GameSocketService } from '../../services/game-socket.service';
import { TerminalComponent } from './terminal.component';

class MockGameSocketService {
  readonly messages$ = new Subject<GameMessage>();
  readonly systemMessages$ = new Subject<string>();
  readonly status = signal<ConnectionStatus>('connected');
  readonly sent: string[] = [];

  connect(): void {}
  disconnect(): void {}
  send(payload: string): void {
    this.sent.push(payload);
  }
}

describe('TerminalComponent', () => {
  let socket: MockGameSocketService;

  beforeEach(async () => {
    socket = new MockGameSocketService();

    await TestBed.configureTestingModule({
      imports: [TerminalComponent],
      providers: [{ provide: GameSocketService, useValue: socket }],
    }).compileComponents();
  });

  it('sends "go north" as a direct command payload', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.inputValue.set('go north');
    component.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'go',
      args: ['north'],
    });
  });

  it('sends "spawn item_ale_mug" as a direct command payload', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.inputValue.set('spawn item_ale_mug');
    component.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'spawn',
      args: ['item_ale_mug'],
    });
  });

  it('sends "telport npc_dog_obi" as a direct command payload', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.inputValue.set('telport npc_dog_obi');
    component.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'telport',
      args: ['npc_dog_obi'],
    });
  });

  it('routes unknown text through natural-language input', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.inputValue.set('dance around happily');
    component.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      input: 'dance around happily',
    });
  });
});
