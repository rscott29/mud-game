export interface NpcDto {
  name: string;
  sentient: boolean;
}

export interface RoomItemDto {
  name: string;
  rarity: string;
}

export interface RoomDto {
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
  step: 'race_class' | 'pronouns' | 'description';
  races?: string[];
  classes?: string[];
  pronounOptions?: PronounOptionDto[];
}

export interface GameMessage {
  type: string;
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

export type ConnectionStatus = 'connected' | 'disconnected' | 'reconnecting';
