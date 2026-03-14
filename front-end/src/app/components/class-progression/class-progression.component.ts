import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { NgClass } from '@angular/common';

import { GameSocketService } from '../../services/game-socket.service';
import { SkillProgressionService, SkillDto } from '../../services/skill-progression.service';

@Component({
  selector: 'app-class-progression',
  standalone: true,
  imports: [NgClass],
  templateUrl: './class-progression.component.html',
  styleUrl: './class-progression.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClassProgressionComponent {
  private readonly socketService = inject(GameSocketService);
  private readonly skillService = inject(SkillProgressionService);

  readonly playerStats = this.socketService.playerStats;
  readonly characterClass = computed(() => this.playerStats()?.characterClass ?? '');
  readonly playerLevel = computed(() => this.playerStats()?.level ?? 1);
  readonly maxLevel = computed(() => this.playerStats()?.maxLevel ?? 50);

  readonly skills = signal<SkillDto[]>([]);
  readonly loading = signal(false);

  constructor() {
    // Load skills whenever character class changes or component initializes
    effect(() => {
      const charClass = this.characterClass();
      if (charClass) {
        this.loading.set(true);
        this.skillService.getSkillsForClass(charClass).subscribe({
          next: response => {
            this.skills.set(response?.skills ?? []);
            this.loading.set(false);
          },
          error: () => {
            this.skills.set([]);
            this.loading.set(false);
          }
        });
      } else {
        this.skills.set([]);
      }
    });
  }

  readonly unlockedSkills = computed(() => {
    const level = this.playerLevel();
    return this.skills().filter(s => s.unlockLevel <= level);
  });

  readonly lockedSkills = computed(() => {
    const level = this.playerLevel();
    return this.skills().filter(s => s.unlockLevel > level);
  });

  readonly progressPercent = computed(() => {
    const total = this.skills().length;
    if (total === 0) return 0;
    return Math.round((this.unlockedSkills().length / total) * 100);
  });

  readonly selectedSkill = signal<SkillDto | null>(null);

  close(): void {
    this.selectedSkill.set(null);
    this.socketService.classProgressionOpen.set(false);
  }

  selectSkill(skill: SkillDto): void {
    this.selectedSkill.set(skill);
  }

  closeSkillModal(): void {
    this.selectedSkill.set(null);
  }

  formatClassName(name: string): string {
    if (!name) return '';
    return name.charAt(0).toUpperCase() + name.slice(1).toLowerCase();
  }

  formatBonusLabel(key: string): string {
    switch (key) {
      case 'minDamageBonus': return 'Min Damage';
      case 'maxDamageBonus': return 'Max Damage';
      case 'hitChanceBonus': return 'Hit Chance';
      case 'armorBonus': return 'Armor';
      default: return key;
    }
  }

  getBonuses(skill: SkillDto): { label: string; value: number }[] {
    if (!skill.passiveBonuses) return [];
    const bonuses: { label: string; value: number }[] = [];
    const b = skill.passiveBonuses;
    if (b.minDamageBonus) bonuses.push({ label: 'Min Damage', value: b.minDamageBonus });
    if (b.maxDamageBonus) bonuses.push({ label: 'Max Damage', value: b.maxDamageBonus });
    if (b.hitChanceBonus) bonuses.push({ label: 'Hit Chance', value: b.hitChanceBonus });
    if (b.armorBonus) bonuses.push({ label: 'Armor', value: b.armorBonus });
    return bonuses;
  }
}
