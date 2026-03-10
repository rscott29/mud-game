import { ComponentFixture, TestBed } from '@angular/core/testing';
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
      step: 'race_class',
      races: ['Human', 'Elf'],
      classes: ['Warrior', 'Mage']
    };
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
