import { Injectable, inject } from '@angular/core';
import { Observable, catchError, concat, map, of } from 'rxjs';

import { PlayerStatsDto } from '../models/game-message';
import { FormattedMessage, MessageFormatterService } from './message-formatter.service';
import { SkillProgressionService } from './skill-progression.service';

@Injectable()
export class TerminalClassProgressionService {
  private readonly skillProgression = inject(SkillProgressionService);
  private readonly formatter = inject(MessageFormatterService);

  request(playerStats: PlayerStatsDto | null): Observable<FormattedMessage> {
    const characterClass = playerStats?.characterClass ?? '';
    if (!characterClass) {
      return of(this.formatter.formatClassProgressionError('current class'));
    }

    const loadingMessage = this.formatter.formatClassProgressionLoading(characterClass);
    const progressionMessage$ = this.skillProgression.getSkillsForClass(characterClass).pipe(
      map(response => {
        if (!response) {
          return this.formatter.formatClassProgressionError(characterClass);
        }

        return this.formatter.formatClassProgression(
          response.characterClass || characterClass,
          playerStats?.level ?? 1,
          playerStats?.maxLevel ?? 0,
          response.skills ?? []
        );
      }),
      catchError(() => of(this.formatter.formatClassProgressionError(characterClass)))
    );

    return concat(of(loadingMessage), progressionMessage$);
  }
}
