# Immersive Portals: Stabilized (fork)

Stability-focused fork of [Immersive Portals for NeoForge](https://github.com/iPortalTeam/ImmersivePortalsModForNeo)
by qouteall & Nick1st (Apache-2.0). Original Fabric mod: https://github.com/iPortalTeam/ImmersivePortalsMod
Focus: modpack stability (NeoForge 1.21.1), quarantine switches for exotic features, performance.
The upstream repos are archived/dormant; this fork continues that work with attribution.

## Immersive Portals Mod

It's a Minecraft mod that provides see-through portals and seamless teleportation. It also can create "Non-Euclidean" (Uneuclidean) space effect.

![immptl.png](https://i.loli.net/2021/09/30/chHMG45dsnZNqep.png)

[On CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-mod)     [On Modrinth](https://modrinth.com/mod/immersiveportals)     [Website](https://qouteall.fun/immptl/)

This mod changes a lot of underlying Minecraft mechanics. This mod allows the client to load multiple dimensions at the same time and synchronize remote world information(blocks/entities) to client. It can render portal-in-portals. The portal rendering is roughly compatible with some versions of Sodium and Iris. The portal can transform player scale and gravity direction.  [Implementation Details](https://qouteall.fun/immptl/wiki/Implementation-Details)

## For Modpack Makers
Copy `safe-profile/immersive_portals.json` into your pack's `config/` folder for the
stability-first profile: exotic features (dimension stack) off, recursion capped at 3,
cross-portal chunk radius capped at 4, portal render count capped at 100, no network calls.
The first-launch info wizard is pre-acknowledged in this profile, so pack users won't see it
on first boot. Core seamless portals stay fully enabled. The file's key names are
self-describing; it also carries a `check_the_wiki_for_more_information` link to the
[wiki's config page](https://qouteall.fun/immptl/wiki/Config-Options) for anything not obvious.

## Known limitations

**Sodium version gate.** The Sodium/Iris-Sodium render-compat mixins in this fork are written
against the Sodium 0.6.x generation and are only applied when a supported Sodium (or Embeddium,
which is 0.6-based) version is detected. Sodium 0.8.x removed internals those mixins depend on
(e.g. `OcclusionCuller$Visitor`), which previously crashed world creation on any modpack that
paired this mod with Sodium 0.8. As of this fork, an unsupported Sodium version now fails soft
instead: the Sodium/Iris-Sodium compat mixins are skipped, portal rendering falls back to the
vanilla (non-Sodium) path, and a single warning is logged
(`Immersive Portals: unsupported Sodium <version>; portal-Sodium integration disabled, vanilla
portal rendering in use`). The modpack boots and portals still work, just without Sodium's
rendering optimizations for portal views. Full Sodium 0.8 integration is tracked as later work,
not covered by this gate.

## API

This mod also provides some API for:

* Manage see-through portals
* Dynamically add dimensions
* Synchronize remote chunks to client
* Render the world into GUI
* Other utilities

[API description](https://qouteall.fun/immptl/wiki/API-for-Other-Mods.html).

## How to run this code
https://fabricmc.net/wiki/tutorial:setup

## Other

[Wiki](https://qouteall.fun/immptl/wiki/)

[Discord Server](https://discord.gg/BZxgURK)

[Support qouteall on Patreon](https://www.patreon.com/qouteall)

