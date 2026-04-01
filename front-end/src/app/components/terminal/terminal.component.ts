import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  ViewChild,
  effect,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';

import { TERMINAL_MESSAGE_CLASSES, type TerminalMessageClass } from '../../models/game-message';
import { SafeHtmlPipe } from '../../pipes/safe-html.pipe';
import { CharacterCreationComponent } from '../character-creation/character-creation.component';
import { TerminalFacade } from '../../services/terminal-facade.service';
import { TerminalInputService } from '../../services/terminal-input.service';
import { TerminalPresenterService } from '../../services/terminal-presenter.service';
import { TerminalMessageStore } from '../../services/terminal-message-store.service';
import { TerminalMessageInterpreterService } from '../../services/terminal-message-interpreter.service';
import { TerminalClassProgressionService } from '../../services/terminal-class-progression.service';

@Component({
  selector: 'app-terminal',
  standalone: true,
  imports: [FormsModule, SafeHtmlPipe, NgClass, CharacterCreationComponent],
  templateUrl: './terminal.component.html',
  styleUrl: './terminal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    TerminalMessageStore,
    TerminalMessageInterpreterService,
    TerminalClassProgressionService,
    TerminalInputService,
    TerminalPresenterService,
    TerminalFacade,
  ],
})
export class TerminalComponent {
  @ViewChild('logEl') private logEl?: ElementRef<HTMLDivElement>;

  private readonly facade = inject(TerminalFacade);
  readonly input = inject(TerminalInputService);
  readonly view = inject(TerminalPresenterService);

  constructor() {
    this.initializeAutoScroll();
  }

  @HostListener('window:beforeunload')
  onBeforeUnload(): void {
    this.disconnectTerminal();
  }

  ngOnDestroy(): void {
    this.disconnectTerminal();
  }

  acceptSuggestion(event: Event): void {
    if (this.input.acceptNextCompletion()) {
      event.preventDefault();
    }
  }

  handleLogClick(event: MouseEvent): void {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }

    const commandElement = target.closest<HTMLElement>('[data-command], button[value], a[title]');
    const command = commandElement?.getAttribute('data-command')?.trim()
      ?? commandElement?.getAttribute('value')?.trim()
      ?? commandElement?.getAttribute('title')?.trim();
    if (!command) {
      return;
    }

    event.preventDefault();
    this.input.sendCommand(command, true);
  }

  private initializeAutoScroll(): void {
    effect(() => {
      const lastMessageClass = this.view.messages().at(-1)?.cssClass;
      this.view.characterCreationData();
      requestAnimationFrame(() => this.scrollLatestIntoView(lastMessageClass));
    });
  }

  private disconnectTerminal(): void {
    this.facade.disconnect();
  }

  private scrollLatestIntoView(lastMessageClass?: TerminalMessageClass): void {
    const logElement = this.logEl?.nativeElement;
    if (!logElement) {
      return;
    }

    if (this.scrollHelpCardIntoView(logElement, lastMessageClass)) {
      return;
    }

    this.scrollLogToBottom(logElement);
  }

  private scrollHelpCardIntoView(
    logElement: HTMLDivElement,
    lastMessageClass?: TerminalMessageClass
  ): boolean {
    if (lastMessageClass !== TERMINAL_MESSAGE_CLASSES.HELP) {
      return false;
    }

    const helpCards = logElement.querySelectorAll<HTMLDivElement>('.msg.HELP');
    const latestHelpCard = helpCards.item(helpCards.length - 1);
    if (!latestHelpCard) {
      return false;
    }

    logElement.scrollTop = Math.max(0, latestHelpCard.offsetTop - 12);
    return true;
  }

  private scrollLogToBottom(logElement: HTMLDivElement): void {
    logElement.scrollTop = logElement.scrollHeight;
  }
}
