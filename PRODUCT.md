# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Primary user: the maintainer, streaming games from one Windows PC to an NVIDIA Shield TV on the living-room TV, using a game controller from the couch. The fork is published on GitHub so other Shield / Android TV owners can use it, but the maintainer's own setup is the design target.

Typical session: one host PC, a library of roughly 5–20 games. The home screen is mostly a pass-through to that one PC's library; the job is "pick a game and be playing within a few presses", then occasionally adjust the stream or end it from the in-stream quick menu.

## Product Purpose
Artemisa is a controller-first Android TV client for streaming games from Apollo or Sunshine hosts. It is a fork of Artemis / Moonlight for Android, reshaped for a 10-foot, D-pad-only experience. Success means it feels like a native console shell rather than a phone app on a TV: predictable focus, few presses, readable from across the room.

## Positioning
Moonlight and Artemis expose every option for every device class (phones, tablets, touchscreens). Artemisa deliberately cuts to what a Shield with a controller needs and presents it as a console UI, while keeping the Artemis streaming feature set (Apollo virtual display, server commands, HDR, surround, frame-pacing options).

## Operating Context
- Viewed on a large TV at couch distance; input is a gamepad (Xbox/Shield-style layout: A/B/X/Y, LB/RB, Menu/View) or the Shield remote D-pad. No touch, no mouse on the UI surfaces.
- Surfaces: home (host list and top actions: settings, help, add PC, profiles), per-host game library (grid or horizontal shelf of portrait covers, plus Desktop), context "Options" menus for a host and a game, pairing PIN dialog, add-computer-manually, stream settings (tabs: Stream, Controls, Appearance, System), profiles, the stream itself with an in-stream quick menu (Select+Start) and its submenus (performance overlay mode, mouse mode, advanced settings, keyboard shortcuts, gamepad on-screen keyboard, disconnect, end stream), performance overlay, toasts, and connection/error dialogs.
- Controller guide conventions already in use: A select/play, B back, Menu for options, LB/RB change tab or keyboard page, X space and Y backspace on the keyboard.

## Capabilities and Constraints
- Native Android Views (XML layouts, Java), androidx Preference for settings. Not Compose, not Leanback.
- Must stay functional on Shield TV (Android 11), 4K output, HDR-capable displays.
- Game covers come from the host (Apollo/Sunshine box art, portrait); some apps (Desktop) have no art.
- Settings were deliberately reduced to the Shield-relevant set (Sept 2026).

## Brand Commitments
- The name "Artemisa" stays.
- The Shield-style green accent (currently #76D600) stays.

## Evidence on Hand
- Current screenshots: `docs/screenshots/home.png`, `library.png`, `quick-menu.png`, `appearance.png`.
- Branding artwork: `artwork/branding/`.
- No user research, testimonials, or metrics exist; do not fabricate any.

## Product Principles
1. Fewest presses from launch to playing; the one-PC case is the main path.
2. Focus is always visible and always predictable; B always goes back one level.
3. The stream is the product; menus appear over it briefly and get out of the way.
4. Show only what a Shield with a controller can use.
5. Readable at 10 feet: large type, high contrast, no dense text.

## Accessibility & Inclusion
TV-distance legibility and controller-only operability on every surface; focus state must never rely on color alone.
