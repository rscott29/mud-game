import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, shareReplay, tap } from 'rxjs';

export interface PassiveBonusesDto {
  minDamageBonus: number;
  maxDamageBonus: number;
  hitChanceBonus: number;
  armorBonus: number;
  movementCostReduction: number;
}

export interface SkillDto {
  id: string;
  name: string;
  unlockLevel: number;
  type: string;
  passiveBonuses: PassiveBonusesDto | null;
}

export interface ClassSkillsResponse {
  characterClass: string;
  skills: SkillDto[];
}

@Injectable({ providedIn: 'root' })
export class SkillProgressionService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<ClassSkillsResponse | null>>();

  getSkillsForClass(characterClass: string): Observable<ClassSkillsResponse | null> {
    if (!characterClass) {
      return of(null);
    }

    const key = characterClass.toLowerCase();
    
    if (!this.cache.has(key)) {
      const request$ = this.http
        .get<ClassSkillsResponse>(`/api/skills/${encodeURIComponent(key)}`)
        .pipe(
          tap(response => {
            // Only cache successful responses with skills
            if (!response?.skills?.length) {
              this.cache.delete(key);
            }
          }),
          catchError(() => {
            // Don't cache failed requests
            this.cache.delete(key);
            return of(null);
          }),
          shareReplay(1)
        );
      this.cache.set(key, request$);
    }

    return this.cache.get(key)!;
  }

  clearCache(): void {
    this.cache.clear();
  }
}
