import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { TERMINAL_MESSAGE_CLASSES } from '../models/game-message';
import { CommandCatalogService } from './command-catalog.service';
import { CommandBuilderService } from './command-builder.service';
import { GameSocketService } from './game-socket.service';
import { DisplayMessage, TerminalMessageStore } from './terminal-message-store.service';
import { TerminalInputService } from './terminal-input.service';

const TERMINAL_HISTORY_STORAGE_KEY = 'mudGameTerminalHistory';

class MockGameSocketService {
  readonly sent: string[] = [];
  readonly playerStats = signal<Record<string, unknown> | null>(null);

  sendRaw(payload: string): void {
    this.sent.push(payload);
  }
}

class MockCommandBuilderService {
  buildCalls: Array<{ raw: string; isPasswordMode: boolean }> = [];
  response = {
    payload: '{"command":"look"}',
    echo: 'look',
    maskEcho: false,
  };

  build(raw: string, isPasswordMode: boolean) {
    this.buildCalls.push({ raw, isPasswordMode });
    return this.response;
  }
}

class MockCommandCatalogService {
  autocompleteMatches(input: string): string[] {
    const normalized = input.trim().toLowerCase();
    if (!normalized) {
      return [];
    }

    if (normalized === 'lo') {
      return ['look', 'lore', 'locate'];
    }

    if (normalized === 'lo fountain') {
      return ['look fountain', 'lore fountain', 'locate fountain'];
    }

    return [];
  }
}

class MockTerminalMessageStore {
  readonly passwordMode = signal(false);
  readonly addedMessages: DisplayMessage[] = [];
  clearCharacterCreationCalls = 0;

  addDisplayMessage(message: Omit<DisplayMessage, 'id'>): void {
    this.addedMessages.push({ id: this.addedMessages.length + 1, ...message });
  }

  clearCharacterCreation(): void {
    this.clearCharacterCreationCalls += 1;
  }
}

