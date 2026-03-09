import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { GameSocketService } from '../../services/game-socket.service';
import { HelpComponent } from './help.component';

class MockGameSocketService {
  readonly helpOpen = signal(false);
  readonly helpIsGod = signal(false);
}

describe('HelpComponent', () => {
  let socket: MockGameSocketService;

  beforeEach(async () => {
    socket = new MockGameSocketService();

    await TestBed.configureTestingModule({
      imports: [HelpComponent],
      providers: [{ provide: GameSocketService, useValue: socket }],
    }).compileComponents();
  });

  it('does not show the God Commands section for non-god users', () => {
    socket.helpIsGod.set(false);

    const fixture = TestBed.createComponent(HelpComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('God Commands');
  });

  it('shows the God Commands section for god users', () => {
    socket.helpIsGod.set(true);

    const fixture = TestBed.createComponent(HelpComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('God Commands');
    expect(text).toContain('deleteitem <item>');
    expect(text).toContain('teleport  /  tp <player|npc>');
  });
});
