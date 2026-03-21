import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import { GameMessage, ConnectionStatus } from '../../models/game-message';
import {
  CommandCatalogEntry,
  CommandCatalogService,
  HelpCategory,
} from '../../services/command-catalog.service';
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

class MockCommandCatalogService {
  private readonly commands: CommandCatalogEntry[] = [
    {
      canonicalName: 'go',
      aliases: ['go', 'move'],
      category: 'Exploration',
      usage: 'go <direction>',
      description: 'Move in a direction (n/s/e/w/u/d)',
      godOnly: false,
      showInHelp: true,
      dispatchMode: 'DIRECT',
    },
    {
      canonicalName: 'look',
      aliases: ['look', 'l', 'examine', 'x'],
      category: 'Exploration',
      usage: 'look [target]',
      description: 'Describe surroundings or examine something',
      godOnly: false,
      showInHelp: true,
      dispatchMode: 'NATURAL_LANGUAGE',
    },
    {
      canonicalName: 'spawn',
      aliases: ['spawn'],
      category: 'God',
      usage: 'spawn <item> [inv]',
      description: 'Spawn an item by ID',
      godOnly: true,
      showInHelp: true,
      dispatchMode: 'DIRECT',
    },
    {
      canonicalName: 'teleport',
      aliases: ['teleport', 'tp', 'warp', 'goto', 'telport', 'teleprot'],
      category: 'God',
      usage: 'teleport <target>',
      description: 'Teleport to a player or NPC',
      godOnly: true,
      showInHelp: true,
      dispatchMode: 'DIRECT',
    },
    {
      canonicalName: 'dance',
      aliases: ['dance', 'boogie'],
      category: 'Social',
      usage: 'dance [target]',
      description: 'Dance on your own or with someone.',
      godOnly: false,
      showInHelp: true,
      dispatchMode: 'DIRECT',
    },
  ];

  load(): void {}

  findByAlias(alias: string): CommandCatalogEntry | undefined {
    const normalized = alias.trim().toLowerCase();
    return this.commands.find(command =>
      command.aliases.some(candidate => candidate === normalized)
    );
  }

  helpCategories(isGod: boolean): HelpCategory[] {
    const visibleCommands = this.commands.filter(command => !command.godOnly || isGod);
    const categories: HelpCategory[] = [];
    const byTitle = new Map<string, HelpCategory>();

    for (const command of visibleCommands) {
      let category = byTitle.get(command.category);
      if (!category) {
        category = { title: command.category, entries: [] };
        byTitle.set(command.category, category);
        categories.push(category);
      }

      category.entries.push({
        cmd: command.usage,
        desc: command.description,
      });
    }

    return categories;
  }
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
        { provide: CommandCatalogService, useClass: MockCommandCatalogService },
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

  it('sends contextual look text as natural language input', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.inputValue.set('look at the old fountain');
    component.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      input: 'look at the old fountain',
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

  it('renders help from the command catalog instead of hardcoded frontend lists', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({ type: 'HELP', message: '' });

    expect(component.messages()).toHaveLength(1);
    expect(component.messages()[0].cssClass).toBe('HELP');
    expect(component.messages()[0].html).toContain("Traveler's handbook");
    expect(component.messages()[0].html).toContain('Exploration');
    expect(component.messages()[0].html).toContain('look [target]');
    expect(component.messages()[0].html).toContain('Describe surroundings or examine something');
    expect(component.messages()[0].html).not.toContain('spawn &lt;item&gt; [inv]');
  });
});
