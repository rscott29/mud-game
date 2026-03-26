import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { take } from 'rxjs';

export const COMMAND_DISPATCH_MODES = {
  DIRECT: 'DIRECT',
  NATURAL_LANGUAGE: 'NATURAL_LANGUAGE',
} as const;

export type CommandDispatchMode =
  typeof COMMAND_DISPATCH_MODES[keyof typeof COMMAND_DISPATCH_MODES];

export const COMMAND_HELP_CATEGORIES = {
  EXPLORATION: 'Exploration',
  INTERACTION: 'Interaction',
  COMMUNICATION: 'Communication',
  SOCIAL: 'Social',
  SESSION: 'Session',
  GOD: 'God',
} as const;

export type KnownCommandHelpCategory =
  typeof COMMAND_HELP_CATEGORIES[keyof typeof COMMAND_HELP_CATEGORIES];

export type CommandHelpCategory = KnownCommandHelpCategory | (string & {});

export interface CommandCatalogEntry {
  canonicalName: string;
  aliases: string[];
  category: CommandHelpCategory;
  usage: string;
  description: string;
  godOnly: boolean;
  showInHelp: boolean;
  dispatchMode: CommandDispatchMode;
}

interface RawCommandCatalogEntry {
  canonicalName: string;
  aliases: string[];
  category: string;
  usage: string;
  description: string;
  godOnly: boolean;
  showInHelp: boolean;
  dispatchMode: string;
}

export interface HelpCategory {
  title: CommandHelpCategory;
  entries: HelpEntry[];
}

export interface HelpEntry {
  cmd: string;
  desc: string;
  aliasesText?: string;
  example?: string;
}

interface CommandCatalogResponse {
  commands: RawCommandCatalogEntry[];
}

interface ParsedAutocompleteInput {
  leadingWhitespace: string;
  partialToken: string;
  suffix: string;
}

@Injectable({ providedIn: 'root' })
export class CommandCatalogService {
  private readonly http = inject(HttpClient);
  private readonly catalog = signal<CommandCatalogEntry[]>([]);
  private loadStarted = false;

  load(): void {
    if (this.loadStarted) {
      return;
    }

    this.loadStarted = true;
    this.http.get<CommandCatalogResponse>('/api/commands')
      .pipe(take(1))
      .subscribe({
        next: response => {
          this.catalog.set((response?.commands ?? []).map(command => this.normalizeCommand(command)));
        },
        error: () => {
          this.loadStarted = false;
        },
      });
  }

  findByAlias(alias: string): CommandCatalogEntry | undefined {
    this.load();

    const normalized = alias.trim().toLowerCase();
    if (!normalized) {
      return undefined;
    }

    return this.catalog().find(command =>
      command.aliases.some(candidate => candidate.toLowerCase() === normalized)
    );
  }

  autocompleteMatches(input: string, isGod: boolean): string[] {
    this.load();

    const parsedInput = this.parseAutocompleteInput(input);
    if (!parsedInput) {
      return [];
    }

    const { leadingWhitespace, partialToken, suffix } = parsedInput;
    const matches = new Set<string>();

    for (const command of this.catalog()) {
      if (command.godOnly && !isGod) {
        continue;
      }

      for (const alias of command.aliases) {
        const normalizedAlias = alias.trim().toLowerCase();
        if (!normalizedAlias || normalizedAlias === partialToken || !normalizedAlias.startsWith(partialToken)) {
          continue;
        }

        matches.add(`${leadingWhitespace}${normalizedAlias}${suffix}`);
      }
    }

    return [...matches].sort((left, right) => left.length - right.length || left.localeCompare(right));
  }

  autocompleteSuggestion(input: string, isGod: boolean): string | undefined {
    return this.autocompleteMatches(input, isGod)[0];
  }

  helpCategories(isGod: boolean): HelpCategory[] {
    this.load();

    const categories: HelpCategory[] = [];
    const byTitle = new Map<string, HelpCategory>();

    for (const command of this.catalog()) {
      if (!command.showInHelp) {
        continue;
      }
      if (command.godOnly && !isGod) {
        continue;
      }

      let category = byTitle.get(command.category);
      if (!category) {
        category = { title: command.category, entries: [] };
        byTitle.set(command.category, category);
        categories.push(category);
      }

      category.entries.push({
        cmd: command.usage,
        desc: command.description,
        aliasesText: this.aliasSummary(command),
        example: this.helpExample(command),
      });
    }

    return categories;
  }

