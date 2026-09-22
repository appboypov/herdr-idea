# ADR-0001: Bundle libghostty-vt and call it through FFM

- Status: accepted
- Date: 2026-09-22
- Supersedes: none

## Context

The plugin must be powered by Ghostty's terminal engine and must run on IntelliJ Platform 2026.1 (build 261) on macOS and Linux, arm64 and x64. IntelliJ's own Ghostty-based terminal exists only on `intellij-community` `master` (`terminal.use.ghostty.emulator`); branches `261` and `262` and the installed 2026.1.2 carry no libghostty. libghostty-vt is a C library built with Zig whose API is marked unstable. The bundled JBR 25 supports Java's Foreign Function and Memory API; the platform also bundles JNA.

## Considered Options

- Bundle libghostty-vt per platform and call it through FFM with hand-written bindings.
- Depend on the IDE's own Ghostty terminal: not present on 261 or 262.
- Bundle libghostty-vt and call it through JNA: works, but is slower and non-standard on JBR 25.
- Generate bindings with jextract: large generated surface that churns with every unstable API change.

## Decision

The plugin ships its own libghostty-vt build for `darwin-aarch64`, `darwin-x86_64`, `linux-x86_64` and `linux-aarch64`. Each release pins one Ghostty commit. Kotlin calls the library through `java.lang.foreign`, with hand-written bindings kept in one place.

## Consequences

- Good: works on 2026.1 without depending on the IDE's terminal plugin.
- Good: the engine version is under the plugin's control and does not move with IDE updates.
- Bad: the plugin owns a four-target native build and a larger archive.
- Bad: every Ghostty bump can break bindings, so it needs a render and key smoke test.
- Follow-up: revisit once every supported IDE build bundles a stable libghostty that plugins can use.
