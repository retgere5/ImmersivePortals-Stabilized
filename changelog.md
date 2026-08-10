# Changelog

All notable changes to this project will be documented in this file.  
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project tries to adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [6.1.0-alpha.1] - Unreleased

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

[6.1.0-alpha.1]: https://github.com/retgere5/ImmersivePortals-Stabilized/compare/v6.0.7...HEAD
[6.0.7]: https://github.com/iPortalTeam/ImmersivePortalsModForNeo/releases/tag/v6.0.7
[6.0.6]: https://github.com/iPortalTeam/ImmersivePortalsModForNeo/releases/tag/v6.0.6
[6.0.3]: https://github.com/iPortalTeam/ImmersivePortalsModForNeo/releases/tag/v6.0.3

