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
}

export type ConnectionStatus = 'connected' | 'disconnected' | 'reconnecting';
