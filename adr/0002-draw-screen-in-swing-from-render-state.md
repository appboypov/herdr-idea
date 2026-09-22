# ADR-0002: Draw the terminal screen in Swing from libghostty's render state

- Status: accepted
- Date: 2026-09-22
- Supersedes: none

## Context

libghostty-vt provides terminal state and an incremental render state (`ghostty_render_state_*`, with a dirty-row iterator) but draws nothing. The panel must follow the IDE's console font and colour scheme. Ghostty's user config and GPU renderer are out of scope. The IDE offers JediTerm (its own emulator) and, on `master`, an editor-based terminal built on IDE internals.

## Considered Options

- A plugin-owned Swing component that paints dirty rows from an immutable frame copied out of the render state.
- JediTerm: brings its own emulator, so the screen would not be Ghostty-powered.
- The IDE editor component: heavy, and tied to IDE-internal APIs that change between builds.

## Decision

A dedicated terminal thread owns all native handles. After each output batch it copies the dirty rows into an immutable `ScreenFrame` and publishes it to the EDT. The Swing component paints only dirty rows, using the IDE console font and colour scheme.

## Consequences

- Good: no dependency on IDE terminal internals, and a clear thread boundary around native memory.
- Good: font and colours follow the IDE automatically.
- Bad: text shaping, wide-glyph fitting and paint performance are the plugin's responsibility.
- Follow-up: measure repaint cost on a full-screen Herdr redraw before adding glyph caching.
