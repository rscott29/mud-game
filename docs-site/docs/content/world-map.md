---
sidebar_position: 2
title: World Map
---

# World map

This page turns the hand-maintained room map reference into visual diagrams.

The root `ROOM_MAP.md` file can stay as the compact text reference, but the docs page should read like a map instead of a code block.

The layouts below are arranged to preserve compass meaning first. A few local branches are slightly offset to avoid impossible overlaps in a flat diagram, but north, south, east, and west should now read naturally at a glance.

## How to read this page

- room placement carries the compass meaning, so north is above and east is right wherever the layout allows it
- dashed paths are hidden routes
- amber dashed paths are quest-gated hidden routes
- small line labels are only used when a path is `up`, `down`, or otherwise needs extra explanation
- these are schematic maps, not exact world coordinates

## Town core

<figure className="world-map-figure">
   <div className="world-map-frame">
      <svg className="world-map-svg" viewBox="0 0 1500 980" width="1500" role="img" aria-label="Compass-oriented town core map">
         <path className="world-map-line" d="M760 498 L760 382" />
         <path className="world-map-line" d="M760 542 L760 678" />
         <path className="world-map-line" d="M700 520 L372 520" />
         <path className="world-map-line" d="M820 520 L1126 520" />

         <path className="world-map-line" d="M700 360 L600 360" />
         <path className="world-map-line" d="M540 338 L540 202" />
         <path className="world-map-line" d="M540 158 L540 82" />
         <path className="world-map-line" d="M588 180 L872 180" />
         <path className="world-map-line" d="M930 202 L930 298" />
         <path className="world-map-line" d="M820 360 L960 360 L960 250 L1026 250" />

         <path className="world-map-line" d="M268 520 L142 520" />
         <path className="world-map-line" d="M320 498 L320 382" />
         <path className="world-map-line" d="M320 542 L320 598" />
         <path className="world-map-line" d="M492 180 L220 180 L220 598 L270 598" />
         <path className="world-map-line" d="M268 502 L156 378" />

         <path className="world-map-line" d="M708 700 L594 700" />
         <path className="world-map-line" d="M486 700 L140 700" />
         <path className="world-map-line" d="M812 700 L924 700" />
         <path className="world-map-line" d="M760 722 L760 802" />

         <path className="world-map-line" d="M1180 498 L1180 382" />
         <path className="world-map-line" d="M1180 542 L1180 678" />
         <path className="world-map-line" d="M1234 520 L1314 520" />

         <g transform="translate(760 520)">
            <rect className="world-map-room world-map-room--major" x="-60" y="-22" width="120" height="44" rx="12" />
            <text className="world-map-text" y="5">Town Square</text>
         </g>

         <g transform="translate(760 360)">
            <rect className="world-map-room world-map-room--major" x="-60" y="-22" width="120" height="44" rx="12" />
            <text className="world-map-text" y="5">Town Hall</text>
         </g>

         <g transform="translate(760 700)">
            <rect className="world-map-room world-map-room--major" x="-52" y="-22" width="104" height="44" rx="12" />
            <text className="world-map-text" y="5">Market</text>
         </g>

         <g transform="translate(320 520)">
            <rect className="world-map-room world-map-room--major" x="-52" y="-22" width="104" height="44" rx="12" />
            <text className="world-map-text" y="5">Tavern</text>
         </g>

         <g transform="translate(1180 520)">
            <rect className="world-map-room world-map-room--major" x="-54" y="-22" width="108" height="44" rx="12" />
            <text className="world-map-text" y="5">Temple</text>
         </g>

         <g transform="translate(540 360)">
            <rect className="world-map-room" x="-60" y="-22" width="120" height="44" rx="12" />
            <text className="world-map-text" y="5">Barracks</text>
         </g>

         <g transform="translate(540 180)">
            <rect className="world-map-room" x="-48" y="-22" width="96" height="44" rx="12" />
            <text className="world-map-text" y="5">Gate</text>
         </g>

         <g transform="translate(540 70)">
            <rect className="world-map-room" x="-68" y="-12" width="136" height="30" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Watchtower</text>
         </g>

         <g transform="translate(930 180)">
            <rect className="world-map-room" x="-58" y="-22" width="116" height="44" rx="12" />
            <text className="world-map-text" y="5">East Road</text>
         </g>

         <g transform="translate(930 320)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="0">
               <tspan x="0" dy="-0.1em">Abandoned</tspan>
               <tspan x="0" dy="1.15em">House</tspan>
            </text>
         </g>

         <g transform="translate(320 360)">
            <rect className="world-map-room" x="-52" y="-22" width="104" height="44" rx="12" />
            <text className="world-map-text" y="5">Bakery</text>
         </g>

         <g transform="translate(100 520)">
            <rect className="world-map-room" x="-42" y="-22" width="84" height="44" rx="12" />
            <text className="world-map-text" y="5">Homes</text>
         </g>

         <g transform="translate(320 620)">
            <rect className="world-map-room" x="-50" y="-22" width="100" height="44" rx="12" />
            <text className="world-map-text" y="5">Stable</text>
         </g>

         <g transform="translate(100 360)">
            <rect className="world-map-room world-map-room--elevated" x="-56" y="-18" width="112" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Tavern Loft</text>
         </g>
         <g transform="translate(210 432)">
            <rect className="world-map-label-chip" x="-18" y="-13" width="36" height="26" rx="9" />
            <text className="world-map-text world-map-text--small" y="4">up</text>
         </g>

         <g transform="translate(540 700)">
            <rect className="world-map-room" x="-54" y="-22" width="108" height="44" rx="12" />
            <text className="world-map-text" y="5">Blacksmith</text>
         </g>

         <g transform="translate(100 700)">
            <rect className="world-map-room" x="-40" y="-22" width="80" height="44" rx="12" />
            <text className="world-map-text world-map-text--small" y="5">Training Yard</text>
         </g>

         <g transform="translate(980 700)">
            <rect className="world-map-room" x="-56" y="-22" width="112" height="44" rx="12" />
            <text className="world-map-text world-map-text--small" y="5">General Store</text>
         </g>

         <g transform="translate(1180 360)">
            <rect className="world-map-room" x="-56" y="-22" width="112" height="44" rx="12" />
            <text className="world-map-text" y="5">Graveyard</text>
         </g>

         <g transform="translate(1180 700)">
            <rect className="world-map-room" x="-66" y="-22" width="132" height="44" rx="12" />
            <text className="world-map-text world-map-text--small" y="5">Old Memorial</text>
         </g>

         <g transform="translate(1380 520)">
            <rect className="world-map-room" x="-66" y="-22" width="132" height="44" rx="12" />
            <text className="world-map-text world-map-text--small" y="5">Ruined Shrine</text>
         </g>

         <g transform="translate(1070 250)">
            <rect className="world-map-room" x="-44" y="-18" width="88" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Bank</text>
         </g>

         <g transform="translate(760 820)">
            <rect className="world-map-room" x="-54" y="-18" width="108" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Alchemist</text>
         </g>
      </svg>
   </div>
