import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { SafeHtmlPipe } from '../../pipes/safe-html.pipe';

import { GameSocketService } from '../../services/game-socket.service';
import { ItemDto } from '../../models/game-message';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [NgClass, SafeHtmlPipe],
  templateUrl: './inventory.component.html',
  styleUrl: './inventory.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryComponent {
  private readonly socketService = inject(GameSocketService);

  readonly items = this.socketService.inventory;

  readonly itemCount = computed(() => this.items().length);
  readonly isEmpty   = computed(() => this.items().length === 0);

  readonly selectedItem = signal<ItemDto | null>(null);

  openModal(item: ItemDto): void {
    this.selectedItem.set(item);
  }

  closeModal(): void {
    this.selectedItem.set(null);
  }

  close(): void {
    this.selectedItem.set(null);
    this.socketService.inventoryOpen.set(false);
  }

  rarityLabel(rarity: string): string {
    return rarity.charAt(0).toUpperCase() + rarity.slice(1).toLowerCase();
  }

  rarityIcon(rarity: string): string {
    switch (rarity?.toLowerCase()) {
      case 'fine':      return '✦';
      case 'rare':      return '✦✦';
      case 'legendary': return '✦✦✦';
      default:          return '✧';
    }
  }
}
