import { TestBed } from '@angular/core/testing';

import { GAME_MESSAGE_TYPES, TERMINAL_MESSAGE_CLASSES } from '../models/game-message';
import { CommandCatalogService } from './command-catalog.service';
import { MessageFormatterService } from './message-formatter.service';
import { TerminalMessageInterpreterService } from './terminal-message-interpreter.service';

class MockCommandCatalogService {
  load(): void {}

  helpCategories(): [] {
    return [];
  }
}

describe('TerminalMessageInterpreterService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TerminalMessageInterpreterService,
        MessageFormatterService,
        { provide: CommandCatalogService, useClass: MockCommandCatalogService },
      ],
    });
  });

  it('routes room updates into room-display handling', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);
    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.ROOM_UPDATE,
      message: 'You arrive.',
      room,
    });

    expect(result.kind).toBe('room_display');
    if (result.kind === 'room_display') {
      expect(result.source.room?.id).toBe('town_square');
      expect(result.stateChanges).toBeNull();
    }
  });

  it('treats auth prompts as display messages and updates password state', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.AUTH_PROMPT,
      message: 'Enter your password:',
      mask: true,
    });

    expect(result.kind).toBe('display');
    expect(result.stateChanges).toEqual({
      clearMessages: false,
      passwordMode: true,
    });
    if (result.kind === 'display') {
      expect(result.message.cssClass).toBe(TERMINAL_MESSAGE_CLASSES.AUTH_PROMPT);
      expect(result.message.html).toContain('The gatehouse asks');
    }
  });

  it('turns room-scoped narrative into inline room content with a fallback', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.NARRATIVE,
      message: 'Quentor arrives from the east.',
    });

    expect(result.kind).toBe('room_inline');
    if (result.kind === 'room_inline') {
      expect(result.inlineFragment).toContain('Quentor arrives from the east.');
      expect(result.fallback.cssClass).toBe(TERMINAL_MESSAGE_CLASSES.NARRATIVE);
    }
  });

  it('keeps class progression as a dedicated orchestration signal', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.CLASS_PROGRESSION,
      message: 'show me my skills',
    });

    expect(result.kind).toBe('class_progression');
    expect(result.stateChanges).toBeNull();
  });
});