describe('TerminalInputService', () => {
  let socket: MockGameSocketService;
  let commandBuilder: MockCommandBuilderService;
  let store: MockTerminalMessageStore;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        TerminalInputService,
        { provide: CommandCatalogService, useClass: MockCommandCatalogService },
        { provide: GameSocketService, useClass: MockGameSocketService },
        { provide: CommandBuilderService, useClass: MockCommandBuilderService },
        { provide: TerminalMessageStore, useClass: MockTerminalMessageStore },
      ],
    });

    socket = TestBed.inject(GameSocketService) as unknown as MockGameSocketService;
    commandBuilder = TestBed.inject(CommandBuilderService) as unknown as MockCommandBuilderService;
    store = TestBed.inject(TerminalMessageStore) as unknown as MockTerminalMessageStore;
  });

  it('ignores blank input', () => {
    const service = TestBed.inject(TerminalInputService);

    service.inputValue.set('   ');
    service.send();

    expect(commandBuilder.buildCalls).toEqual([]);
    expect(socket.sent).toEqual([]);
    expect(store.addedMessages).toEqual([]);
  });

  it('builds, echoes, sends, and clears command input', () => {
    const service = TestBed.inject(TerminalInputService);
    socket.playerStats.set({ name: 'Traveler' });

    service.inputValue.set('look');
    service.send();

    expect(commandBuilder.buildCalls).toEqual([{ raw: 'look', isPasswordMode: false }]);
    expect(store.addedMessages).toEqual([
      {
        id: 1,
        cssClass: TERMINAL_MESSAGE_CLASSES.SENT,
        html: '&gt; look',
      },
    ]);
    expect(socket.sent).toEqual(['{"command":"look"}']);
    expect(service.inputValue()).toBe('');
  });

  it('masks echoed password input when the builder requests it', () => {
    const service = TestBed.inject(TerminalInputService);
    socket.playerStats.set({ name: 'Traveler' });
    store.passwordMode.set(true);
    commandBuilder.response = {
      payload: '{"input":"secret"}',
      echo: 'secret',
      maskEcho: true,
    };

    service.inputValue.set('secret');
    service.send();

    expect(commandBuilder.buildCalls).toEqual([{ raw: 'secret', isPasswordMode: true }]);
    expect(store.addedMessages[0].html).toBe('&gt; ********');
  });

  it('clears character creation state and submits the selection', () => {
    const service = TestBed.inject(TerminalInputService);

    service.completeCharacterCreation('elf mage');

    expect(store.clearCharacterCreationCalls).toBe(1);
    expect(socket.sent).toEqual(['{"input":"elf mage"}']);
  });

  it('recalls sent commands in reverse order and restores the draft on arrow down', () => {
    const service = TestBed.inject(TerminalInputService);
    socket.playerStats.set({ name: 'Traveler' });

    service.updateInputValue('look');
    service.send();
    service.updateInputValue('say hello');
    service.send();
    service.updateInputValue('ca');

    expect(service.recallPrevious()).toBe(true);
    expect(service.inputValue()).toBe('say hello');

    expect(service.recallPrevious()).toBe(true);
    expect(service.inputValue()).toBe('look');

    expect(service.recallNext()).toBe(true);
    expect(service.inputValue()).toBe('say hello');

    expect(service.recallNext()).toBe(true);
    expect(service.inputValue()).toBe('ca');
  });

  it('does not recall command history while password mode is active', () => {
    const service = TestBed.inject(TerminalInputService);
    socket.playerStats.set({ name: 'Traveler' });

    service.updateInputValue('look');
    service.send();
    service.updateInputValue('secret');
    store.passwordMode.set(true);

    expect(service.recallPrevious()).toBe(false);
    expect(service.inputValue()).toBe('secret');
  });

  it('restores persisted command history after the service is recreated', () => {
    localStorage.setItem(TERMINAL_HISTORY_STORAGE_KEY, JSON.stringify(['look', 'inventory']));

    const service = TestBed.inject(TerminalInputService);
    socket.playerStats.set({ name: 'Traveler' });

    expect(service.recallPrevious()).toBe(true);
    expect(service.inputValue()).toBe('inventory');

    expect(service.recallPrevious()).toBe(true);
    expect(service.inputValue()).toBe('look');
  });

  it('persists new history entries and skips consecutive duplicates', () => {
    const service = TestBed.inject(TerminalInputService);
    socket.playerStats.set({ name: 'Traveler' });

    service.updateInputValue('look');
    service.send();
    service.updateInputValue('look');
    service.send();
    service.updateInputValue('inventory');
    service.send();

    expect(JSON.parse(localStorage.getItem(TERMINAL_HISTORY_STORAGE_KEY) ?? '[]')).toEqual([
      'look',
      'inventory',
    ]);

    expect(service.recallPrevious()).toBe(true);
    expect(service.inputValue()).toBe('inventory');

    expect(service.recallPrevious()).toBe(true);
    expect(service.inputValue()).toBe('look');
  });

  it('cycles command completions and preserves the rest of the line', () => {
    const service = TestBed.inject(TerminalInputService);
    socket.playerStats.set({ name: 'Traveler' });

    service.updateInputValue('lo fountain');

    expect(service.commandCompletionSuggestions()).toEqual([
      'look fountain',
      'lore fountain',
      'locate fountain',
    ]);
    expect(service.activeCommandCompletion()).toBe('look fountain');

    expect(service.acceptNextCompletion()).toBe(true);
    expect(service.inputValue()).toBe('look fountain');

    expect(service.acceptNextCompletion()).toBe(true);
    expect(service.inputValue()).toBe('lore fountain');

    expect(service.acceptNextCompletion()).toBe(true);
    expect(service.inputValue()).toBe('locate fountain');
  });

  it('clears command completion state on manual edits', () => {
    const service = TestBed.inject(TerminalInputService);
    socket.playerStats.set({ name: 'Traveler' });

    service.updateInputValue('lo');
    service.acceptNextCompletion();
    expect(service.activeCommandCompletionIndex()).toBe(0);

    service.updateInputValue('lookout');

    expect(service.commandCompletionSuggestions()).toEqual([]);
    expect(service.activeCommandCompletion()).toBeUndefined();
    expect(service.activeCommandCompletionIndex()).toBe(-1);
  });
});
