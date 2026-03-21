import { TestBed } from '@angular/core/testing';

import { GAME_MESSAGE_TYPES, TERMINAL_MESSAGE_CLASSES } from '../models/game-message';
import { CommandCatalogService } from './command-catalog.service';
import { MessageFormatterService } from './message-formatter.service';
import { TerminalMessageStore } from './terminal-message-store.service';

class MockCommandCatalogService {
  load(): void {}

  helpCategories(): [] {
    return [];
  }
}

describe('TerminalMessageStore', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TerminalMessageStore,
        MessageFormatterService,
        { provide: CommandCatalogService, useClass: MockCommandCatalogService },
      ],
    });
  });

  it('merges same-room updates into the active room entry', () => {
    const store = TestBed.inject(TerminalMessageStore);
    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    store.upsertRoomMessage({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'A dog barks.', room });
    store.upsertRoomMessage({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'A bard sings.', room });

    expect(store.messages()).toHaveLength(1);
    expect(store.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_UPDATE);
    expect(store.messages()[0].html).toContain('A dog barks.');
    expect(store.messages()[0].html).toContain('A bard sings.');
  });

  it('starts a fresh room entry for room refreshes even in the same room', () => {
    const store = TestBed.inject(TerminalMessageStore);
    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    store.upsertRoomMessage({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });
    store.upsertRoomMessage({ type: GAME_MESSAGE_TYPES.ROOM_REFRESH, message: 'You take a slower look around.', room });

    expect(store.messages()).toHaveLength(2);
    expect(store.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_UPDATE);
    expect(store.messages()[1].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_REFRESH);
    expect(store.messages()[1].html).toContain('You look around');
  });

  it('appends inline room events into the active room entry', () => {
    const store = TestBed.inject(TerminalMessageStore);
    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    store.upsertRoomMessage({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });
    store.appendToActiveRoomMessage(
      { type: GAME_MESSAGE_TYPES.ROOM_ACTION, message: 'Quentor arrives from the east.' },
      '<span class="term-inline-event">Quentor arrives from the east.</span>',
      {
        cssClass: TERMINAL_MESSAGE_CLASSES.ROOM_ACTION,
        html: '<div>Quentor arrives from the east.</div>',
      }
    );

    expect(store.messages()).toHaveLength(1);
    expect(store.messages()[0].html).toContain('You arrive.');
    expect(store.messages()[0].html).toContain('Quentor arrives from the east.');
  });

  it('moves the active room entry back to the bottom when it receives a later update', () => {
    const store = TestBed.inject(TerminalMessageStore);
    const room = {
      id: 'town_square',
      name: 'Town Square',
      description: 'A lively square.',
      exits: ['north'],
      items: [],
      npcs: [],
      players: [],
    };

    store.upsertRoomMessage({ type: GAME_MESSAGE_TYPES.ROOM_UPDATE, message: 'You arrive.', room });
    store.addDisplayMessage({
      cssClass: TERMINAL_MESSAGE_CLASSES.INVENTORY_UPDATE,
      html: '<div class="term-card">Inventory</div>',
    });
    store.appendToActiveRoomMessage(
      { type: GAME_MESSAGE_TYPES.ROOM_ACTION, message: 'Quentor arrives from the east.' },
      '<span class="term-inline-event">Quentor arrives from the east.</span>',
      {
        cssClass: TERMINAL_MESSAGE_CLASSES.ROOM_ACTION,
        html: '<div>Quentor arrives from the east.</div>',
      }
    );

    expect(store.messages()).toHaveLength(2);
    expect(store.messages()[0].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.INVENTORY_UPDATE);
    expect(store.messages()[1].cssClass).toBe(TERMINAL_MESSAGE_CLASSES.ROOM_UPDATE);
    expect(store.messages()[1].html).toContain('Quentor arrives from the east.');
  });
});
