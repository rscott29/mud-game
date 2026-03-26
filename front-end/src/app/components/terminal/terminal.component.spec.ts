import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import {
  CONNECTION_STATUSES,
  type ConnectionStatus,
  GAME_MESSAGE_TYPES,
  type GameMessage,
  type PlayerStatsDto,
  TERMINAL_MESSAGE_CLASSES,
} from '../../models/game-message';
import {
  COMMAND_DISPATCH_MODES,
  COMMAND_HELP_CATEGORIES,
  type CommandCatalogEntry,
  CommandCatalogService,
  type HelpCategory,
} from '../../services/command-catalog.service';
import { GameSocketService } from '../../services/game-socket.service';
import { SkillProgressionService } from '../../services/skill-progression.service';
import { ZoomService } from '../../services/zoom.service';
import { TerminalComponent } from './terminal.component';

class MockGameSocketService {
  readonly messages$ = new Subject<GameMessage>();
  readonly systemMessages$ = new Subject<string>();
  readonly status = signal<ConnectionStatus>(CONNECTION_STATUSES.CONNECTED);
  readonly playerStats = signal<PlayerStatsDto | null>(null);
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
      aliases: ['go', 'move', 'north', 'n', 'south', 's', 'east', 'e', 'west', 'w', 'up', 'u', 'down', 'd'],
      category: COMMAND_HELP_CATEGORIES.EXPLORATION,
      usage: 'go <direction>',
      description: 'Move in a direction (n/s/e/w/u/d)',
      godOnly: false,
      showInHelp: true,
      dispatchMode: COMMAND_DISPATCH_MODES.DIRECT,
    },
    {
      canonicalName: 'look',
      aliases: ['look', 'l', 'lore', 'locate', 'examine', 'x'],
      category: COMMAND_HELP_CATEGORIES.EXPLORATION,
      usage: 'look [target]',
      description: 'Describe surroundings or examine something',
      godOnly: false,
      showInHelp: true,
      dispatchMode: COMMAND_DISPATCH_MODES.NATURAL_LANGUAGE,
    },
    {
      canonicalName: 'spawn',
      aliases: ['spawn'],
      category: COMMAND_HELP_CATEGORIES.GOD,
      usage: 'spawn <item> [inv]',
      description: 'Spawn an item by ID',
      godOnly: true,
      showInHelp: true,
      dispatchMode: COMMAND_DISPATCH_MODES.DIRECT,
    },
    {
      canonicalName: 'teleport',
      aliases: ['teleport', 'tp', 'warp', 'goto', 'telport', 'teleprot'],
      category: COMMAND_HELP_CATEGORIES.GOD,
      usage: 'teleport <target>',
      description: 'Teleport to a player or NPC',
      godOnly: true,
      showInHelp: true,
      dispatchMode: COMMAND_DISPATCH_MODES.DIRECT,
    },
    {
      canonicalName: 'dance',
      aliases: ['dance', 'boogie'],
      category: COMMAND_HELP_CATEGORIES.SOCIAL,
      usage: 'dance [target|self]',
      description: 'Dance on your own or with someone.',
      godOnly: false,
      showInHelp: true,
      dispatchMode: COMMAND_DISPATCH_MODES.DIRECT,
    },
  ];

  load(): void {}

  findByAlias(alias: string): CommandCatalogEntry | undefined {
    const normalized = alias.trim().toLowerCase();
    return this.commands.find(command =>
      command.aliases.some(candidate => candidate === normalized)
    );
  }

  autocompleteSuggestion(input: string, isGod: boolean): string | undefined {
    return this.autocompleteMatches(input, isGod)[0];
  }

  autocompleteMatches(input: string, isGod: boolean): string[] {
    const leadingWhitespace = input.match(/^\s*/)?.[0] ?? '';
    const trimmedLeading = input.slice(leadingWhitespace.length);
    const tokenMatch = trimmedLeading.match(/^(\S+)([\s\S]*)$/);
    if (!tokenMatch) {
      return [];
    }

    const partialToken = tokenMatch[1].toLowerCase();
    const suffix = tokenMatch[2] ?? '';

    return this.commands
      .filter(command => !command.godOnly || isGod)
      .flatMap(command => command.aliases)
      .map(alias => alias.trim().toLowerCase())
      .filter(alias => alias.startsWith(partialToken) && alias !== partialToken)
      .map(alias => `${leadingWhitespace}${alias}${suffix}`)
      .filter((alias, index, aliases) => aliases.indexOf(alias) === index)
      .sort((left, right) => left.length - right.length || left.localeCompare(right));
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
        aliasesText: undefined,
        example: command.canonicalName === 'go' ? 'n' : command.canonicalName === 'look' ? 'look fountain' : undefined,
      });
    }

    return categories;
  }

  helpTips(): string[] {
    return ['Use n, s, e, w, u, and d for quick travel.'];
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

    component.input.inputValue.set('go north');
    component.input.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'go',
      args: ['north'],
    });
  });

  it('sends "up" as a direct command payload instead of natural language', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.input.inputValue.set('up');
    component.input.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'up',
    });
  });

  it('sends "spawn item_ale_mug" as a direct command payload', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.input.inputValue.set('spawn item_ale_mug');
    component.input.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'spawn',
      args: ['item_ale_mug'],
    });
  });

  it('sends "telport npc_dog_obi" as a direct command payload', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.input.inputValue.set('telport npc_dog_obi');
    component.input.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'telport',
      args: ['npc_dog_obi'],
    });
  });

  it('sends recognized social text as a direct command payload', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.input.inputValue.set('dance around happily');
    component.input.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      command: 'dance',
      args: ['around', 'happily'],
    });
  });

  it('sends contextual look text as natural language input', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    component.input.inputValue.set('look at the old fountain');
    component.input.send();

    expect(socket.sent.length).toBe(1);
    expect(JSON.parse(socket.sent[0])).toEqual({
      input: 'look at the old fountain',
    });
  });

  it('recalls previous commands from the input when the player presses arrow up', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.playerStats.set({
      health: 12,
      maxHealth: 20,
      mana: 7,
      maxMana: 10,
      movement: 8,
      maxMovement: 12,
      level: 3,
      maxLevel: 10,
      xpProgress: 25,
      xpForNextLevel: 100,
      totalXp: 250,
      isGod: false,
      characterClass: 'mage',
    });
    fixture.detectChanges();

    component.input.updateInputValue('look');
    component.input.send();
    component.input.updateInputValue('north');
    component.input.send();

    const input: HTMLInputElement = fixture.nativeElement.querySelector('.cmd-input');
    const firstUpEvent = new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true });
    input.dispatchEvent(firstUpEvent);
    fixture.detectChanges();

    expect(component.input.inputValue()).toBe('north');
    expect(firstUpEvent.defaultPrevented).toBe(true);

    const secondUpEvent = new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true });
    input.dispatchEvent(secondUpEvent);
    fixture.detectChanges();

    expect(component.input.inputValue()).toBe('look');

    const downEvent = new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true });
    input.dispatchEvent(downEvent);
    fixture.detectChanges();

    expect(component.input.inputValue()).toBe('north');
  });

  it('shows command completions, preserves the rest of the line, and cycles them with tab', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.playerStats.set({
      health: 12,
      maxHealth: 20,
      mana: 7,
      maxMana: 10,
      movement: 8,
      maxMovement: 12,
      level: 3,
      maxLevel: 10,
      xpProgress: 25,
      xpForNextLevel: 100,
      totalXp: 250,
      isGod: false,
      characterClass: 'mage',
    });
    component.input.updateInputValue('lo fountain');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent ?? '';
    expect(text).toContain('Tab');
    expect(text).toContain('complete look fountain');
    expect(text).toContain('1/3');
    expect(text).toContain('lore fountain');

    const input: HTMLInputElement = fixture.nativeElement.querySelector('.cmd-input');
    const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    input.dispatchEvent(tabEvent);
    fixture.detectChanges();

    expect(component.input.inputValue()).toBe('look fountain');
    expect(tabEvent.defaultPrevented).toBe(true);

    const secondTabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    input.dispatchEvent(secondTabEvent);
    fixture.detectChanges();

    expect(component.input.inputValue()).toBe('lore fountain');
    expect(fixture.nativeElement.textContent ?? '').toContain('2/3');
  });

  it('appends narrative messages immediately as they arrive', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.NARRATIVE, message: 'First voice.' });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.NARRATIVE, message: 'Second voice.' });

    expect(component.view.messages().map(message => message.cssClass)).toEqual([
      TERMINAL_MESSAGE_CLASSES.NARRATIVE,
      TERMINAL_MESSAGE_CLASSES.NARRATIVE,
    ]);
    expect(component.view.messages().length).toBe(2);
  });

  it('renders auth prompts as a full prompt card with preserved line breaks', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({
      type: GAME_MESSAGE_TYPES.AUTH_PROMPT,
      message: 'Welcome to the MUD!\nEnter your username:',
    });

    expect(component.view.messages()).toHaveLength(1);
    expect(component.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.AUTH_PROMPT);
    expect(component.view.messages()[0].html).toContain('term-card--prompt');
    expect(component.view.messages()[0].html).toContain('<pre class="term-copy term-copy--preformatted">');
    expect(component.view.messages()[0].html).toContain('Welcome to the MUD!\nEnter your username:');
    expect(component.view.messages()[0].html).not.toContain('Wayfarer entry');
    expect(component.view.messages()[0].html).not.toContain('The gatehouse asks');
  });

  it('renders the banner-style auth welcome as a fuller sign-in layout', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({
      type: GAME_MESSAGE_TYPES.AUTH_PROMPT,
      message: '      /\\\\\n /  \\\\\n\nEnter your username:',
    });

    expect(component.view.messages()).toHaveLength(1);
    expect(component.view.messages()[0].html).toContain('term-card--prompt-auth');
    expect(component.view.messages()[0].html).toContain('term-auth-kicker');
    expect(component.view.messages()[0].html).toContain('Sign in');
    expect(component.view.messages()[0].html).toContain('New usernames will guide you through creating a character.');
    expect(component.view.messages()[0].html).toContain('      /\\\\');
  });

  it('preserves leading spaces in auth banner art', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({
      type: GAME_MESSAGE_TYPES.AUTH_PROMPT,
      message: '  /\\\\\n /  \\\\',
    });

    expect(component.view.messages()).toHaveLength(1);
    expect(component.view.messages()[0].html).toContain('  /\\\\');
    expect(component.view.messages()[0].html).toContain('\n /  \\\\');
  });

  it('shows god levels above the normal cap without a confusing max-level suffix', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.playerStats.set({
      health: 999,
      maxHealth: 999,
      mana: 999,
      maxMana: 999,
      movement: 999,
      maxMovement: 999,
      level: 100,
      maxLevel: 70,
      xpProgress: 0,
      xpForNextLevel: 0,
      totalXp: 105,
      isGod: true,
      characterClass: 'mage',
    });

    expect(component.view.levelLabel()).toBe('Level 100');
  });

  it('hides total xp in the header for god accounts', () => {
    const fixture = TestBed.createComponent(TerminalComponent);

    socket.playerStats.set({
      health: 999,
      maxHealth: 999,
      mana: 999,
      maxMana: 999,
      movement: 999,
      maxMovement: 999,
      level: 100,
      maxLevel: 70,
      xpProgress: 0,
      xpForNextLevel: 0,
      totalXp: 105,
      isGod: true,
      characterClass: 'mage',
    });

    fixture.detectChanges();

    const text = fixture.nativeElement.textContent ?? '';
    expect(text).toContain('GOD');
    expect(text).not.toContain('Total XP 105');
  });

  it('renders the me command response as a character sheet card in the log', () => {
    const fixture = TestBed.createComponent(TerminalComponent);

    socket.messages$.next({
      type: GAME_MESSAGE_TYPES.PLAYER_OVERVIEW,
      message: 'Axi',
      playerStats: {
        health: 20,
        maxHealth: 24,
        mana: 10,
        maxMana: 12,
        movement: 12,
        maxMovement: 14,
        level: 4,
        maxLevel: 10,
        xpProgress: 40,
        xpForNextLevel: 100,
        totalXp: 340,
        isGod: false,
        characterClass: 'warrior',
      },
      combatStats: {
        armor: 5,
        minDamage: 6,
        maxDamage: 10,
        hitChance: 81,
        critChance: 0,
      },
      inventory: [
        {
          id: 'item_iron_sword',
          name: 'Iron Sword',
          description: 'A sturdy sword.',
          rarity: 'common',
          equipped: true,
          equippedSlot: 'Main weapon',
        },
        {
          id: 'item_leather_shield',
          name: 'Leather Shield',
          description: 'A light shield.',
          rarity: 'uncommon',
          equipped: true,
          equippedSlot: 'Off hand / shield',
        },
      ],
    });

    fixture.detectChanges();

    const text = fixture.nativeElement.textContent ?? '';
    expect(text).toContain('Character sheet');
    expect(text).toContain('Axi');
    expect(text).toContain('2/6 readied');
    expect(text).toContain('Combat profile');
    expect(text).toContain('Armor');
    expect(text).toContain('5');
    expect(text).toContain('Attack damage');
    expect(text).toContain('6-10');
    expect(text).toContain('Hit chance');
    expect(text).toContain('81%');
    expect(text).toContain('Critical hit');
    expect(text).toContain('0%');
    expect(text).toContain('Health');
    expect(text).toContain('20/24');
    expect(text).not.toContain('HP 20/24');
    expect(text).toContain('Main weapon');
    expect(text).toContain('Iron Sword');
    expect(text).toContain('Off hand / shield');
    expect(text).toContain('Leather Shield');
    expect(text).toContain('Head');
    expect(text).toContain('Empty');
  });

  it('renders god players in who with a distinct divine marker', () => {
    const fixture = TestBed.createComponent(TerminalComponent);

    socket.messages$.next({
      type: GAME_MESSAGE_TYPES.WHO_LIST,
      whoPlayers: [
        {
          name: 'Axi',
          level: 7,
          title: 'Forgewalker',
          location: "Blacksmith's Forge",
          isGod: false,
        },
        {
          name: 'Zeal',
          level: 100,
          title: 'Worldshaper',
          location: 'Star Sanctum',
          isGod: true,
        },
      ],
    });

    fixture.detectChanges();

    expect(fixture.componentInstance.view.messages()).toHaveLength(1);
    expect(fixture.componentInstance.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.WHO_LIST);
    expect(fixture.componentInstance.view.messages()[0].html).toContain('term-list__item--god');
    expect(fixture.componentInstance.view.messages()[0].html).toContain('term-pill--god-presence');
    expect(fixture.nativeElement.textContent ?? '').toContain('Divine');
    expect(fixture.nativeElement.textContent ?? '').toContain('Zeal');
  });

  it('uses auth-focused shell copy before the player is signed in', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    fixture.detectChanges();

    expect(component.view.promptLabel()).toBe('name');
    expect(component.view.placeholder()).toBe('Username');
    expect(component.view.isAuthScreen()).toBe(true);

    const text = fixture.nativeElement.textContent ?? '';
    expect(text).toContain('Sign in to begin or continue your adventure.');
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'A dog barks.', room });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'A bard sings.', room });

    expect(component.view.messages().length).toBe(1);
    expect(component.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_UPDATE);
    expect(component.view.messages()[0].html).toContain('A dog barks.');
    expect(component.view.messages()[0].html).toContain('A bard sings.');
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room: townSquare });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'A raven watches.', room: forestEdge });

    expect(component.view.messages().length).toBe(2);
    expect(component.view.messages()[0].html).toContain('Town Square');
    expect(component.view.messages()[1].html).toContain('Forest Edge');
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });

    expect(component.view.messages()).toHaveLength(1);
    expect(component.view.messages()[0].html).not.toContain('term-badge');
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });

    expect(component.view.messages()).toHaveLength(1);
    expect(component.view.messages()[0].html).not.toContain('term-badge');
    expect(component.view.messages()[0].html).toContain('Travelers');
    expect(component.view.messages()[0].html).toContain('Quentor');
    expect(component.view.messages()[0].html).toContain('Mira');
  });

  it('renders a quest marker for NPCs with available quests', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    const room = {
      id: 'forest_fork',
      name: 'Forest Fork',
      description: 'A quiet crossroads.',
      exits: ['west'],
      items: [],
      npcs: [{ name: 'Blind Guide', sentient: true, hasAvailableQuest: true }],
      players: [],
    };

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'The guide waits.', room });

    expect(component.view.messages()).toHaveLength(1);
    expect(component.view.messages()[0].html).toContain('term-chip__marker--quest');
    expect(component.view.messages()[0].html).toContain('Blind Guide');
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_ACTION, message: 'Quentor arrives from the east.' });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_REFRESH, message: 'You take a slower look around.', room });

    expect(component.view.messages().length).toBe(2);
    expect(component.view.messages()[0].html).toContain('Quentor arrives from the east.');
    expect(component.view.messages()[1].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_REFRESH);
    expect(component.view.messages()[1].html).toContain('You look around');
    expect(component.view.messages()[1].html).toContain('You take a slower look around.');
    expect(component.view.messages()[1].html).not.toContain('Quentor arrives from the east.');
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.NARRATIVE, message: 'Quentor arrives from the east.' });

    expect(component.view.messages().length).toBe(1);
    expect(component.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_UPDATE);
    expect(component.view.messages()[0].html).toContain('You arrive.');
    expect(component.view.messages()[0].html).toContain('Quentor arrives from the east.');
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_ACTION, message: 'Quentor arrives from the east.' });

    expect(component.view.messages().length).toBe(1);
    expect(component.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_UPDATE);
    expect(component.view.messages()[0].html).toContain('term-inline-event--player-action');
    expect(component.view.messages()[0].html).toContain('Traveler');
    expect(component.view.messages()[0].html).toContain('Quentor arrives from the east.');
  });

  it('folds social actions into the active room card with a social highlight', () => {
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.SOCIAL_ACTION, message: 'You dance.' });

    expect(component.view.messages().length).toBe(1);
    expect(component.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_UPDATE);
    expect(component.view.messages()[0].html).toContain('term-inline-event--social-action');
    expect(component.view.messages()[0].html).toContain('Social');
    expect(component.view.messages()[0].html).toContain('You dance.');
  });

  it('moves the active room card back to the bottom when it receives updates after inventory', () => {
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

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });
    socket.messages$.next({
      type: GAME_MESSAGE_TYPES.INVENTORY_UPDATE,
      inventory: [
        {
          id: 'item_lantern',
          name: 'Lantern',
          description: 'A warm copper lantern.',
          rarity: 'common',
          equipped: false,
        },
      ],
    });
    socket.messages$.next({ type: GAME_MESSAGE_TYPES.ROOM_ACTION, message: 'Quentor arrives from the east.' });

    expect(component.view.messages().length).toBe(2);
    expect(component.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.INVENTORY_UPDATE);
    expect(component.view.messages()[1].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_UPDATE);
    expect(component.view.messages()[1].html).toContain('You arrive.');
    expect(component.view.messages()[1].html).toContain('Quentor arrives from the east.');
  });

  it('renders help from the command catalog instead of hardcoded frontend lists', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({ type: GAME_MESSAGE_TYPES.HELP, message: '' });

    expect(component.view.messages()).toHaveLength(1);
    expect(component.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.HELP);
    expect(component.view.messages()[0].html).toContain("Traveler's handbook");
    expect(component.view.messages()[0].html).toContain('term-help__body');
    expect(component.view.messages()[0].html).toContain('Exploration');
    expect(component.view.messages()[0].html).toContain('look [target]');
    expect(component.view.messages()[0].html).toContain('Describe surroundings or examine something');
    expect(component.view.messages()[0].html).toContain('Try: look fountain');
    expect(component.view.messages()[0].html).toContain('Use n, s, e, w, u, and d for quick travel.');
    expect(component.view.messages()[0].html).not.toContain('spawn &lt;item&gt; [inv]');
  });

  it('renders moderation notices as a distinct warning card', () => {
    const fixture = TestBed.createComponent(TerminalComponent);
    const component = fixture.componentInstance;

    socket.messages$.next({
      type: GAME_MESSAGE_TYPES.MODERATION_NOTICE,
      message: "That message wasn't sent. Please keep broadcasts free of profanity and hate speech.",
    });

    expect(component.view.messages()).toHaveLength(1);
    expect(component.view.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.MODERATION_NOTICE);
    expect(component.view.messages()[0].html).toContain('Message withheld');
    expect(component.view.messages()[0].html).toContain('broadcast blocked');
  });
});
