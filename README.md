# Female Gender Mod Plugin

This Paper/Folia plugin synchronizes per-player settings for
[Wildfire's Female Gender Mod](https://modrinth.com/mod/female-gender). The client mod is still required; this plugin
only provides server-side configuration synchronization. It is a community project and is not affiliated with the mod.

Thanks to Flamgop for the original plugin-development help, and to Stigstille and winnpixie for the version ports.

Download releases from [Modrinth](https://modrinth.com/plugin/female-gender-spigot).

## Compatibility

- Paper and Folia: Minecraft 1.21.x through 26.2
- Spigot and Minecraft versions older than 1.21 are not supported
- Minecraft 1.21.x servers require Java 21
- Minecraft 26.2 servers require Java 25
- Folia 26.2 support is beta while the corresponding Folia builds remain beta

Version 1.6.0 is distributed as one Java 21 bytecode JAR for both Paper and Folia. Female Gender Mod 5.0.0-Beta.4
is supported through protocol V5, including its play-phase hello handshake.

## Building

1. Install JDK 21 or newer and Maven.
2. Run `mvn package`.
3. Copy the JAR from `target` into the server's `plugins` directory.

## Configuration

`mod.protocol` selects the Female Gender Mod packet format:

| Protocol | Mod versions |
|:--------:|:-------------|
| 2 | 2.8.1–3.0.1 |
| 3 | 3.1.0–4.0.0 |
| 4 | 4.0.1–4.3.4 |
| 5 | 5.0.0, including Beta.4 |

The default value `-1` selects the newest supported protocol (currently V5). Any other unsupported value prevents the
plugin from enabling, so it cannot register incompatible channels or listeners.
