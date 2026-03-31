import { Injectable, computed, inject, signal } from '@angular/core';

import { CommandCatalogService } from './command-catalog.service';
import { GameSocketService } from './game-socket.service';
import { CommandBuilderService } from './command-builder.service';
import { TerminalMessageStore } from './terminal-message-store.service';
import { escapeHtml } from '../utils/html';
import { TERMINAL_MESSAGE_CLASSES } from '../models/game-message';

const TERMINAL_HISTORY_STORAGE_KEY = 'mudGameTerminalHistory';

@Injectable()
export class TerminalInputService {
  private static readonly MAX_HISTORY_ENTRIES = 100;

  private readonly commandCatalog = inject(CommandCatalogService);
  private readonly socketService = inject(GameSocketService);
  private readonly commandBuilder = inject(CommandBuilderService);
  private readonly store = inject(TerminalMessageStore);
  private readonly commandHistory = this.readStoredHistory();
  private readonly completionBaseInput = signal<string | null>(null);
  private readonly completionCycleIndex = signal(0);
  private historyIndex: number | null = null;
  private historyDraft = '';

  readonly inputValue = signal('');
  readonly commandCompletionSuggestions = computed(() => {
    if (!this.canAutocomplete()) {
      return [];
    }

    const completionBaseInput = this.completionBaseInput() ?? this.inputValue();
    return this.commandCatalog.autocompleteMatches(
      completionBaseInput,
      this.socketService.playerStats()?.isGod ?? false
    );
  });
  readonly activeCommandCompletionIndex = computed(() => {
    const suggestions = this.commandCompletionSuggestions();
    if (suggestions.length === 0) {
      return -1;
    }

    return Math.min(this.completionCycleIndex(), suggestions.length - 1);
  });
  readonly activeCommandCompletion = computed(() => {
    const suggestions = this.commandCompletionSuggestions();
    const activeIndex = this.activeCommandCompletionIndex();
    return activeIndex >= 0 ? suggestions[activeIndex] : undefined;
  });

  updateInputValue(value: string): void {
    this.inputValue.set(value);
    this.resetCompletionState();
    this.stopHistoryNavigationOnManualEdit(value);
  }

  send(): void {
    this.submitCommand(this.inputValue().trim(), true, true);
  }

  sendCommand(command: string, echo = true): void {
    this.submitCommand(command.trim(), echo, false);
  }

  completeCharacterCreation(selection: string): void {
    this.resetCompletionState();
    this.store.clearCharacterCreation();
    this.socketService.sendRaw(JSON.stringify({ input: selection }));
  }

  acceptNextCompletion(): boolean {
    const suggestions = this.commandCompletionSuggestions();
    if (suggestions.length === 0) {
      this.resetCompletionState();
      return false;
    }

    const completionBaseInput = this.completionBaseInput() ?? this.inputValue();
    const nextIndex = this.completionBaseInput() === null
      ? 0
      : (this.completionCycleIndex() + 1) % suggestions.length;

    this.completionBaseInput.set(completionBaseInput);
    this.completionCycleIndex.set(nextIndex);
    this.inputValue.set(suggestions[nextIndex]);
    return true;
  }

  recallPrevious(): boolean {
    if (!this.canNavigateHistory()) {
      return false;
    }

    this.resetCompletionState();

    if (this.historyIndex === null) {
      this.beginHistoryNavigation();
    } else if (this.historyIndex > 0) {
      this.historyIndex -= 1;
    }

    const historyIndex = this.historyIndex;
    if (historyIndex === null) {
      return false;
    }

    this.showHistoryEntry(historyIndex);
    return true;
  }

  recallNext(): boolean {
    if (this.historyIndex === null) {
      return false;
    }

    this.resetCompletionState();

    if (this.historyIndex < this.commandHistory.length - 1) {
      this.historyIndex += 1;
      this.showHistoryEntry(this.historyIndex);
      return true;
    }

    this.inputValue.set(this.historyDraft);
    this.resetHistoryNavigation();
    return true;
  }

  private isInGame(): boolean {
    return this.socketService.playerStats() !== null;
  }

  private canNavigateHistory(): boolean {
    return !this.store.passwordMode() && this.isInGame() && this.commandHistory.length > 0;
  }

  private canAutocomplete(): boolean {
    return !this.store.passwordMode() && this.isInGame();
  }

  private stopHistoryNavigationOnManualEdit(value: string): void {
    if (this.historyIndex === null) {
      return;
    }

    const currentEntry = this.commandHistory[this.historyIndex];
    if (value !== currentEntry) {
      this.historyDraft = value;
      this.historyIndex = null;
    }
  }

  private recordCommandHistory(command: string, isPasswordMode: boolean): void {
    if (isPasswordMode || !this.isInGame()) {
      return;
    }

    this.pushHistory(command);
  }

  private beginHistoryNavigation(): void {
    this.historyDraft = this.inputValue();
    this.historyIndex = this.commandHistory.length - 1;
  }

  private showHistoryEntry(index: number): void {
    this.inputValue.set(this.commandHistory[index]);
  }

  private clearInputAfterSend(): void {
    this.inputValue.set('');
    this.resetHistoryNavigation();
    this.resetCompletionState();
  }

  private echoSentCommand(echo: string, maskEcho: boolean): void {
    this.store.addDisplayMessage({
      cssClass: TERMINAL_MESSAGE_CLASSES.SENT,
      html: `&gt; ${maskEcho ? '********' : escapeHtml(echo)}`,
    });
  }

  private submitCommand(command: string, echo: boolean, clearInput: boolean): void {
    if (!command) {
      return;
    }

    const isPasswordMode = this.store.passwordMode();
    this.recordCommandHistory(command, isPasswordMode);

    const { payload, echo: renderedEcho, maskEcho } = this.commandBuilder.build(command, isPasswordMode);

    if (clearInput) {
      this.clearInputAfterSend();
    } else {
      this.resetHistoryNavigation();
      this.resetCompletionState();
    }

    if (echo) {
      this.echoSentCommand(renderedEcho, maskEcho);
    }

    this.socketService.sendRaw(payload);
  }

  private pushHistory(command: string): void {
    if (this.commandHistory.at(-1) === command) {
      return;
    }

    this.commandHistory.push(command);

    if (this.commandHistory.length > TerminalInputService.MAX_HISTORY_ENTRIES) {
      this.commandHistory.shift();
    }

    this.persistHistory();
  }

  private resetHistoryNavigation(): void {
    this.historyIndex = null;
    this.historyDraft = '';
  }

  private resetCompletionState(): void {
    this.completionBaseInput.set(null);
    this.completionCycleIndex.set(0);
  }

  private readStoredHistory(): string[] {
    const raw = this.getStorage()?.getItem(TERMINAL_HISTORY_STORAGE_KEY);
    if (!raw) {
      return [];
    }

    try {
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        return [];
      }

      return parsed
        .filter((entry): entry is string => typeof entry === 'string' && entry.trim().length > 0)
        .slice(-TerminalInputService.MAX_HISTORY_ENTRIES);
    } catch {
      return [];
    }
  }

  private persistHistory(): void {
    this.getStorage()?.setItem(TERMINAL_HISTORY_STORAGE_KEY, JSON.stringify(this.commandHistory));
  }

  private getStorage(): Storage | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    return localStorage;
  }
}