</figure>

<p className="world-map-key">The town map now uses explicit elbow routes for the awkward gate and stable branch instead of forcing those rooms into collisions. As the town grows, extending the canvas is safer than compressing the grid.</p>

## Forest and cave

<figure className="world-map-figure">
   <div className="world-map-frame">
      <svg className="world-map-svg" viewBox="0 0 900 720" width="980" role="img" aria-label="Compass-oriented forest and cave map">
         <path className="world-map-line" d="M120 250 L240 250" />
         <path className="world-map-line" d="M300 228 L300 130" />
         <path className="world-map-line" d="M360 108 L470 108" />
         <path className="world-map-line world-map-line--quest" d="M534 108 L650 108" />

         <path className="world-map-line world-map-line--hidden" d="M300 272 L300 360" />
         <path className="world-map-line" d="M360 382 L470 382" />
         <path className="world-map-line" d="M530 404 L530 478" />
         <path className="world-map-line" d="M470 500 L360 500" />
         <path className="world-map-line" d="M300 522 L300 600" />
         <path className="world-map-line world-map-line--hidden" d="M258 270 C180 330, 180 560, 258 620" />

         <g transform="translate(60 250)">
            <rect className="world-map-room" x="-58" y="-22" width="116" height="44" rx="12" />
            <text className="world-map-text" y="5">East Road</text>
         </g>
         <g transform="translate(300 250)">
            <rect className="world-map-room world-map-room--major" x="-62" y="-22" width="124" height="44" rx="12" />
            <text className="world-map-text" y="5">Forest Edge</text>
         </g>
         <g transform="translate(300 108)">
            <rect className="world-map-room" x="-62" y="-22" width="124" height="44" rx="12" />
            <text className="world-map-text" y="5">Deep Forest</text>
         </g>
         <g transform="translate(530 108)">
            <rect className="world-map-room" x="-60" y="-22" width="120" height="44" rx="12" />
            <text className="world-map-text" y="5">Forest Fork</text>
         </g>
         <g transform="translate(710 108)">
            <rect className="world-map-room world-map-room--major" x="-78" y="-22" width="156" height="44" rx="12" />
            <text className="world-map-text" y="5">Old Oak Crossing</text>
         </g>

         <g transform="translate(300 382)">
            <rect className="world-map-room" x="-66" y="-22" width="132" height="44" rx="12" />
            <text className="world-map-text" y="5">Cave Entrance</text>
         </g>
         <g transform="translate(530 382)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 1</text>
         </g>
         <g transform="translate(530 500)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 2</text>
         </g>
         <g transform="translate(710 382)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 3</text>
         </g>
         <g transform="translate(710 500)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 4</text>
         </g>
         <g transform="translate(530 620)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 5</text>
         </g>
         <g transform="translate(300 620)">
            <rect className="world-map-room world-map-room--major" x="-56" y="-22" width="112" height="44" rx="12" />
            <text className="world-map-text" y="5">Cave Heart</text>
         </g>

         <g transform="translate(620 88)">
            <rect className="world-map-label-chip" x="-70" y="-14" width="140" height="28" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">hidden east, quest_purpose</text>
         </g>
         <g transform="translate(248 324)">
            <rect className="world-map-label-chip" x="-40" y="-14" width="80" height="28" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">hidden south</text>
         </g>
         <g transform="translate(190 456)">
            <rect className="world-map-label-chip" x="-78" y="-14" width="156" height="28" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">hidden down after quest_courage</text>
         </g>
         <g transform="translate(230 560)">
            <rect className="world-map-label-chip" x="-16" y="-14" width="32" height="28" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">up</text>
         </g>
      </svg>
   </div>
