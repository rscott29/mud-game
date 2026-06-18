import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import type { MapRoomNodeDto, MapSnapshotDto } from '../../models/game-message';

interface NodeView {
  id: string;
  name: string;
  cx: number;
  cy: number;
  kind: string;
  isCurrent: boolean;
  hasUp: boolean;
  hasDown: boolean;
  glyph: string;
}

interface EdgeView {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  key: string;
}

interface MapView {
  width: number;
  height: number;
  nodes: NodeView[];
  edges: EdgeView[];
}

const CELL = 64;
const PADDING = 28;
const NODE_RADIUS = 18;

@Component({
  selector: 'app-mini-map',
  standalone: true,
  imports: [],
  templateUrl: './mini-map.component.html',
  styleUrl: './mini-map.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MiniMapComponent {
  readonly snapshot = input<MapSnapshotDto | null>(null);

  readonly view = computed<MapView | null>(() => {
    const snap = this.snapshot();
    if (!snap || !snap.rooms?.length) {
      return null;
    }
    return this.layout(snap);
  });

  readonly nodeRadius = NODE_RADIUS;

  private layout(snap: MapSnapshotDto): MapView {
    const xs = snap.rooms.map(r => r.x);
    const ys = snap.rooms.map(r => r.y);
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);

    const cols = maxX - minX + 1;
    const rows = maxY - minY + 1;

    const width = cols * CELL + PADDING * 2;
    const height = rows * CELL + PADDING * 2;

    const project = (x: number, y: number) => ({
      cx: PADDING + (x - minX) * CELL + CELL / 2,
      cy: PADDING + (y - minY) * CELL + CELL / 2,
    });

    const byId = new Map<string, MapRoomNodeDto>();
    for (const r of snap.rooms) byId.set(r.id, r);

    const nodes: NodeView[] = snap.rooms.map(r => {
      const p = project(r.x, r.y);
      return {
        id: r.id,
        name: r.name,
        cx: p.cx,
        cy: p.cy,
        kind: r.kind,
        isCurrent: r.id === snap.currentRoomId,
        hasUp: !!r.hasUp,
        hasDown: !!r.hasDown,
        glyph: this.glyphFor(r.kind),
      };
    });

    const seen = new Set<string>();
    const edges: EdgeView[] = [];
    for (const e of snap.exits) {
      if (e.dir === 'UP' || e.dir === 'DOWN') continue;
      const a = byId.get(e.fromId);
      const b = byId.get(e.toId);
      if (!a || !b) continue;
      const key = [e.fromId, e.toId].sort().join('|');
      if (seen.has(key)) continue;
      seen.add(key);
      const pa = project(a.x, a.y);
      const pb = project(b.x, b.y);
      edges.push({ x1: pa.cx, y1: pa.cy, x2: pb.cx, y2: pb.cy, key });
    }

    return { width, height, nodes, edges };
  }

private glyphFor(kind: string): string {
  switch (kind) {
    case 'shop': return '⚒';
    case 'recall': return '⌂';
    case 'dark': return '◌';
    case 'temple': return '✦';
    case 'danger': return '!';
    case 'forest': return '♣';
    case 'water': return '≈';
    default: return '•';
  }
}
}
