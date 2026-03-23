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
    effect(() => {
      const lastMessageClass = this.view.messages().at(-1)?.cssClass;
      this.view.characterCreationData();
      requestAnimationFrame(() => this.scrollLatestIntoView(lastMessageClass));
    });
  }

  @HostListener('window:beforeunload')
  onBeforeUnload(): void {
    this.facade.disconnect();
  }

  ngOnDestroy(): void {
    this.facade.disconnect();
  }

  private scrollLatestIntoView(lastMessageClass?: TerminalMessageClass): void {
    const el = this.logEl?.nativeElement;
    if (!el) {
      return;
    }

    if (lastMessageClass === TERMINAL_MESSAGE_CLASSES.HELP) {
      const helpCards = el.querySelectorAll<HTMLDivElement>('.msg.HELP');
      const latestHelpCard = helpCards.item(helpCards.length - 1);
      if (latestHelpCard) {
        el.scrollTop = Math.max(0, latestHelpCard.offsetTop - 12);
        return;
      }
    }

    el.scrollTop = el.scrollHeight;
  }
}