</figure>

### Cave dead-end branches

<figure className="world-map-figure">
   <div className="world-map-frame">
      <svg className="world-map-svg" viewBox="0 0 900 360" width="980" role="img" aria-label="Cave dead-end branches map">
         <path className="world-map-line" d="M450 70 L580 70" />
         <path className="world-map-line" d="M350 70 L220 70" />
         <path className="world-map-line" d="M450 100 L450 155" />
         <path className="world-map-line" d="M390 178 L270 178" />
         <path className="world-map-line" d="M510 178 L630 178" />
         <path className="world-map-line" d="M450 202 L450 256" />
         <path className="world-map-line" d="M390 280 L270 280" />
         <path className="world-map-line" d="M510 280 L630 280" />
         <path className="world-map-line" d="M450 304 L450 340" />

         <g transform="translate(450 70)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 1</text>
         </g>
         <g transform="translate(180 70)">
            <rect className="world-map-room" x="-74" y="-22" width="148" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Dead End 2</text>
         </g>
         <g transform="translate(620 70)">
            <rect className="world-map-room" x="-74" y="-22" width="148" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Dead End 1</text>
         </g>

         <g transform="translate(450 178)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 2</text>
         </g>
         <g transform="translate(230 178)">
            <rect className="world-map-room" x="-74" y="-22" width="148" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Dead End 4</text>
         </g>
         <g transform="translate(670 178)">
            <rect className="world-map-room" x="-74" y="-22" width="148" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 3</text>
         </g>

         <g transform="translate(450 280)">
            <rect className="world-map-room" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Passage 4</text>
         </g>
         <g transform="translate(230 280)">
            <rect className="world-map-room" x="-74" y="-22" width="148" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Dead End 7</text>
         </g>
         <g transform="translate(670 280)">
            <rect className="world-map-room" x="-74" y="-22" width="148" height="44" rx="12" />
            <text className="world-map-text" y="5">Dark Dead End 8</text>
         </g>

         <g transform="translate(450 344)">
            <rect className="world-map-room" x="-72" y="-18" width="144" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Dark Passage 5 / north to Dead End 9</text>
         </g>

         <g transform="translate(560 156)">
            <rect className="world-map-label-chip" x="-18" y="-14" width="36" height="28" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">east</text>
         </g>
         <g transform="translate(130 156)">
            <rect className="world-map-label-chip" x="-18" y="-14" width="36" height="28" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">west</text>
         </g>
      </svg>
   </div>
</figure>

## Grove and ridge

