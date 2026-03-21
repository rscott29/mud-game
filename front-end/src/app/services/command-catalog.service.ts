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
  entries: Array<{ cmd: string; desc: string }>;
}

interface CommandCatalogResponse {
  commands: RawCommandCatalogEntry[];
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
      });
    }

    return categories;
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
}
