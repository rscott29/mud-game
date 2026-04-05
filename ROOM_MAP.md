# World Room Map

This is a hand-friendly map of the current `rooms.json` layout.

Legend:
- `north / south / east / west / up / down` = normal exits
- `hidden` = exit exists, but is not visible until discovered
- `quest` = hidden exit is gated by quest state

## 1. Town Core

```text
Town Square
|- north: Town Hall
|  |- east: Bank
|  `- west: Barracks
|     `- north: Gate
|        |- north: Watchtower
|        |- west: Stable
|        |  `- north: Tavern
|        |     |- north: Bakery
|        |     |- west: Homes
|        |     `- up: Tavern Loft
|        `- east: East Road
|           `- south: Abandoned House
|- south: Market
|  |- east: General Store
|  |- south: Alchemist
|  `- west: Blacksmith
|     `- west: Training Yard
|- east: Temple
|  |- north: Graveyard
|  |- east: Ruined Shrine
|  `- south: Old Memorial
`- west: Tavern
   |- north: Bakery
   |- west: Homes
   |- south: Stable
   `- up: Tavern Loft
```

## 2. Forest And Cave

```text
East Road
`- east: Forest Edge
   |- north: Deep Forest
   |  `- east: Forest Fork
   |     `- hidden east (quest_purpose): Old Oak Crossing
   |- hidden south: Cave Entrance
   |  `- south: Dark Passage 1
   |     |- east: Dark Dead End 1
   |     |- west: Dark Dead End 2
   |     `- safe route:
   |        south -> Dark Passage 2
   |        east  -> Dark Passage 3
   |        south -> Dark Passage 4
   |        west  -> Dark Passage 5
   |        south -> Cave Heart
   |           `- up: Forest Edge
   `- hidden down (after quest_courage): Cave Heart
```

### Cave Dead-End Branches

```text
Dark Passage 2
|- south: Dark Dead End 3
`- west: Dark Dead End 4

Dark Passage 3
|- north: Dark Dead End 5
`- east: Dark Dead End 6

Dark Passage 4
|- south: Dark Dead End 7
`- east: Dark Dead End 8

Dark Passage 5
`- north: Dark Dead End 9
```

## 3. Grove And Ridge

```text
Old Oak Crossing
`- north: Wayfarer's Rest
   |- north: Ruined Waystation
   |  `- east: Beacon Steps
   |     |- south: Ribbon Gorge
   |     |  |- south: Switchback Trail
   |     |  |  |- east: Scree Walk
   |     |  |  |  `- east: Shrine Approach
   |     |  |  |     `- east: Prayer Ledge
   |     |  |  `- west: Wayfarer's Rest
   |     |  `- up: Shrine Approach
   |     `- east: Collapsed Span
   |        `- south: Shrine Approach
   `- east: Switchback Trail
      |- north: Ribbon Gorge
      `- east: Scree Walk
```

## 4. Obi Route At A Glance

```text
Town Square
-> East Road
-> Forest Edge
-> Deep Forest
-> Forest Fork
-> hidden east (quest_purpose)
-> Old Oak Crossing
-> north to Wayfarer's Rest / Ruined Waystation / ridge content
```

## 5. Hidden / Quest-Gated Paths

- `Forest Edge -> south` is a hidden cave entrance.
- `Forest Edge -> down` is a hidden shortcut to `Cave Heart` and is now effectively a post-`quest_courage` reveal.
- `Forest Fork -> east` is the hidden grove path tied to `quest_purpose`.
