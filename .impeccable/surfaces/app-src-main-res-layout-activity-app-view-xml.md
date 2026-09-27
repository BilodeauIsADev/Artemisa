---
version: 1
slug: "app-src-main-res-layout-activity-app-view-xml"
primary_target: "app/src/main/res/layout/activity_app_view.xml"
related_targets: ["app/src/main/res/layout/activity_pc_view_content.xml","app/src/main/res/layout/dialog_game_menu.xml","app/src/main/res/layout/activity_stream_settings.xml"]
---

# Surface brief: Artemisa app shell (all TV surfaces)

Scope: every Android TV surface of Artemisa: home (PcView), library (AppView), game and computer option menus, pairing, stream launch, in-stream quick menu (GameMenu), performance overlay, gamepad keyboard, stream settings, toasts and dialogs.
Visitor mode: Operate. Controller-only, 10-foot, Shield TV at 1080p/4K (960×540 dp).
Job: pick a game and be playing in a few presses; adjust or end the stream from the quick menu.
Constraints: Android Views + Java, androidx Preference, API floor of the app; Artemisa name and #76D600 green are binding (PRODUCT.md).
Reference mock: mockups/dashboard.html (screens in mockups/shots/dashboard-*.png); the chosen decision mock doubles as the critique reference.
Build path: code-led (no image generation available).

## Direction contract

THESIS: The focused game owns the whole screen; the interface is one row of tiles and a title. Refuses the category default of a dark grid of equal cards with outline-glow focus and a header of icon buttons.

OWN-WORLD: Full-bleed art from the focused game (soft blurred backdrop plus the sharp cover faded in from the right), a left-to-right ink shade for legibility. Albert Sans throughout: ExtraBold titles, SemiBold labels. Ink #0B0D10 with surfaces #14171C / #1C2027 / #272C34; white text at 100/74/52%. Green #76D600 only as the focus ring (5px outline, 5px gap) and the selected fill of menu rows and keys; primary action is a white pill. Rounded 14–36dp radii, true soft shadows, no glows, no borders on cards.

STORY: The viewer sees the game they are about to play at poster scale, understands which PC and at what quality (4K · 60 FPS · 80 Mbps), and presses A to play or Menu for other ways to start.

FIRST VIEWPORT: Library. Top bar inside title-safe: host pill with monitor icon and PC name, "Games" tab, then settings button, profile avatar and clock at right. One row of 2:3 tiles starts under it at the left; the focused tile grows 1.33× with the green ring. Bottom-left hero: game title up to two lines at ~44sp ExtraBold, meta line, then a white Play pill and a round "…" button. Controller hints bottom-right. Cover art fills the right ~43% of the screen.

FORM: Art-led console dashboard (PS5/Xbox lineage), the IMPECCABLE'S PICK card of seed 9dd5ec3e. Signature interaction: moving focus along the tile row cross-fades the backdrop and key art to the newly focused game while the hero text swaps; the quick menu is a control-center sheet of cards over the live stream.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
