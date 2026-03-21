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
      this.view.messages();
      this.view.characterCreationData();
      requestAnimationFrame(() => this.scrollToBottom());
    });
  }

  @HostListener('window:beforeunload')
  onBeforeUnload(): void {
    this.facade.disconnect();

  }

  ngOnDestroy(): void {
    this.facade.disconnect();
  }

  private scrollToBottom(): void {
    const el = this.logEl?.nativeElement;
    if (!el) {
      return;
    }
    el.scrollTop = el.scrollHeight;
  }
}
