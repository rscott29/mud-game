import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { take } from 'rxjs';

export type CommandDispatchMode = 'DIRECT' | 'NATURAL_LANGUAGE';

export interface CommandCatalogEntry {
  canonicalName: string;
  aliases: string[];
  category: string;
  usage: string;
  description: string;
  godOnly: boolean;
  showInHelp: boolean;
  dispatchMode: CommandDispatchMode;
}

interface CommandCatalogResponse {
  commands: CommandCatalogEntry[];
}

export interface HelpCategory {
  title: string;
  entries: Array<{ cmd: string; desc: string }>;
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
          this.catalog.set(response?.commands ?? []);
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
}
