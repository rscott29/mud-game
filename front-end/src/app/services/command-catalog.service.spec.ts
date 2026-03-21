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
});
