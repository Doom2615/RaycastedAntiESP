### Latest stable version: v1.6.5 | Latest alpha version: Download from the Discord or compile from `main`

The latest stable version can currently only be found on Modrinth https://modrinth.com/plugin/raycasted-anti-esp/ or by compiling from `v1.6.x`. The latest alpha version can be found on the [Discord](https://discord.gg/hGTRAK2hNM) or by compiling from `main`.

This is an async plugin for PaperMC and its forks that hides entities (including players) and tile entities (blocks such as chests, banners, signs, etc) from players if they do not have line-of-sight.

The supported versions are 1.20.6+, with MC 26.x support in v2 only.

## Use cases:

- Prevent cheating (anti-esp hacks)
  - Block usage of pie-ray to locate underground bases
  - Prevent mods such as mini-maps or cheat clients from displaying the locations of hidden entities
- Increase client-side performance for low-end devices
  - Massive megabases containing hundreds of armour stands, item frames, banners etc can cause performance issues on low-end devices unable to process so many entities. Raycasted AntiESP will cull those entities for the client, reducing the number of entities to process.
- Hide nametags behind walls
  - Yes, this plugin is a bit overkill for doing that, yes you can do it anyways.
 
## Dependencies:
- Packetevents
  - In v1: Only needed if you are using the cull-players option and wish for the players to remain in the tablist
  - In v2: Required for all features

## Known issues:
- Due to the nature of the plugin, there will be a short delay once an entity should be visible before it appears, causing it to appear like it "popped" into view.

## Versioning:
Note that the following versioning information only applies to v2 and beyond.

RaycastedAntiESP binaries are composed of four distinct modules: the core, Locatable-lib (used for platform-independent location objects), [CubiLogging](https://github.com/Cubicake/CubiLogging/tree/main), and a platform adapter.

Each of these has its own versioning system. Locatable-lib and the logging api both declare public apis, and follow [Semantic Versioning](https://semver.org/#semantic-versioning-specification-semver). The platform adapter and core do not declare public apis, and thus [cannot follow semantic versioning](https://semver.org/#spec-item-1). They still follow a major.minor.patch versioning system, but which version number increments is less deterministic. Generally, major version bumps will be for when significant refactoring or rewriting has occurred, or a very significant distinct feature has been added or removed. Minor version bumps will be for the addition/removal of smaller distinct features, while patch version bumps are for bug fixes and tweaks or minor addition/removals which are related to the features added/removed in the minor version bumps.

In addition to the versions for each module, there is also an overall version for each platform binary, which is the version that is advertised in the description and file name, and is used for update checks. This version is a combination of the core and platform adapter versions, in the format `{core version}-PlatformName-{platform adapter version}`. This allows all platforms to share the same first three version numbers, while still allowing for differences in the platform adapter versions.

## Credits:

- Cubicake (Sole developer, creator and maintainer of RaycastedAntiESP)

### Special mentions:

- Strokkur424 and other contributors to [StrokkCommands](https://github.com/Strokkur424/StrokkCommands), an LGPL-licensed open-source annotation-based brigadier command tree generator.
  - While StrokkCommands is not essential for the functioning of the project, it makes handling commands infinitely easier.
- Retrooper, Booky10, and all other contributors to [PacketEvents](https://github.com/retrooper/packetevents), a GPL-licensed open-source library for handling minecraft packets.
  - PacketEvents is essential for the functioning of the plugin, as it allows for handling of packets across multiple platforms and Minecraft versions.
- All contributors to Paper and its upstream projects Spigot and Bukkit, without which none of this would be possible.

## Contributions:
Contributions via pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.