<figure className="world-map-figure">
   <div className="world-map-frame">
      <svg className="world-map-svg" viewBox="0 0 900 520" width="1040" role="img" aria-label="Compass-oriented grove and ridge map">
         <path className="world-map-line" d="M130 360 L130 272" />
         <path className="world-map-line" d="M130 208 L130 120" />
         <path className="world-map-line" d="M190 230 L330 230" />
         <path className="world-map-line" d="M390 206 L390 120" />
         <path className="world-map-line" d="M450 98 L560 98" />
         <path className="world-map-line" d="M390 254 L390 338" />
         <path className="world-map-line" d="M450 360 L560 360" />
         <path className="world-map-line" d="M620 122 L620 206" />
         <path className="world-map-line" d="M620 230 L730 230" />
         <path className="world-map-line" d="M620 338 L620 254" />

         <g transform="translate(130 404)">
            <rect className="world-map-room world-map-room--major" x="-78" y="-22" width="156" height="44" rx="12" />
            <text className="world-map-text" y="5">Old Oak Crossing</text>
         </g>
         <g transform="translate(130 230)">
            <rect className="world-map-room world-map-room--major" x="-74" y="-22" width="148" height="44" rx="12" />
            <text className="world-map-text" y="5">Wayfarer's Rest</text>
         </g>
         <g transform="translate(130 98)">
            <rect className="world-map-room" x="-78" y="-22" width="156" height="44" rx="12" />
            <text className="world-map-text" y="5">Ruined Waystation</text>
         </g>
         <g transform="translate(390 230)">
            <rect className="world-map-room" x="-74" y="-22" width="148" height="44" rx="12" />
            <text className="world-map-text" y="5">Switchback Trail</text>
         </g>
         <g transform="translate(390 98)">
            <rect className="world-map-room" x="-68" y="-22" width="136" height="44" rx="12" />
            <text className="world-map-text" y="5">Ribbon Gorge</text>
         </g>
         <g transform="translate(620 98)">
            <rect className="world-map-room" x="-64" y="-22" width="128" height="44" rx="12" />
            <text className="world-map-text" y="5">Scree Walk</text>
         </g>
         <g transform="translate(620 230)">
            <rect className="world-map-room world-map-room--elevated" x="-72" y="-22" width="144" height="44" rx="12" />
            <text className="world-map-text" y="5">Shrine Approach</text>
         </g>
         <g transform="translate(780 230)">
            <rect className="world-map-room world-map-room--elevated" x="-66" y="-22" width="132" height="44" rx="12" />
            <text className="world-map-text" y="5">Prayer Ledge</text>
         </g>
         <g transform="translate(620 360)">
            <rect className="world-map-room" x="-70" y="-22" width="140" height="44" rx="12" />
            <text className="world-map-text" y="5">Collapsed Span</text>
         </g>
         <g transform="translate(390 360)">
            <rect className="world-map-room" x="-66" y="-22" width="132" height="44" rx="12" />
            <text className="world-map-text" y="5">Beacon Steps</text>
         </g>

         <g transform="translate(470 174)">
            <rect className="world-map-label-chip" x="-16" y="-14" width="32" height="28" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">up</text>
         </g>
         <path className="world-map-line" d="M448 184 L560 242" />
      </svg>
   </div>
</figure>

## Obi route at a glance

<figure className="world-map-figure">
   <div className="world-map-frame">
      <svg className="world-map-svg" viewBox="0 0 900 220" width="920" role="img" aria-label="Compass-oriented Obi route overview">
         <path className="world-map-line" d="M92 110 L210 110 L210 70 L330 70 L450 70" />
         <path className="world-map-line world-map-line--quest" d="M510 70 L640 70" />
         <path className="world-map-line" d="M700 92 L700 150" />

         <g transform="translate(40 110)">
            <rect className="world-map-room world-map-room--major" x="-38" y="-18" width="76" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Town Square</text>
         </g>
         <g transform="translate(270 110)">
            <rect className="world-map-room" x="-36" y="-18" width="72" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">East Road</text>
         </g>
         <g transform="translate(390 70)">
            <rect className="world-map-room" x="-42" y="-18" width="84" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Forest Edge</text>
         </g>
         <g transform="translate(510 70)">
            <rect className="world-map-room" x="-42" y="-18" width="84" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Forest Fork</text>
         </g>
         <g transform="translate(700 70)">
            <rect className="world-map-room world-map-room--major" x="-58" y="-18" width="116" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Old Oak Crossing</text>
         </g>
         <g transform="translate(700 170)">
            <rect className="world-map-room world-map-room--major" x="-72" y="-18" width="144" height="36" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">Wayfarer's Rest / ridge</text>
         </g>
         <g transform="translate(606 46)">
            <rect className="world-map-label-chip" x="-48" y="-13" width="96" height="26" rx="10" />
            <text className="world-map-text world-map-text--small" y="4">hidden east, quest</text>
         </g>
      </svg>
   </div>
</figure>

## Hidden or quest-gated paths

- `Forest Edge -> south` is a hidden cave entrance.
- `Forest Edge -> down` is a hidden shortcut to `Cave Heart` and is currently a post-`quest_courage` reveal.
- `Forest Fork -> east` is the hidden grove path tied to `quest_purpose`.

Keep this page aligned with the root `ROOM_MAP.md` file when the world layout changes.