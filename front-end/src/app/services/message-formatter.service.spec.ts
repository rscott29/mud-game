import { TestBed } from '@angular/core/testing';

import { GAME_MESSAGE_TYPES } from '../models/game-message';
import { CommandCatalogService } from './command-catalog.service';
import { MessageFormatterService } from './message-formatter.service';

class MockCommandCatalogService {
  categories: Array<{
    title: string;
    entries: Array<{ cmd: string; desc: string; aliasesText?: string; example?: string }>;
  }> = [];

  tips: string[] = [];

  load(): void {}

  helpCategories(): Array<{
    title: string;
    entries: Array<{ cmd: string; desc: string; aliasesText?: string; example?: string }>;
  }> {
    return this.categories;
  }

  helpTips(): string[] {
    return this.tips;
  }
}

describe('MessageFormatterService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MessageFormatterService,
        { provide: CommandCatalogService, useClass: MockCommandCatalogService },
      ],
    });
  });

  it('formats the help card through the facade', () => {
    const formatter = TestBed.inject(MessageFormatterService);
    const catalog = TestBed.inject(CommandCatalogService) as unknown as MockCommandCatalogService;
    catalog.categories = [
      {
        title: 'Travel',
        entries: [
          {
            cmd: 'north',
            desc: 'Walk north.',
            aliasesText: 'n',
            example: 'north',
          },
        ],
      },
    ];
    catalog.tips = ['Use plain language if you forget a command.'];

    const result = formatter.formatHelpCard(false);

    expect(result.html).toContain("Traveler's handbook");
    expect(result.html).toContain('Travel');
    expect(result.html).toContain('north');
    expect(result.html).toContain('Use plain language if you forget a command.');
  });

  it('formats the player overview through the facade', () => {
    const formatter = TestBed.inject(MessageFormatterService);

    const result = formatter.formatPlayerOverview({
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

    expect(result.html).toContain('Character sheet');
    expect(result.html).toContain('Axi');
    expect(result.html).toContain('Mage');
    expect(result.html).toContain('4-8');
    expect(result.html).toContain('Practice Sword');
  });

  it('formats auth prompts through the facade', () => {
    const formatter = TestBed.inject(MessageFormatterService);

    const result = formatter.formatAuthPrompt('Welcome back.\n\nEnter your username:');

    expect(result.html).toContain('term-card--prompt-auth');
    expect(result.html).toContain('Sign in');
    expect(result.html).toContain('Enter your username:');
  });

  it('escapes unsafe html in social action fallback output', () => {
    const formatter = TestBed.inject(MessageFormatterService);

    const result = formatter.formatSocialActionMessage(
      'waves <strong>boldly</strong> <img src=x onerror=alert(1)>'
    );

    expect(result.html).toContain('<strong>boldly</strong>');
    expect(result.html).toContain('&lt;img src=x onerror=alert(1)&gt;');
    expect(result.html).not.toContain('<img src=x onerror=alert(1)>');
  });

  it('escapes unsafe html in room action fallback output', () => {
    const formatter = TestBed.inject(MessageFormatterService);

    const result = formatter.formatRoomActionMessage(
      'travels past <script>alert("boom")</script>'
    );

    expect(result.html).toContain('&lt;script&gt;alert(&quot;boom&quot;)&lt;/script&gt;');
    expect(result.html).not.toContain('<script>alert("boom")</script>');
  });
});
