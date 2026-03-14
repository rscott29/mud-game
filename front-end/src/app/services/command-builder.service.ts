import { Injectable } from '@angular/core';

/**
 * Commands that can be recognized and sent directly as structured requests.
 * These are commands with no arguments, or where arguments don't semantically matter.
 */
const DIRECT_COMMANDS = new Set([
  'n', 's', 'e', 'w', 'u', 'd',
  'north', 'south', 'east', 'west', 'up', 'down',
  'go', 'move',
  'inventory', 'inv', 'i',
  'skills', 'sk', 'progression', 'abilities',
  'spawn',
  'deleteitem', 'delitem', 'deleteinv', 'destroyitem',
  'teleport', 'telport', 'teleprot', 'tp', 'warp', 'goto',
  'summon', 'call',
  'kick', 'remove', 'boot',
  'setlevel', 'setlvl', 'level',
  'help', '?',
  'logout', 'logoff', 'quit', 'exit',
  'who',
  // Social action emotes
  'wave', 'smile', 'nod', 'bow', 'wink', 'hug', 'laugh', 'cheer', 'dance', 'applaud', 'salute',
  // Custom emote
  '/em', '/emote', '/me', 'emote',
]);

/**
 * Commands that should ALWAYS be sent as natural language (input field),
 * even if recognized. This lets the AI resolver handle semantic understanding
 * of targets (items, NPCs, room features), typos, descriptions, etc.
 *
 * Examples: "look at the stone", "take shiny sword", "talk to the guard"
 */
const AI_RESOLVED_COMMANDS = new Set([
  'look', 'l', 'examine', 'x',
  'talk', 'greet',
  'take', 'get', 'pickup', 'pick', 'grab', 'snatch', 'lift', 'collect', 'steal',
  'drop', 'discard', 'toss', 'leave',
  'investigate', 'search',
]);

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

    // Commands that target items/NPCs should always use AI resolution
    if (AI_RESOLVED_COMMANDS.has(cmd)) {
      return {
        payload: JSON.stringify({ input: raw }),
        echo: raw,
        maskEcho: false,
      };
    }

    // Simple direct commands
    if (DIRECT_COMMANDS.has(cmd)) {
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
