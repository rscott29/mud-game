import { Injectable, inject } from '@angular/core';
import { COMMAND_DISPATCH_MODES, CommandCatalogService } from './command-catalog.service';

export interface CommandPayload {
  /** JSON string to send over the socket */
  payload: string;
  /** Original input for display echo */
  echo: string;
  /** Whether to mask the echo (for passwords) */
  maskEcho: boolean;
}

/**
 * Builds socket payloads from user input, determining whether to send
 * as structured commands or natural language for AI resolution.
 */
@Injectable({ providedIn: 'root' })
export class CommandBuilderService {
  private readonly commandCatalog = inject(CommandCatalogService);

  constructor() {
    this.commandCatalog.load();
  }

  /**
   * Build a payload from raw user input.
   * @param raw The user's input string
   * @param isPasswordMode Whether the input is a password (always sends as natural language)
   */
  build(raw: string, isPasswordMode: boolean): CommandPayload {
    if (isPasswordMode) {
      return {
        payload: JSON.stringify({ input: raw }),
        echo: raw,
        maskEcho: true,
      };
    }

    // Raw JSON passthrough
    if (raw.startsWith('{')) {
      return { payload: raw, echo: raw, maskEcho: false };
    }

    // Slash commands (chat, etc.)
    if (raw.startsWith('/')) {
      const [command, ...args] = raw.trim().split(/\s+/);
      const cmd = command.toLowerCase();
      return {
        payload: JSON.stringify(args.length ? { command: cmd, args } : { command: cmd }),
        echo: raw,
        maskEcho: false,
      };
    }

    const [first = '', ...args] = raw.split(/\s+/);
    const cmd = first.toLowerCase();
    const knownCommand = this.commandCatalog.findByAlias(cmd);

    if (knownCommand?.dispatchMode === COMMAND_DISPATCH_MODES.NATURAL_LANGUAGE) {
      return {
        payload: JSON.stringify({ input: raw }),
        echo: raw,
        maskEcho: false,
      };
    }

    if (knownCommand) {
      return {
        payload: JSON.stringify(args.length ? { command: cmd, args } : { command: cmd }),
        echo: raw,
        maskEcho: false,
      };
    }

    // Unknown command → natural language
    return {
      payload: JSON.stringify({ input: raw }),
      echo: raw,
      maskEcho: false,
    };
  }
}
