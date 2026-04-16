import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  input,
  output,
} from '@angular/core';

import { QuickCastService, ActiveSkillDto } from '../../services/quick-cast.service';

@Component({
  selector: 'app-quick-cast',
  standalone: true,
  templateUrl: './quick-cast.component.html',
  styleUrl: './quick-cast.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuickCastComponent {
  private readonly quickCastService = inject(QuickCastService);

  /** Character class to load spells for */
  readonly characterClass = input<string>('');
  
  /** Player's current level */
  readonly level = input<number>(1);
  
  /** Current mana available */
  readonly currentMana = input<number>(0);
  
  /** Whether the player is in combat */
  readonly inCombat = input<boolean>(false);

  /** Emitted when a spell button is clicked */
  readonly castSpell = output<string>();

  /** Fetched active skills */
  readonly activeSkills = signal<ActiveSkillDto[]>([]);

  /** Track the last fetched class/level combo to avoid refetching */
  private lastFetchKey = '';

  constructor() {
    effect(() => {
      const charClass = this.characterClass();
      const lvl = this.level();
      const key = `${charClass}-${lvl}`;
      
      if (!charClass || key === this.lastFetchKey) {
        return;
      }
      
      this.lastFetchKey = key;
      this.quickCastService.getActiveSkillsForClass(charClass, lvl).subscribe(response => {
        this.activeSkills.set(response?.skills ?? []);
      });
    });
  }

  /** Visible when in combat and has active skills */
  readonly isVisible = computed(() => {
    const skills = this.activeSkills();
    return this.inCombat() && skills.length > 0;
  });

  /** Check if a skill can be cast based on current mana */
  canCast(skill: ActiveSkillDto): boolean {
    return this.currentMana() >= skill.manaCost;
  }

  /** Handle click on a spell button */
  onCastClick(skill: ActiveSkillDto): void {
    if (this.canCast(skill)) {
      this.castSpell.emit(`utter ${skill.castAlias}`);
    }
  }

  /** Track skills by id for ngFor */
  trackBySkillId(_index: number, skill: ActiveSkillDto): string {
    return skill.id;
  }
}
