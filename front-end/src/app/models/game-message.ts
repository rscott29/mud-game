export interface NpcDto {
  name: string;
}

export interface RoomDto {
  name: string;
  description: string;
  exits: string[];
  items: string[];
  npcs: NpcDto[];
  players: string[];
}

export interface GameMessage {
  type: string;
  message?: string;
  from?: string;
  token?: string;
  mask?: boolean;
  room?: RoomDto;
}

export type ConnectionStatus = 'connected' | 'disconnected' | 'reconnecting';
