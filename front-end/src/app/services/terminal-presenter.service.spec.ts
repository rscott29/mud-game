import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import {
  CHARACTER_CREATION_STEPS,
  CONNECTION_STATUSES,
  type CharacterCreationDto,
  type ConnectionStatus,
  type PlayerStatsDto,
  TERMINAL_MESSAGE_CLASSES,
} from '../models/game-message';
import { GameSocketService } from './game-socket.service';
import { TerminalInputService } from './terminal-input.service';
import { DisplayMessage, TerminalMessageStore } from './terminal-message-store.service';
import { TerminalPresenterService } from './terminal-presenter.service';
import { ZoomService } from './zoom.service';

class MockGameSocketService {
  readonly status = signal<ConnectionStatus>(CONNECTION_STATUSES.CONNECTED);
  readonly playerStats = signal<PlayerStatsDto | null>(null);
}

class MockTerminalMessageStore {
  readonly messages = signal<DisplayMessage[]>([]);
  readonly passwordMode = signal(false);
  readonly characterCreationData = signal<CharacterCreationDto | null>(null);
}

class MockZoomService {
  readonly zoomLevel = signal(100);
}

class MockTerminalInputService {
  readonly inputValue = signal('');
  readonly commandCompletionSuggestions = signal<string[]>([]);
  readonly activeCommandCompletionIndex = signal(-1);
  readonly activeCommandCompletion = signal<string | undefined>(undefined);
}

describe('TerminalPresenterService', () => {
  let socket: MockGameSocketService;
  let input: MockTerminalInputService;
  let store: MockTerminalMessageStore;
  let zoom: MockZoomService;

  beforeEach(() => {
    document.title = 'MudGameUi';

    TestBed.configureTestingModule({
      providers: [
        TerminalPresenterService,
        { provide: GameSocketService, useClass: MockGameSocketService },
        { provide: TerminalInputService, useClass: MockTerminalInputService },
        { provide: TerminalMessageStore, useClass: MockTerminalMessageStore },
        { provide: ZoomService, useClass: MockZoomService },
      ],
    });

    socket = TestBed.inject(GameSocketService) as unknown as MockGameSocketService;
    input = TestBed.inject(TerminalInputService) as unknown as MockTerminalInputService;
    store = TestBed.inject(TerminalMessageStore) as unknown as MockTerminalMessageStore;
    zoom = TestBed.inject(ZoomService) as unknown as MockZoomService;
  });

  it('maps password mode into input affordances', () => {
    const presenter = TestBed.inject(TerminalPresenterService);

    expect(presenter.inputType()).toBe('text');
    expect(presenter.promptLabel()).toBe('name');
    expect(presenter.placeholder()).toBe('Username');
    expect(presenter.hudEmptyMessage()).toBe('Sign in to begin or continue your adventure.');
    expect(presenter.isAuthScreen()).toBe(true);

    store.passwordMode.set(true);

    expect(presenter.inputType()).toBe('password');
    expect(presenter.promptLabel()).toBe('pass');
    expect(presenter.placeholder()).toBe('Password');
    expect(presenter.hudEmptyMessage()).toBe('Enter your password to continue.');
  });

  it('maps connection status into labels and classes', () => {
    const presenter = TestBed.inject(TerminalPresenterService);

    expect(presenter.statusLabel()).toBe('Connected');
    expect(presenter.statusClass()).toEqual({
      connected: true,
      reconnecting: false,
      disconnected: false,
    });

    socket.status.set(CONNECTION_STATUSES.RECONNECTING);

    expect(presenter.statusLabel()).toBe('Reconnecting...');
    expect(presenter.statusClass()).toEqual({
      connected: false,
      reconnecting: true,
      disconnected: false,
    });
  });

  it('surfaces the document title as the world title', () => {
    document.title = 'Lantern MUD';

    const presenter = TestBed.inject(TerminalPresenterService);

    expect(presenter.worldTitle()).toBe('Lantern MUD');
  });

  it('formats hud labels from player stats and god mode', () => {
    const presenter = TestBed.inject(TerminalPresenterService);

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
      characterClass: 'dark_mage',
      inCombat: false,
    });

    expect(presenter.classLabel()).toBe('Dark Mage');
    expect(presenter.promptLabel()).toBe('cmd');
    expect(presenter.placeholder()).toBe('Speak a command...');
    expect(presenter.isAuthScreen()).toBe(false);
    expect(presenter.levelLabel()).toBe('Level 3 / 10');
    expect(presenter.hudHealth()).toBe('HP 12/20');
    expect(presenter.hudMana()).toBe('MP 7/10');
    expect(presenter.hudMovement()).toBe('MV 8/12');
    expect(presenter.xpPercent()).toBe(25);
    expect(presenter.xpLabel()).toBe('25 / 100 XP');

    socket.playerStats.set({
      health: 999,
      maxHealth: 999,
      mana: 999,
      maxMana: 999,
      movement: 999,
      maxMovement: 999,
      level: 100,
      maxLevel: 10,
      xpProgress: 0,
      xpForNextLevel: 0,
      totalXp: 9999,
      isGod: true,
      characterClass: 'archon',
      inCombat: false,
    });

    expect(presenter.levelLabel()).toBe('Level 100');
    expect(presenter.hudHealth()).toBe('HP ∞');
    expect(presenter.hudMana()).toBe('MP ∞');
    expect(presenter.hudMovement()).toBe('MV ∞');
    expect(presenter.xpPercent()).toBe(100);
    expect(presenter.xpLabel()).toBe('Max level');
  });

  it('passes through shared store and zoom state', () => {
    const presenter = TestBed.inject(TerminalPresenterService);

    store.messages.set([{ id: 1, cssClass: TERMINAL_MESSAGE_CLASSES.SYSTEM, html: 'hello' }]);
    store.characterCreationData.set({ step: CHARACTER_CREATION_STEPS.DESCRIPTION });
    zoom.zoomLevel.set(115);

    expect(presenter.messages()).toHaveLength(1);
    expect(presenter.characterCreationData()?.step).toBe(CHARACTER_CREATION_STEPS.DESCRIPTION);
    expect(presenter.zoomLevel()).toBe(115);
  });

  it('offers a command suggestion only for in-game non-password input', () => {
    const presenter = TestBed.inject(TerminalPresenterService);

    input.activeCommandCompletion.set(undefined);
    expect(presenter.commandSuggestion()).toBeUndefined();

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
      inCombat: false,
    });
    input.commandCompletionSuggestions.set(['look', 'lore', 'locate']);
    input.activeCommandCompletionIndex.set(0);
    input.activeCommandCompletion.set('look');

    expect(presenter.commandSuggestion()).toBe('look');
    expect(presenter.visibleCommandSuggestions()).toEqual(['look', 'lore', 'locate']);

    store.passwordMode.set(true);
    expect(presenter.commandSuggestion()).toBe('look');
  });
});
