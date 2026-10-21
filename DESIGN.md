---
name: Artemisa
description: Art-led, controller-first game streaming dashboard for Android TV.
colors:
  go-green: "#76D600"
  on-go: "#0A1200"
  stop-coral: "#FF6B6E"
  ink: "#0B0D10"
  surface-1: "#14171C"
  surface-2: "#1C2027"
  surface-3: "#272C34"
  surface-3-focused: "#323842"
  text: "#FFFFFF"
  text-2: "rgba(255, 255, 255, 0.74)"
  text-3: "rgba(255, 255, 255, 0.52)"
  line: "rgba(255, 255, 255, 0.14)"
  glass: "rgba(255, 255, 255, 0.10)"
  glass-strong: "rgba(255, 255, 255, 0.16)"
  hint-badge: "rgba(255, 255, 255, 0.90)"
typography:
  display:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "52sp"
    fontWeight: 800
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "44sp"
    fontWeight: 800
    lineHeight: 0.95
    letterSpacing: "-0.02em"
  title-lg:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "26sp"
    fontWeight: 800
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "22sp"
    fontWeight: 800
  button:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "16sp"
    fontWeight: 700
  body-strong:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "15sp"
    fontWeight: 600
  meta:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    fontFeature: "tnum"
  card-title:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "13sp"
    fontWeight: 700
  label:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "12sp"
    fontWeight: 600
  body:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "12sp"
    fontWeight: 400
  caption:
    fontFamily: "Albert Sans, sans-serif"
    fontSize: "11sp"
    fontWeight: 500
rounded:
  hairline: "2dp"
  sm: "8dp"
  row: "9dp"
  card: "11dp"
  md: "12dp"
  ring: "16dp"
  sheet: "18dp"
  pill: "20dp"
  toast: "24dp"
  full: "40dp"
spacing:
  focus-gap: "6dp"
  xs: "8dp"
  sm: "10dp"
  md: "12dp"
  card-inset: "13dp"
  lg: "16dp"
  xl: "24dp"
  safe-y: "27dp"
  safe-x: "48dp"
components:
  button-primary:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    height: "56dp"
    padding: "0 30dp 0 25dp"
  button-circle:
    backgroundColor: "{colors.glass-strong}"
    textColor: "{colors.text}"
    rounded: "{rounded.full}"
    size: "56dp"
  button-icon-small:
    backgroundColor: "{colors.glass}"
    textColor: "{colors.text}"
    rounded: "{rounded.full}"
    size: "30dp"
  button-dialog:
    backgroundColor: "{colors.glass}"
    textColor: "{colors.text}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    height: "48dp"
    padding: "0 20dp"
  button-dialog-focused:
    backgroundColor: "{colors.go-green}"
    textColor: "{colors.on-go}"
  host-pill:
    backgroundColor: "{colors.glass}"
    textColor: "{colors.text}"
    rounded: "{rounded.pill}"
    height: "28dp"
    padding: "0 12dp 0 10dp"
  tile:
    backgroundColor: "{colors.surface-3}"
    rounded: "10dp"
    width: "168dp"
    height: "224dp"
  quick-card:
    backgroundColor: "{colors.surface-3}"
    textColor: "{colors.text}"
    typography: "{typography.card-title}"
    rounded: "{rounded.card}"
    height: "98dp"
    padding: "{spacing.card-inset}"
  quick-card-focused:
    backgroundColor: "{colors.surface-3-focused}"
  menu-row:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
  menu-row-focused:
    backgroundColor: "{colors.go-green}"
    textColor: "{colors.on-go}"
  menu-row-danger-focused:
    backgroundColor: "{colors.stop-coral}"
    textColor: "{colors.ink}"
  key:
    backgroundColor: "{colors.surface-3}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
  key-focused:
    backgroundColor: "{colors.go-green}"
    textColor: "{colors.on-go}"
  preference-row:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    typography: "{typography.body-strong}"
    rounded: "{rounded.row}"
  preference-row-focused:
    backgroundColor: "{colors.surface-3}"
  settings-tab:
    backgroundColor: "transparent"
    textColor: "{colors.text-3}"
    rounded: "17dp"
    height: "34dp"
    padding: "0 12dp"
  settings-tab-selected:
    textColor: "{colors.text}"
  settings-guide:
    backgroundColor: "{colors.surface-2}"
    rounded: "{rounded.ring}"
    width: "300dp"
  sheet:
    backgroundColor: "{colors.surface-1}"
    rounded: "{rounded.sheet}"
  panel:
    backgroundColor: "{colors.surface-2}"
    rounded: "{rounded.ring}"
  toast:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.text}"
    rounded: "{rounded.toast}"
    padding: "12dp 22dp"
  hint-badge:
    backgroundColor: "{colors.hint-badge}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    size: "20dp"
---

