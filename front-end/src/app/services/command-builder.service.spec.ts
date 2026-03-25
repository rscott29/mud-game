import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { CommandBuilderService } from './command-builder.service';
import { COMMAND_DISPATCH_MODES, CommandCatalogService, type CommandCatalogEntry } from './command-catalog.service';

class MockCommandCatalogService {
  readonly entries = signal<CommandCatalogEntry[]>([
    {
      canonicalName: 'follow',
      aliases: ['follow', 'party'],
      category: 'Social',
      usage: 'follow <player|stop>',
      description: 'Follow a player and join their group',
      godOnly: false,
      showInHelp: true,
      dispatchMode: COMMAND_DISPATCH_MODES.DIRECT,
    },
    {
      canonicalName: 'look',
      aliases: ['look', 'l'],
      category: 'Exploration',
      usage: 'look [target]',
      description: 'Look around or inspect something',
      godOnly: false,
      showInHelp: true,
      dispatchMode: COMMAND_DISPATCH_MODES.NATURAL_LANGUAGE,
    },
  ]);

  load(): void {
    // No-op for tests.
  }

  findByAlias(alias: string): CommandCatalogEntry | undefined {
    const normalized = alias.trim().toLowerCase();
    return this.entries().find(command =>
      command.aliases.some(candidate => candidate.toLowerCase() === normalized)
    );
  }
}

describe('CommandBuilderService', () => {
  let service: CommandBuilderService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CommandBuilderService,
        { provide: CommandCatalogService, useClass: MockCommandCatalogService },
      ],
    });

    service = TestBed.inject(CommandBuilderService);
  });

  it('builds follow commands as direct payloads', () => {
    const result = service.build('follow axi', false);

    expect(JSON.parse(result.payload)).toEqual({
      command: 'follow',
      args: ['axi'],
    });
  });

  it('builds look text as natural-language payloads', () => {
    const result = service.build('look at fountain', false);

    expect(JSON.parse(result.payload)).toEqual({
      input: 'look at fountain',
    });
  });
});