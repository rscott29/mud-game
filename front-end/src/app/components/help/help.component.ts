import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';

import { GameSocketService } from '../../services/game-socket.service';

interface HelpEntry {
  cmd: string;
  desc: string;
}

interface HelpCategory {
  title: string;
  icon: string;
  entries: HelpEntry[];
}

@Component({
  selector: 'app-help',
  standalone: true,
  imports: [],
  templateUrl: './help.component.html',
  styleUrl: './help.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HelpComponent {
  readonly socketService = inject(GameSocketService);

  private readonly baseCategories: HelpCategory[] = [
    {
      title: 'Movement',
      icon: '🧭',
      entries: [
        { cmd: 'look  /  l',          desc: 'Describe your current surroundings' },
        { cmd: 'look <target>',        desc: 'Examine an item, NPC, or type "exits"' },
        { cmd: 'examine <target>  /  x <target>', desc: 'Alias for look <target>' },
        { cmd: 'go <direction>',       desc: 'Move: north, south, east, west, up, down' },
        { cmd: 'n / s / e / w',        desc: 'Shorthand directional movement' },
        { cmd: 'u / d',                desc: 'Shorthand for up / down' },
      ],
    },
    {
      title: 'Exploration',
      icon: '🔍',
      entries: [
        { cmd: 'investigate  /  search', desc: 'Search the room for hidden exits or secrets' },
      ],
    },
    {
      title: 'Items',
      icon: '🎒',
      entries: [
        { cmd: 'take <item>  /  get <item>', desc: 'Pick up an item from the room' },
        { cmd: 'drop <item>',               desc: 'Drop an item from your inventory' },
        { cmd: 'inventory  /  inv  /  i',   desc: 'List what you are carrying' },
      ],
    },
    {
      title: 'Communication',
      icon: '💬',
      entries: [
        { cmd: 'talk <npc>',           desc: 'Talk to an NPC in the room' },
        { cmd: '/speak <message>',     desc: 'Chat with players in your room' },
        { cmd: '/world <message>',     desc: 'Broadcast to all online players' },
        { cmd: '/dm <player> <msg>',   desc: 'Send a private message to a player' },
        { cmd: 'who  /  /who',         desc: 'List currently online players' },
      ],
    },
    {
      title: 'Social & Emotes',
      icon: '🎭',
      entries: [
        { cmd: '/em <action>',         desc: 'Custom emote (e.g., /em dances)' },
        { cmd: '/em <action> <player>', desc: 'Target a player (they see "at you")' },
        { cmd: 'wave, smile, nod, bow', desc: 'Built-in social actions' },
        { cmd: 'laugh, cheer, dance',   desc: 'More social actions (with optional target)' },
      ],
    },
    {
      title: 'General',
      icon: '⚙️',
      entries: [
        { cmd: 'help  /  ?',  desc: 'Open this help panel' },
        { cmd: 'logout',      desc: 'Log out of the game (with confirmation)' },
      ],
    },
  ];

  private readonly godCategory: HelpCategory = {
    title: 'God Commands',
    icon: '👑',
    entries: [
      { cmd: 'spawn <item_id> [inv]', desc: 'Spawn an item by data id (room or inventory)' },
      { cmd: 'deleteitem <item>', desc: 'Delete an item from your inventory' },
      { cmd: 'teleport  /  tp <target>', desc: 'Teleport to a player or NPC location' },
      { cmd: 'summon <player>', desc: 'Summon a player to your location' },
      { cmd: 'kick <player>', desc: 'Kick a player from the game' },
    ],
  };

  readonly categories = computed(() =>
    this.socketService.helpIsGod()
      ? [...this.baseCategories, this.godCategory]
      : this.baseCategories
  );

  close(): void {
    this.socketService.helpOpen.set(false);
  }
}
