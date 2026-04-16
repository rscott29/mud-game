import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, shareReplay, tap } from 'rxjs';

export interface ActiveSkillDto {
  id: string;
  name: string;
  unlockLevel: number;
  manaCost: number;
  castAlias: string;
}

export interface ActiveSkillsResponse {
  characterClass: string;
  skills: ActiveSkillDto[];
}

@Injectable({ providedIn: 'root' })
export class QuickCastService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<ActiveSkillsResponse | null>>();

  getActiveSkillsForClass(characterClass: string, level: number): Observable<ActiveSkillsResponse | null> {
    if (!characterClass) {
      return of(null);
    }

    const key = `${characterClass.toLowerCase()}-${level}`;
    
    if (!this.cache.has(key)) {
      const request$ = this.http
        .get<ActiveSkillsResponse>(
          `/api/skills/${encodeURIComponent(characterClass.toLowerCase())}/active`,
          { params: { level: level.toString() } }
        )
        .pipe(
          tap(response => {
            if (!response?.skills?.length) {
              this.cache.delete(key);
            }
          }),
          catchError(() => {
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
