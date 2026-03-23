import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import {
  COMMAND_DISPATCH_MODES,
  COMMAND_HELP_CATEGORIES,
  CommandCatalogService,
} from './command-catalog.service';

describe('CommandCatalogService', () => {
  let httpMock: HttpTestingController;
  let service: CommandCatalogService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CommandCatalogService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    service = TestBed.inject(CommandCatalogService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('normalizes dispatch modes and known categories from the backend catalog', () => {
    service.load();

    const request = httpMock.expectOne('/api/commands');
    request.flush({
      commands: [
        {
          canonicalName: 'look',
          aliases: ['look', 'l'],
          category: 'Exploration',
          usage: 'look [target]',
          description: 'Describe surroundings or examine something',
          godOnly: false,
          showInHelp: true,
          dispatchMode: 'NATURAL_LANGUAGE',
        },
      ],
    });

    const look = service.findByAlias('l');

    expect(look?.category).toBe(COMMAND_HELP_CATEGORIES.EXPLORATION);
    expect(look?.dispatchMode).toBe(COMMAND_DISPATCH_MODES.NATURAL_LANGUAGE);
  });

  it('falls back invalid dispatch modes to DIRECT while preserving unknown categories', () => {
    service.load();

    const request = httpMock.expectOne('/api/commands');
    request.flush({
      commands: [
        {
          canonicalName: 'chant',
          aliases: ['chant'],
          category: 'Mystic',
          usage: 'chant <verse>',
          description: 'Speak a strange rite',
          godOnly: false,
          showInHelp: true,
          dispatchMode: 'SOMETHING_ODD',
        },
      ],
    });

    const chant = service.findByAlias('chant');
    const categories = service.helpCategories(false);

    expect(chant?.category).toBe('Mystic');
    expect(chant?.dispatchMode).toBe(COMMAND_DISPATCH_MODES.DIRECT);
    expect(categories[0].title).toBe('Mystic');
  });

  it('surfaces alias summaries, examples, and quick tips for help rendering', () => {
    service.load();

    const request = httpMock.expectOne('/api/commands');
    request.flush({
      commands: [
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
          canonicalName: 'go',
          aliases: ['go', 'move', 'north', 'n', 'south', 's', 'east', 'e', 'west', 'w', 'up', 'u', 'down', 'd'],
          category: 'Exploration',
          usage: 'go <direction>',
          description: 'Move in a direction (n/s/e/w/u/d)',
          godOnly: false,
          showInHelp: true,
          dispatchMode: 'DIRECT',
        },
        {
          canonicalName: 'wave',
          aliases: ['wave', 'wav'],
          category: 'Social',
          usage: 'wave [target|self]',
          description: 'Wave to someone or simply wave.',
          godOnly: false,
          showInHelp: true,
          dispatchMode: 'DIRECT',
        },
      ],
    });

    const categories = service.helpCategories(false);
    const tips = service.helpTips();
    const look = categories[0].entries.find(entry => entry.cmd === 'look [target]');
    const go = categories[0].entries.find(entry => entry.cmd === 'go <direction>');
    const social = categories[1].entries.find(entry => entry.cmd === 'wave [target|self]');

    expect(look?.aliasesText).toBe('l, examine, x');
    expect(look?.example).toBe('look fountain');
    expect(go?.aliasesText).toContain('move');
    expect(go?.example).toBe('n');
    expect(social?.example).toBe('wave self');
    expect(tips).toContain('Use n, s, e, w, u, and d for quick travel.');
  });
});
