export const CHARACTER_CREATION_STEPS = {
  RACE_CLASS: 'race_class',
  PRONOUNS: 'pronouns',
  DESCRIPTION: 'description',
} as const;

export type CharacterCreationStep =
  typeof CHARACTER_CREATION_STEPS[keyof typeof CHARACTER_CREATION_STEPS];

export const CONNECTION_STATUSES = {
  CONNECTED: 'connected',
  DISCONNECTED: 'disconnected',
  RECONNECTING: 'reconnecting',
} as const;

export type ConnectionStatus =
  typeof CONNECTION_STATUSES[keyof typeof CONNECTION_STATUSES];

export const GAME_MESSAGE_TYPES = {
  WELCOME: 'WELCOME',
  ROOM_UPDATE: 'ROOM_UPDATE',
  ROOM_REFRESH: 'ROOM_REFRESH',
  ERROR: 'ERROR',
  AUTH_PROMPT: 'AUTH_PROMPT',
  CHAT_ROOM: 'CHAT_ROOM',
  CHAT_WORLD: 'CHAT_WORLD',
  CHAT_DM: 'CHAT_DM',
  WHO_LIST: 'WHO_LIST',
  SESSION_TOKEN: 'SESSION_TOKEN',
  INVENTORY_UPDATE: 'INVENTORY_UPDATE',
  HELP: 'HELP',
  CHARACTER_CREATION: 'CHARACTER_CREATION',
  STAT_UPDATE: 'STAT_UPDATE',
  CLASS_PROGRESSION: 'CLASS_PROGRESSION',
  NARRATIVE: 'NARRATIVE',
  ROOM_ACTION: 'ROOM_ACTION',
  SOCIAL_ACTION: 'SOCIAL_ACTION',
  AMBIENT_EVENT: 'AMBIENT_EVENT',
  COMPANION_DIALOGUE: 'COMPANION_DIALOGUE',
} as const;

export type KnownGameMessageType =
  typeof GAME_MESSAGE_TYPES[keyof typeof GAME_MESSAGE_TYPES];

export type GameMessageType = KnownGameMessageType | (string & {});

export const TERMINAL_MESSAGE_CLASSES = {
  ...GAME_MESSAGE_TYPES,
  SYSTEM: 'SYSTEM',
  SENT: 'SENT',
} as const;

export type KnownTerminalMessageClass =
  typeof TERMINAL_MESSAGE_CLASSES[keyof typeof TERMINAL_MESSAGE_CLASSES];

export type TerminalMessageClass = KnownTerminalMessageClass | (string & {});

export interface NpcDto {
  name: string;
  sentient: boolean;
}

export interface RoomItemDto {
  name: string;
  rarity: string;
}

export interface RoomDto {
  id?: string;
  name: string;
  description: string;
  exits: string[];
  items: RoomItemDto[];
  npcs: NpcDto[];
  players: string[];
}

export interface ItemDto {
  id: string;
  name: string;
  description: string;
  rarity: string;
  equipped: boolean;
}

export interface WhoPlayerDto {
  name: string;
  level: number;
  title: string;
  location: string;
}

export interface PlayerStatsDto {
  health: number;
  maxHealth: number;
  mana: number;
  maxMana: number;
  movement: number;
  maxMovement: number;
  level: number;
  maxLevel: number;
  xpProgress: number;
  xpForNextLevel: number;
  totalXp: number;
  isGod: boolean;
  characterClass: string;
}

export interface PronounOptionDto {
  label: string;
  subject: string;
  object: string;
  possessive: string;
}

export interface CharacterCreationDto {
  step: CharacterCreationStep;
  races?: string[];
  classes?: string[];
  pronounOptions?: PronounOptionDto[];
}

export interface GameMessage {
  type: GameMessageType;
  message?: string;
  from?: string;
  token?: string;
  mask?: boolean;
  room?: RoomDto;
  inventory?: ItemDto[];
  whoPlayers?: WhoPlayerDto[];
  playerStats?: PlayerStatsDto;
  characterCreation?: CharacterCreationDto;
}
