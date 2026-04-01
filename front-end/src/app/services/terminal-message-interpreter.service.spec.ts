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

  helpTips(): [] {
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
      expect(result.message.html).toContain('term-card--prompt');
      expect(result.message.html).toContain('Enter your password:');
    }
  });

  it('turns help messages into a dedicated help interpretation', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.HELP,
      message: 'god',
    });

    expect(result.kind).toBe('help');
    if (result.kind === 'help') {
      expect(result.isGod).toBe(true);
      expect(result.stateChanges).toBeNull();
    }
  });

  it('renders player overview messages as a dedicated display card', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.PLAYER_OVERVIEW,
      message: 'Axi',
      playerStats: {
        health: 20,
        maxHealth: 24,
        mana: 11,
        maxMana: 15,
        movement: 14,
        maxMovement: 18,
        level: 4,
        maxLevel: 10,
        xpProgress: 20,
        xpForNextLevel: 100,
        totalXp: 320,
        isGod: false,
        characterClass: 'mage',
      },
      combatStats: {
        armor: 3,
        minDamage: 4,
        maxDamage: 8,
        hitChance: 82,
        critChance: 0,
      },
      inventory: [
        {
          id: 'item_practice_sword',
          name: 'Practice Sword',
          description: 'A training blade.',
          rarity: 'common',
          equipped: true,
          equippedSlot: 'Main weapon',
        },
      ],
    });

    expect(result.kind).toBe('display');
    if (result.kind === 'display') {
      expect(result.message.cssClass).toBe(TERMINAL_MESSAGE_CLASSES.PLAYER_OVERVIEW);
      expect(result.message.html).toContain('Character sheet');
      expect(result.message.html).toContain('Axi');
      expect(result.message.html).toContain('Combat profile');
      expect(result.message.html).toContain('4-8');
      expect(result.message.html).toContain('82%');
      expect(result.message.html).toContain('Practice Sword');
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

  it('uses the combat feed card for combat-marked narrative fallback', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.NARRATIVE,
      message: "<div class='combat-line npc-hit'><span class='combat-badge combat-badge--enemy'>Dummy</span><span class='combat-body'><span class='qualifier-hit'>hits</span> <span class='combat-entity combat-entity--you'>you</span>.</span></div>",
    });

    expect(result.kind).toBe('room_inline');
    if (result.kind === 'room_inline') {
      expect(result.fallback.cssClass).toBe(TERMINAL_MESSAGE_CLASSES.NARRATIVE);
      expect(result.fallback.html).toContain('term-card--combat');
      expect(result.fallback.html).toContain('Combat feed');
    }
  });

  it('turns social actions into inline room content with a distinct fallback style', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.SOCIAL_ACTION,
      message: 'You dance.',
    });

    expect(result.kind).toBe('room_inline');
    if (result.kind === 'room_inline') {
      expect(result.inlineFragment).toContain('term-inline-event--social-action');
      expect(result.inlineFragment).toContain('Social');
      expect(result.fallback.cssClass).toBe(TERMINAL_MESSAGE_CLASSES.SOCIAL_ACTION);
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

  it('renders moderation notices as a dedicated display card', () => {
    const interpreter = TestBed.inject(TerminalMessageInterpreterService);

    const result = interpreter.interpret({
      type: GAME_MESSAGE_TYPES.MODERATION_NOTICE,
      message: "That message wasn't sent.",
    });

    expect(result.kind).toBe('display');
    if (result.kind === 'display') {
      expect(result.message.cssClass).toBe(TERMINAL_MESSAGE_CLASSES.MODERATION_NOTICE);
      expect(result.message.html).toContain('Message withheld');
      expect(result.message.html).toContain('broadcast blocked');
    }
  });
});
