import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import { GameMessage, ConnectionStatus } from '../../models/game-message';
import { GameSocketService } from '../../services/game-socket.service';
import { SkillProgressionService } from '../../services/skill-progression.service';
import { ZoomService } from '../../services/zoom.service';
import { TerminalComponent } from './terminal.component';

class MockGameSocketService {
  readonly messages$ = new Subject<GameMessage>();
  readonly systemMessages$ = new Subject<string>();
  readonly status = signal<ConnectionStatus>('connected');
  readonly playerStats = signal(null);
  readonly sent: string[] = [];

  connect(): void {}
  disconnect(): void {}
  sendRaw(payload: string): void {
    this.sent.push(payload);
  }
}

class MockSkillProgressionService {
  getSkillsForClass(characterClass: string) {
    return of({ characterClass, skills: [] });
  }
}

class MockZoomService {
  readonly zoomLevel = signal(100);

  increaseZoom(): void {}
  decreaseZoom(): void {}
}

describe('TerminalComponent', () => {
  let socket: MockGameSocketService;

  beforeEach(async () => {
    socket = new MockGameSocketService();

    await TestBed.configureTestingModule({
      imports: [TerminalComponent],
      providers: [
        { provide: GameSocketService, useValue: socket },
        { provide: SkillProgressionService, useClass: MockSkillProgressionService },
        { provide: ZoomService, useClass: MockZoomService },
      ],
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

  it('sends recognized social text as a direct command payload', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.inputValue.set('dance around happily');
    component.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'dance',
      args: ['around', 'happily'],
    });
  });

  it('appends narrative messages immediately as they arrive', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({ type: 'NARRATIVE', message: 'First voice.' });
    socket.messages$.next({ type: 'NARRATIVE', message: 'Second voice.' });

    expect(component.messages().map(message => message.cssClass)).toEqual([
      'NARRATIVE',
      'NARRATIVE',
    ]);
    expect(component.messages().length).toBe(2);
  });

  it('renders auth prompts as a full prompt card with preserved line breaks', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({
      type: 'AUTH_PROMPT',
      message: 'Welcome to the MUD!\nEnter your username:',
    });

    expect(component.messages()).toHaveLength(1);
    expect(component.messages()[0].cssClass).toBe('AUTH_PROMPT');
    expect(component.messages()[0].html).toContain('term-card--prompt');
    expect(component.messages()[0].html).toContain('Welcome to the MUD!<br>Enter your username:');
  });

  it('merges same-room updates into the existing room card', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'A dog barks.', room });
    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'A bard sings.', room });

    expect(component.messages().length).toBe(1);
    expect(component.messages()[0].cssClass).toBe('ROOM_UPDATE');
    expect(component.messages()[0].html).toContain('A dog barks.');
    expect(component.messages()[0].html).toContain('A bard sings.');
  });

  it('creates a new room card when the player enters a different room', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    const townSquare = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };
    const forestEdge = {
      id: 'forest_edge',
      name: 'Forest Edge',
      description: 'Pines sway at the edge of town.',
      exits: ['south'],
      items: [],
      npcs: [],
      players: [],
    };

    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'You arrive.', room: townSquare });
    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'A raven watches.', room: forestEdge });

    expect(component.messages().length).toBe(2);
    expect(component.messages()[0].html).toContain('Town Square');
    expect(component.messages()[1].html).toContain('Forest Edge');
  });

  it('does not show a redundant room badge when no other players are present', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'You arrive.', room });

    expect(component.messages()).toHaveLength(1);
    expect(component.messages()[0].html).not.toContain('term-badge');
  });

  it('does not show a redundant room badge when other players are present', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: ['Quentor', 'Mira'],
    };

    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'You arrive.', room });

    expect(component.messages()).toHaveLength(1);
    expect(component.messages()[0].html).not.toContain('term-badge');
    expect(component.messages()[0].html).toContain('Travelers');
    expect(component.messages()[0].html).toContain('Quentor');
    expect(component.messages()[0].html).toContain('Mira');
  });

  it('creates a fresh room card when look refreshes the current room', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'You arrive.', room });
    socket.messages$.next({ type: 'ROOM_ACTION', message: 'Quentor arrives from the east.' });
    socket.messages$.next({ type: 'ROOM_REFRESH', message: 'You take a slower look around.', room });

    expect(component.messages().length).toBe(2);
    expect(component.messages()[0].html).toContain('Quentor arrives from the east.');
    expect(component.messages()[1].cssClass).toBe('ROOM_REFRESH');
    expect(component.messages()[1].html).toContain('You look around');
    expect(component.messages()[1].html).toContain('You take a slower look around.');
    expect(component.messages()[1].html).not.toContain('Quentor arrives from the east.');
  });

  it('folds room-scoped narrative messages into the active room card', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'You arrive.', room });
    socket.messages$.next({ type: 'NARRATIVE', message: 'Quentor arrives from the east.' });

    expect(component.messages().length).toBe(1);
    expect(component.messages()[0].cssClass).toBe('ROOM_UPDATE');
    expect(component.messages()[0].html).toContain('You arrive.');
    expect(component.messages()[0].html).toContain('Quentor arrives from the east.');
  });

  it('folds room action messages into the active room card with distinct styling', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    socket.messages$.next({ type: 'ROOM_UPDATE', message: 'You arrive.', room });
    socket.messages$.next({ type: 'ROOM_ACTION', message: 'Quentor arrives from the east.' });

    expect(component.messages().length).toBe(1);
    expect(component.messages()[0].cssClass).toBe('ROOM_UPDATE');
    expect(component.messages()[0].html).toContain('term-inline-event--player-action');
    expect(component.messages()[0].html).toContain('Traveler');
    expect(component.messages()[0].html).toContain('Quentor arrives from the east.');
  });
});
