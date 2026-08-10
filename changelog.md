# Changelog

All notable changes to this project will be documented in this file.  
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project tries to adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [6.1.0-alpha.2] - Unreleased

Fix phase: works through the fork's known crash and desync bugs one at a time,
each with a targeted regression test.

### Fixed

- SecurityCraft compatibility crash, by applying upstream PR #50.
- Dimension-id assignment crash for runtime/datapack dimensions, including a
  thread-safety fix so concurrent lazy id assignment (reachable off the
  server thread via packet redirection) can no longer race or corrupt the
  backing map.
- Chunk tracking redesigned to run cooperatively alongside vanilla instead of
  `@Overwrite`-ing it out: vanilla's per-player `ChunkTrackingView` now stays
  populated, fixing a whole class of silent update suppression in mods that
  consult vanilla tracking (Mekanism-class issues).
- Null-pointer crash when the portal frustum culler is uninitialized; falls
  back to vanilla culling instead.
- Portal render pass used a null `hitResult` instead of a miss result,
  causing a crash; fixed to use the miss result.
- Global portal cleanup was bound to the wrong event, deleting global portals
  almost immediately after creation instead of at server shutdown; rebound
  to the correct shutdown event, abnormal-portal cleanup restored, portal
  entities' zero-size dimensions restored (both were regressions from the
  port), and the mod now reports its real version instead of a `1.0.0` stub.
- Teleportation kill-switch (`disableTeleportation`) now also gates the
  server-side teleport path, not just the client-side one.
- Sodium 0.8 fails soft instead of crashing world creation: the Sodium/Iris
  compat mixins are now version-gated and simply skip themselves (with a
  warning) when an unsupported Sodium is detected, falling back to vanilla
  portal rendering. Full 0.8 support is still pending and requires
  NeoForge >= 21.1.219.

### Changed

- Mod version reporting is now accurate (see Fixed above), which has a
  handshake implication: mismatch warnings/kicks against `6.0.7` clients are
  now possible where they weren't before, subject to the existing tolerance
  flags.

## [6.1.0-alpha.1] - 2026-08-10

Fork setup phase: gets the project building cleanly under its own identity, on
a green CI, with the risky/exotic features quarantined behind switches instead
of removed.

### Added

- Fork identity: renamed to Immersive Portals: Stabilized, Apache-2.0 attribution
  carried forward in NOTICE.
- Quarantine kill-switches (`enableDimensionStack`, `disableTeleportation`,
  `enableCrossPortalCollision`) so exotic/risky features can be toggled off
  independently instead of ripped out.
- Safe modpack profile (`safe-profile/immersive_portals.json`) for pack makers:
  dimension stack off, recursion capped at 3, cross-portal radius capped at 4,
  portal render count capped at 100, no network calls.
- GameTest skeleton and a portal-spawn smoke test.
- JUnit 5 wired into the build; previously gutted unit tests restored and passing.
- GitHub Actions CI: build + gametest server run on every push/PR.

### Changed

- Phone-home behavior (update checks, mod-info-from-internet) off by default.
- Heavy dev-runtime mods (Create, Mekanism, Aether, owo-lib, architectury-api,
  geckolib) gated behind `-Pheavy_dev_mods=true`; the default dev loop stays
  lean (Sodium + Iris + Cloth Config + Pehkui).

### Fixed

- Build hygiene: purged dead build files, fixed the template group id, reconciled
  the build-script access-transformer list and `accesstransformer.cfg` (they had
  drifted out of sync), restored the missing mod icon.
- Stale gametest namespace property (`forge.enabledGameTestNamespaces`) was a
  silent no-op under NeoForge; corrected to `neoforge.enabledGameTestNamespaces`
  so gametest filtering actually happens.

### Removed

- Dead dev-runtime dependencies dropped: `oritech` (missing `athena` dependency)
  and `stitch` (no longer published on Modrinth).

## [6.0.7] - 2025-06-18

### Fixed

- Oritech animations not displaying ([#13](https://github.com/iPortalTeam/ImmersivePortalsModForNeo/issues/13)).
- ComputerCraft monitors not displaying anything ([#31](https://github.com/iPortalTeam/ImmersivePortalsModForNeo/issues/31)).

## [6.0.6] - 2024-12-22

### Updated

- Sync upstream (v6.0.6)
- Sodium compat (v0.6.0)
- Iris compat (v1.8.0) (experimental)

### Fixed

- Default config values being wrong

## [6.0.3] - 2024-10-20

### Added

- Initial port to NeoForge 1.21.1

### Known Issues

- Iris compatibility is not fully functional
- Crash with SecurityCraft

[6.1.0-alpha.2]: https://github.com/retgere5/ImmersivePortals-Stabilized/compare/v6.0.7...HEAD
[6.1.0-alpha.1]: https://github.com/retgere5/ImmersivePortals-Stabilized/compare/v6.0.7...628ea46a
[6.0.7]: https://github.com/iPortalTeam/ImmersivePortalsModForNeo/releases/tag/v6.0.7
[6.0.6]: https://github.com/iPortalTeam/ImmersivePortalsModForNeo/releases/tag/v6.0.6
[6.0.3]: https://github.com/iPortalTeam/ImmersivePortalsModForNeo/releases/tag/v6.0.3

