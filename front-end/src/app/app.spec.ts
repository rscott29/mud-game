import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import { App } from './app';
import { ConnectionStatus, GameMessage } from './models/game-message';
import { CommandCatalogService } from './services/command-catalog.service';
import { GameSocketService } from './services/game-socket.service';
import { SkillProgressionService } from './services/skill-progression.service';
import { ZoomService } from './services/zoom.service';

class MockGameSocketService {
  readonly messages$ = new Subject<GameMessage>();
  readonly systemMessages$ = new Subject<string>();
  readonly status = signal<ConnectionStatus>('connected');
  readonly playerStats = signal(null);

  connect(): void {}
  disconnect(): void {}
  sendRaw(): void {}
}

class MockSkillProgressionService {
  getSkillsForClass(characterClass: string) {
    return of({ characterClass, skills: [] });
  }
}

class MockZoomService {
  readonly zoomLevel = signal(100);

  increaseZoom(): void {}
  decreaseZoom(): void {}
}

class MockCommandCatalogService {
  load(): void {}
}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: GameSocketService, useClass: MockGameSocketService },
        { provide: SkillProgressionService, useClass: MockSkillProgressionService },
        { provide: ZoomService, useClass: MockZoomService },
        { provide: CommandCatalogService, useClass: MockCommandCatalogService },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
