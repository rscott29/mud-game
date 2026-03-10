import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { NgClass } from '@angular/common';

export interface CharacterCreationData {
  step: 'race_class' | 'pronouns' | 'description';
  races?: string[];
  classes?: string[];
  pronounOptions?: PronounOption[];
}

export interface PronounOption {
  label: string;
  subject: string;
  object: string;
  possessive: string;
}

@Component({
  selector: 'app-character-creation',
  standalone: true,
  imports: [NgClass],
  templateUrl: './character-creation.component.html',
  styleUrl: './character-creation.component.scss'
})
export class CharacterCreationComponent {
  @Input() data!: CharacterCreationData;
  @Output() selectionComplete = new EventEmitter<string>();

  selectedRace = signal<string | null>(null);
  selectedClass = signal<string | null>(null);
  description = signal<string>('');

  selectRace(race: string): void {
    this.selectedRace.set(race);
  }

  selectClass(characterClass: string): void {
    this.selectedClass.set(characterClass);
  }

  confirmRaceClass(): void {
    if (this.selectedRace() && this.selectedClass()) {
      this.selectionComplete.emit(`${this.selectedRace()} ${this.selectedClass()}`);
    }
  }

  selectPronouns(option: PronounOption): void {
    this.selectionComplete.emit(`${option.subject}/${option.object}/${option.possessive}`);
  }

  submitCustomPronouns(value: string): void {
    if (value.trim()) {
      this.selectionComplete.emit(value.trim());
    }
  }

  submitDescription(): void {
    const desc = this.description().trim();
    this.selectionComplete.emit(desc || 'skip');
  }

  skipDescription(): void {
    this.selectionComplete.emit('skip');
  }

  updateDescription(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    this.description.set(textarea.value);
  }
}
