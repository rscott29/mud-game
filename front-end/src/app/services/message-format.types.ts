import { type TerminalMessageClass } from '../models/game-message';

export interface FormattedMessage {
  cssClass: TerminalMessageClass;
  html: string;
}
