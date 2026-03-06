import { Component } from '@angular/core';
import { TerminalComponent } from './components/terminal/terminal.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [TerminalComponent],
  template: '<app-terminal />',
  styles: [':host { display: block; }'],
})
export class App {}
