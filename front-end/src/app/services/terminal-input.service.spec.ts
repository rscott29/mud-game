import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { TERMINAL_MESSAGE_CLASSES } from '../models/game-message';
import { CommandBuilderService } from './command-builder.service';
import { GameSocketService } from './game-socket.service';
import { DisplayMessage, TerminalMessageStore } from './terminal-message-store.service';
import { TerminalInputService } from './terminal-input.service';

class MockGameSocketService {
  readonly sent: string[] = [];

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
    TestBed.configureTestingModule({
      providers: [
        TerminalInputService,
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
});
