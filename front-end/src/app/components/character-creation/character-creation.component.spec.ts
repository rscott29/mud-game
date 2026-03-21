import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CHARACTER_CREATION_STEPS } from '../../models/game-message';
import { CharacterCreationComponent } from './character-creation.component';

describe('CharacterCreationComponent', () => {
  let component: CharacterCreationComponent;
  let fixture: ComponentFixture<CharacterCreationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CharacterCreationComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(CharacterCreationComponent);
    component = fixture.componentInstance;
    component.data = {
      step: CHARACTER_CREATION_STEPS.RACE_CLASS,
      races: ['Human', 'Elf'],
      classes: ['Warrior', 'Mage']
    };
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