  helpTips(): string[] {
    this.load();

    const tips: string[] = [];
    if (this.findByAlias('n') && this.findByAlias('u')) {
      tips.push('Use n, s, e, w, u, and d for quick travel.');
    }
    if (this.findByAlias('l')) {
      tips.push('Look supports shortcuts like l and x, and plain language works for contextual actions.');
    }
    if (this.findByAlias('wave')) {
      tips.push('Social actions support self-targets: wave self, wave me, or wave at Mira.');
    }

    return tips;
  }

  private normalizeCommand(command: RawCommandCatalogEntry): CommandCatalogEntry {
    return {
      canonicalName: command.canonicalName,
      aliases: command.aliases ?? [],
      category: this.normalizeCategory(command.category),
      usage: command.usage,
      description: command.description,
      godOnly: command.godOnly,
      showInHelp: command.showInHelp,
      dispatchMode: this.normalizeDispatchMode(command.dispatchMode),
    };
  }

  private aliasSummary(command: CommandCatalogEntry): string | undefined {
    const usageToken = (command.usage.trim().split(/\s+/)[0] ?? '').toLowerCase();
    const usageBare = usageToken.replace(/^\//, '');
    const filtered = new Map<string, string>();

    for (const alias of command.aliases) {
      const normalized = alias.trim().toLowerCase();
      const bare = normalized.replace(/^\//, '');
      if (!bare || bare === usageBare) {
        continue;
      }

      const existing = filtered.get(bare);
      if (!existing || (existing.startsWith('/') && !normalized.startsWith('/'))) {
        filtered.set(bare, normalized);
      }
    }

    const aliases = [...filtered.values()];
    if (aliases.length === 0) {
      return undefined;
    }
    if (aliases.length <= 6) {
      return aliases.join(', ');
    }

    return `${aliases.slice(0, 6).join(', ')} +${aliases.length - 6} more`;
  }

  private helpExample(command: CommandCatalogEntry): string | undefined {
    const usageToken = (command.usage.trim().split(/\s+/)[0] ?? '').replace(/^\//, '');

    switch (command.canonicalName) {
      case 'go':
        return 'n';
      case 'look':
        return 'look fountain';
      case 'talk':
        return 'talk pilgrim';
      case 'take':
        return 'take lantern';
      case 'drop':
        return 'drop lantern';
      case 'equip':
        return 'equip iron sword';
      case 'attack':
        return 'attack wolf';
      case 'world':
        return '/world anyone near the market?';
      case 'dm':
        return '/dm Mira meet me at the gate';
      case 'emote':
        return '/em smiles warmly';
      case 'quest':
        return 'quest';
      case 'give':
        return 'give bread to pilgrim';
      case 'skills':
        return 'skills';
      case 'me':
        return 'me';
      default:
        if (command.category === COMMAND_HELP_CATEGORIES.SOCIAL && command.usage.includes('[target|self]')) {
          return `${usageToken} self`;
        }
        if (usageToken === 'say') {
          return 'say hello there';
        }
        return undefined;
    }
  }

  private normalizeDispatchMode(dispatchMode: string | null | undefined): CommandDispatchMode {
    return dispatchMode === COMMAND_DISPATCH_MODES.NATURAL_LANGUAGE
      ? COMMAND_DISPATCH_MODES.NATURAL_LANGUAGE
      : COMMAND_DISPATCH_MODES.DIRECT;
  }

  private normalizeCategory(category: string | null | undefined): CommandHelpCategory {
    const normalized = category?.trim() ?? '';
    if (!normalized) {
      return COMMAND_HELP_CATEGORIES.EXPLORATION;
    }

    const knownCategory = Object.values(COMMAND_HELP_CATEGORIES).find(value => value === normalized);

    return knownCategory ?? normalized;
  }

  private parseAutocompleteInput(input: string): ParsedAutocompleteInput | null {
    const leadingWhitespace = input.match(/^\s*/)?.[0] ?? '';
    const trimmedLeading = input.slice(leadingWhitespace.length);
    if (!trimmedLeading) {
      return null;
    }

    const tokenMatch = trimmedLeading.match(/^(\S+)([\s\S]*)$/);
    if (!tokenMatch) {
      return null;
    }

    const partialToken = tokenMatch[1].toLowerCase();
    if (!partialToken) {
      return null;
    }

    return {
      leadingWhitespace,
      partialToken,
      suffix: tokenMatch[2] ?? '',
    };
  }
}
