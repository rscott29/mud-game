import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { PlayerStatsDto } from '../models/game-message';
import { CommandCatalogService } from './command-catalog.service';
import { MessageFormatterService } from './message-formatter.service';
import {
  ClassSkillsResponse,
  SkillProgressionService,
} from './skill-progression.service';
import { TerminalClassProgressionService } from './terminal-class-progression.service';

class MockCommandCatalogService {
  load(): void {}

  helpCategories(): [] {
    return [];
  }
}

class MockSkillProgressionService {
  response$: Observable<ClassSkillsResponse | null> = of(null);

  getSkillsForClass(): Observable<ClassSkillsResponse | null> {
    return this.response$;
  }
}

describe('TerminalClassProgressionService', () => {
  let skillProgression: MockSkillProgressionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TerminalClassProgressionService,
        MessageFormatterService,
        { provide: CommandCatalogService, useClass: MockCommandCatalogService },
        { provide: SkillProgressionService, useClass: MockSkillProgressionService },
      ],
    });

    skillProgression = TestBed.inject(SkillProgressionService) as unknown as MockSkillProgressionService;
  });

  it('emits a loading card followed by the class progression card', () => {
    const service = TestBed.inject(TerminalClassProgressionService);
    const stats: PlayerStatsDto = {
      health: 10,
      maxHealth: 10,
      mana: 8,
      maxMana: 8,
      movement: 12,
      maxMovement: 12,
      level: 3,
      maxLevel: 10,
      xpProgress: 25,
      xpForNextLevel: 100,
      totalXp: 250,
      isGod: false,
      characterClass: 'mage',
    };

    skillProgression.response$ = of({
      characterClass: 'mage',
      skills: [
        {
          id: 'arcane_bolt',
          name: 'Arcane Bolt',
          unlockLevel: 1,
          type: 'ACTIVE',
          passiveBonuses: null,
        },
      ],
    });

    const messages: string[] = [];
    service.request(stats).subscribe(message => messages.push(message.html));

    expect(messages).toHaveLength(2);
    expect(messages[0]).toContain('Unfurling the skill ledger');
    expect(messages[1]).toContain('Mage arts');
    expect(messages[1]).toContain('Arcane Bolt');
  });

  it('returns an error card immediately when no class is available', () => {
    const service = TestBed.inject(TerminalClassProgressionService);

    const messages: string[] = [];
    service.request(null).subscribe(message => messages.push(message.html));

    expect(messages).toHaveLength(1);
    expect(messages[0]).toContain('The skill ledger is quiet');
  });

  it('recovers with an error card when the progression request fails', () => {
    const service = TestBed.inject(TerminalClassProgressionService);
    const stats: PlayerStatsDto = {
      health: 10,
      maxHealth: 10,
      mana: 8,
      maxMana: 8,
      movement: 12,
      maxMovement: 12,
      level: 3,
      maxLevel: 10,
      xpProgress: 25,
      xpForNextLevel: 100,
      totalXp: 250,
      isGod: false,
      characterClass: 'mage',
    };

    skillProgression.response$ = throwError(() => new Error('boom'));

    const messages: string[] = [];
    service.request(stats).subscribe(message => messages.push(message.html));

    expect(messages).toHaveLength(2);
    expect(messages[0]).toContain('Unfurling the skill ledger');
    expect(messages[1]).toContain('The skill ledger is quiet');
  });
});