# Design System: Artemisa

## Overview

**Creative North Star: "The Spotlight"**

The focused game stands in the middle of the screen. Its cover sits full size in the centre of a carousel while its neighbours shrink and fade towards the edges, and its art is the room: a soft, blurred, slightly oversaturated backdrop fills the display, pulled back to ink at the corners by a vignette. Under the cover, centred: a big ExtraBold title, a meta line, and a white Play pill. Home uses the same carousel for computers. Everything else (options, settings, the in-stream quick menu) comes up as dark surfaces and sheets layered over that art or over the live stream.

The system is built for a controller at ten feet on a Shield TV (960x540dp). Focus is the only cursor, so focus has to be unmistakable: a green ring standing off the element with a dark gap, or a solid green fill on rows and keys. Green is kept for that job and almost nothing else, which is what makes it readable at a glance. The primary action is white, not green, and destructive actions are coral.

Depth comes from real Android elevation (soft, ambient shadows), tonal steps from ink to surface-3, and translucent white "glass" for chrome over art. There are no glows, no borders on cards, and no decorative gradients beyond the legibility scrims.

**Key Characteristics:**
- Full-bleed, focus-driven art: the backdrop cross-fades to whatever is focused.
- One centred carousel; the focused cover is 168x224dp, neighbours scale to 75%, 62.5% and then out.
- Green (#76D600) is focus and selection, never decoration or the primary button.
- White pill for the primary action; glass circles and pills for secondary chrome.
- Albert Sans throughout: ExtraBold titles, SemiBold labels, tabular figures for numbers.
- Rounded forms from 8dp rows up to fully round pills; soft elevation, no glows.

## Colors

A near-black blue-ink stage with three stepped surfaces, white type at three opacities, and two signal colors: green for focus, coral for stop.

### Primary
- **Go Green** (go-green): The binding brand green, used only as the focus ring, the selected/focused fill of menu rows, keys and dialog buttons, the "on" track of switches, the live dot in the quick-menu header, and the fill of primary progress bars. Text and icons sitting on it use **Go Ink** (on-go), a near-black green.

### Secondary
- **Stop Coral** (stop-coral): Destructive actions only (End stream, Delete, Unpair). Coral text at rest; a solid coral fill with ink text when focused.

### Neutral
- **Ink** (ink): The screen background and the base under every scrim. Also the text colour on white pills and hint badges.
- **Surface 1** (surface-1): Sheets rising from the bottom (quick menu) and the settings shell.
- **Surface 2** (surface-2): Dialog panels and toasts.
- **Surface 3** (surface-3): Resting cards, keys, PIN digits and tile placeholders; also the focused preference row.
- **Surface 3 Focused** (surface-3-focused): A card or activated key under focus, lifted one step above surface-3.
- **White, 74% White, 52% White** (text, text-2, text-3): Primary copy; meta, summaries and hint labels; category headers and tertiary notes.
- **Glass and Strong Glass** (glass, glass-strong): Translucent white fills for chrome that sits over art: host pill, small icon buttons, the round "more" button, dialog buttons at rest, a focused settings tab.
- **Line** (line): Menu separators and dividers.
- **Hint White** (hint-badge): The discs and pills that carry controller button letters.

### Named Rules
**The Green Means Focus Rule.** Green marks where the controller is or what is selected: focus rings, focused/selected fills, switch-on tracks, the live dot, primary progress. It is never a resting background, a decorative accent, or the primary button.

**The White Play Rule.** The primary action is a white pill with ink text. Focus adds the green ring around it; it does not turn green.

**The Coral Stop Rule.** Destructive actions are coral and nothing else is. When a destructive action asks for confirmation, focus lands on the safe option.

## Typography

**Display Font:** Albert Sans (OFL, bundled 400/500/600/700/800)
**Body Font:** Albert Sans
**Label/Mono Font:** Albert Sans with tabular figures (`tnum`) for meta lines, clocks and numbers

**Character:** One geometric-grotesque family carries everything. Weight does the hierarchy work: ExtraBold with slightly tight tracking (-0.02) for titles, Bold for buttons and card titles, SemiBold for labels and nav, Medium for meta, Regular for summaries.

### Hierarchy
- **Headline** (800, 32sp, one line, centred): The hero title under the carousel (game on Library, host name on Home).
- **Title Large** (800, 26 to 36sp): Screen titles (Settings, pairing), launch overlay, empty/loading state.
- **Title** (800, 22sp): Dialog titles.
- **Button** (700, 15 to 16sp): Play pill and dialog buttons. Sentence case, never all caps.
- **Body Strong** (600, 15sp): Preference row titles.
- **Meta** (500, 14sp, tnum, 74% white): The line under a hero title ("4K · 60 FPS · HDR · 80 Mbps"), clocks.
- **Card Title** (700, 13sp) over **Caption** (500, 11sp): Quick-menu and option cards (name, then current state).
- **Label** (600, 12sp): Controller hint labels, keyboard segments, settings chrome.
- **Body** (400, 12sp, 74% white): Preference summaries, which show the current value for list and slider preferences.

### Named Rules
**The Weight Not Case Rule.** Hierarchy comes from weight and size. Labels are sentence case; the system has no all-caps labels or small tracked-out lines above titles.

**The Tabular Numbers Rule.** Anything that updates or compares numbers (meta line, clock, performance figures) uses tabular figures.

## Layout

The design canvas is 960x540dp (Shield TV at xhdpi). Home and Library are full-bleed: art and scrims run to every edge with no root padding, while all content sits inside the title-safe frame of 48dp horizontal and 27dp vertical (5% of the screen).

Library, top to bottom: a 32dp top bar at the safe edge (app icon, host pill with monitor icon, then settings button, avatar and clock at right); the 240dp carousel band starting 78dp from the top; position dots at 326dp (hidden past 15 games); the centred hero block from 342dp, 760dp wide (title, meta, Play pill plus round more button); controller hints bottom-right on the safe line. Home follows the same frame with 168dp square computer tiles and an "Add computer" tile at the end, and opens straight into the library when exactly one paired computer is online.

The carousel lays every item out in the centre and spreads them with translation from the focused one, so the focused item is always centred. Each item sits in a focus frame padded by the 6dp focus gap; nearer items draw over farther ones. Left and right move focus one item at a time; only the current item is reachable from above and below.

Menus anchor to what opened them: option cards appear beside their tile. The in-stream quick menu is a bottom sheet with a header (live dot, game, source, clock) and one row of equal cards; long submenus become a 4-column compact grid. Settings has section tabs across the top (LB/RB, or the D-pad), the section's rows on the left (530dp) and a 300dp guide card on the right that explains the focused row; a list setting's choices open inside that card instead of a dialog.

Spacing runs on small steps (8, 10, 12, 16, 24dp) with card insets of 13dp; the focus gap (6dp) is the one spacing value every focusable ring respects.

## Elevation & Depth

A hybrid: tonal layering does most of the work (ink, surface-1, surface-2, surface-3, surface-3-focused, plus translucent glass over art), and native Android elevation adds soft ambient shadows where something should read as lifted. Library tiles rest at 6dp elevation; a focused card scales to 1.04 and rises 8dp in translationZ; anchored option menus sit at 16dp. Legibility over art comes from ink gradients, not from boxes: a radial vignette centred just above the middle (clear, 35% ink halfway out, 88% at the corners) and a 120dp top shade (50% to clear). The backdrop itself is a small blurred copy of the art scaled up, at 0.5 to 0.75 brightness with 1.3 saturation.

### Shadow Vocabulary
- **Tile rest** (elevation 6dp): Library tiles on the art.
- **Card lift** (translationZ 8dp on focus, 140ms decelerate): Quick-menu and option cards.
- **Anchored menu** (elevation 16dp): Option menus opened beside an element.

### Named Rules
**The No Glow Rule.** Shadows are soft, neutral Android elevation. Focus is shown with the ring or fill, never with a coloured glow or outer bloom.

**The Shade Not Box Rule.** Text over art is made legible with ink gradients, not by putting it on a panel.

## Shapes

Everything is rounded, and radius grows with the size of the object: 2dp for progress tracks, 8dp for tiles, menu rows, keys and nav items, 9dp for preference rows, 11dp for cards, 12dp for PIN digits and the performance panel, 16dp for the focus ring and dialog panels, 18dp for the top corners of sheets, 20dp for dialog buttons and the host pill, 24dp for toasts, and fully round for the Play pill, circle buttons, the avatar and hint badges. Cards have no borders at rest; a stroke only appears as the 3dp green focus ring (2dp for an activated key such as sticky Shift).

The focus ring is the system's signature shape: a 3dp green stroke that stands off the element by a dark gap. On tiles it is a 16dp-radius outline 6dp outside the art; on the Play pill and circle buttons it is built as green, then a 3dp ink band, then the white or glass face inset 6dp.

Icons are an authored stroked set on a 24dp grid (Lucide-derived, ISC), tinted by the view: white on dark, ink on white or green.

## Components

### Buttons
Tactile and decisive: one white pill, everything else glass.
- **Shape:** Fully round (the full radius); dialog buttons use the 20dp pill radius.
- **Primary (Play):** White fill, ink play icon and Bold 16sp label, 56dp tall, 25dp start and 30dp end padding. On focus a green ring with an ink gap wraps it.
- **Circle (more):** 56dp strong glass disc with a white icon; the same ring-and-gap on focus.
- **Small icon buttons:** 30dp glass discs (settings); on focus strong glass plus a 3dp green stroke.
- **Dialog buttons:** Glass pill, white Bold text, 48dp tall; focused or pressed they fill green with ink-green text. Never all caps.

### Cards / Containers
- **Quick-menu and option cards:** Surface-3 at rest, 11dp corners, 13dp inset, 98dp tall. Icon top-left, then a Bold 13sp name and a Medium 11sp state line ("Off", "Type on the PC"). Focus: surface-3-focused, 3dp green stroke, lift to 1.04 and 8dp. The destructive card uses coral for its icon and name.
- **Sheets:** Surface-1 with 18dp top corners, rising over the live stream.
- **Dialog panels:** Surface-2, 16dp corners, background dimmed to 60%.
- **Toasts:** 95% surface-2, 24dp corners, 12dp by 22dp padding.

### Menu Rows
- Transparent 8dp-radius rows with white text. Focus fills the row green and flips text and icons to ink-green. Destructive rows are coral text at rest and fill coral with ink text on focus. Separators use the line colour.

### Inputs / Fields
- **Preference rows:** Transparent at rest; focus gives a surface-3 fill with the 3dp green ring (9dp corners). SemiBold 15sp title on the left; list and slider rows show their current value at the right in Medium 14sp at 74% white, switches show the switch. Descriptions live in the guide card, not in the row. Left and right change the focused row in place: lists step one entry (except language), sliders one step (ten when held), switches turn off or on. Category headers are Bold 12sp at 52% white.
- **Guide card:** Surface-2, 16dp corners. Section name (SemiBold 12sp, 52% white), the row's title (ExtraBold 20sp), its description (12sp, 74% white, 1.35 line spacing). A list's choices are 34dp rows with a ring radio that fill green on focus.
- **Switches:** On is a green track with an ink-green thumb; off is a neutral grey track (#3A404A) and thumb (#9AA0A8).
- **Segmented control:** Glass track (14dp corners); the selected segment is a white 12dp chip; labels SemiBold 12sp.
- **PIN digits:** Surface-3 boxes, 12dp corners, 55sp digits.

### Navigation
- **Spotlight carousel:** Portrait 168x224dp tiles, 10dp corners, 6dp elevation; covers crop to fill, with the title as a Bold 11sp fallback on a dark gradient tile when there is no art. Neighbours scale to 75% / 62.5% at 70% / 35% opacity and are hidden from three away; a move slides the row in 220ms (decelerate). The focused tile carries the 16dp green ring 6dp outside it. Dots under the carousel mark the position: 5dp dots at 30% white, the current one a 16dp white bar.
- **Settings tabs:** Bold 15sp text, 34dp tall, between LB and RB hint pills. Rest is 52% white; the selected tab is white with a 3dp white underline; focus gives a strong-glass pill with the green stroke. Up from the list always lands on the selected tab.
- **Top bar:** App icon, then the host pill (glass, 28dp, monitor icon plus SemiBold 13sp name) on Library or a plain SemiBold 14sp section label on Home, then right-aligned settings, avatar (30dp green disc) and a tabular clock.

### Controller Hints
Bottom-right on the safe line: a 20dp hint-white disc with an ExtraBold 10sp ink letter (or a tinted icon, or a 6dp-radius pill for wider labels), then a SemiBold 12sp label at 74% white. Each hint names the action its button performs on this screen.

### Focus-following Art (signature)
Moving focus along the carousel cross-fades the backdrop (260ms, 300ms on Home) to the new game while the hero title and meta swap.

## Do's and Don'ts

### Do:
- **Do** keep content inside the 48dp by 27dp title-safe frame, and let art and scrims run full-bleed behind it on Home and Library.
- **Do** show focus with the 3dp green ring standing 6dp off the element, or with a solid green fill on rows, keys and dialog buttons.
- **Do** make the primary action a white pill with ink text and put secondary actions on glass.
- **Do** use coral only for destructive actions, and land focus on the safe option in destructive confirmations.
- **Do** put text over art on ink gradients (the vignette and top shade) rather than on panels.
- **Do** use Albert Sans weights for hierarchy (ExtraBold titles at -0.02 tracking, SemiBold labels) and tabular figures for numbers.
- **Do** show the current value as the summary of list and slider preferences.
- **Do** use the authored stroked icon set on a 24dp grid, tinted by the view.

### Don't:
- **Don't** use green as a resting background, a decorative accent or the primary button fill.
- **Don't** put glows, outer blooms or coloured shadows on focused elements; elevation stays soft and neutral.
- **Don't** add borders to cards at rest; a stroke only ever means focus.
- **Don't** lay the library out as a grid of equal cards; it is one carousel centred on the focused game.
- **Don't** set labels in all caps or place small tracked-out lines above titles.
- **Don't** use sharp corners; the smallest radius on a surface is 8dp.
