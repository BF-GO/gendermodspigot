# Female Gender Mod Plugin

Female Gender Mod Plugin is a community-maintained Paper/Folia plugin that synchronizes per-player settings for
[Wildfire's Female Gender Mod](https://modrinth.com/mod/female-gender). It provides the server-side synchronization
layer only: every player who uses the feature must still install a compatible version of the client mod.

This project is not affiliated with or endorsed by the Female Gender Mod authors.

## Features

- Synchronizes Female Gender Mod settings between nearby players who are tracking one another.
- Supports both Fabric and Forge client transports without sending duplicate packets.
- Supports the protocol V5 play-phase hello handshake used by `5.0.0-Beta.4`.
- Uses Paper's entity schedulers and remains compatible with Folia's region threading model.
- Validates client payloads and safely ignores malformed, oversized, unauthorized, or excessive messages.
- Supports the historical V2-V4 packet formats for older client mod releases.

## Compatibility

| Component | Supported versions |
|:----------|:-------------------|
| Server software | Paper and Folia |
| Minecraft | 1.21.x through 26.2 |
| Java | 21 for Minecraft 1.21.x; 25 for Minecraft 26.2 |
| Plugin bytecode | Java 21 |
| Female Gender Mod | See the protocol table below |

Spigot and Minecraft versions older than 1.21 are not supported. Folia 26.2 support remains beta while the
corresponding Folia server builds are beta.

Protocol V5 is fixture-tested against Female Gender Mod `5.0.0-Beta.4`, including its play-phase hello handshake.
The future V5 handshake revision 2 from the mod's development branch is not supported yet.

## Installation

1. Download the plugin JAR from [Modrinth](https://modrinth.com/plugin/female-gender-spigot) or the
   [GitHub releases page](https://github.com/BF-GO/gendermodspigot/releases).
2. Stop the server.
3. Place the JAR in the server's `plugins` directory.
4. Start the server and confirm that `Female-Gender-Mod-Plugin` enabled successfully.
5. Install a compatible Female Gender Mod version on each client that should participate in synchronization.

To upgrade, stop the server, replace the old plugin JAR, and start the server again. Existing configuration options
remain compatible with version `1.6.1`.

## Configuration

The generated `plugins/Female-Gender-Mod-Plugin/config.yml` contains these options:

```yaml
mod:
  protocol: -1

logging:
  enabled: true
  debug: false
```

### Protocol selection

`mod.protocol` selects the Female Gender Mod packet format:

| Protocol | Female Gender Mod versions |
|:--------:|:---------------------------|
| 2 | 2.8.1 - 3.0.1 |
| 3 | 3.1.0 - 4.0.0 |
| 4 | 4.0.1 - 4.3.4 |
| 5 | 5.0.0-Beta.4 |

The default value `-1` selects the newest supported protocol, currently V5. Set an explicit protocol only when the
server intentionally targets an older client mod release. An unsupported value prevents the plugin from enabling so
that incompatible channels and listeners are never registered.

### Logging

- `logging.enabled` controls all plugin console logging.
- `logging.debug` enables additional diagnostic messages when logging is enabled.

Restart the server after changing the configuration.

## Synchronization and safety

The plugin sends a player's configuration only to clients currently tracking that player's entity. Fabric V5 clients
must complete the compatible hello handshake before receiving Fabric sync packets; Forge clients continue to use the
`main_channel` transport without that handshake.

Clients may update only the profile matching their own player UUID. Invalid data is discarded without changing stored
state, broadcasting it to other players, or kicking the sender. Repeated invalid or excessive traffic is also prevented
from flooding the server log.

## Troubleshooting

### The mod works locally but other players do not see the settings

- Confirm that every affected player has a compatible client mod installed.
- Confirm that the configured protocol matches the client mod version.
- Check the server startup log for the selected protocol and plugin enable status.
- Enable `logging.debug`, restart the server, and reproduce the issue.

### The plugin disables itself during startup

Check `mod.protocol` in `config.yml`. Use `-1` for the latest supported format or one of the documented protocol
numbers above.

### Can this run on Spigot?

No. This plugin depends on Paper APIs for entity tracking and Folia-compatible scheduling.

If the problem persists, open an issue and include the server version, Java version, plugin version, configured
protocol, client mod version, and relevant log output.

## Building from source

Requirements:

- JDK 21 or newer
- Maven

Run the full verification build:

```shell
mvn clean verify
```

The resulting JAR is written to `target/Female-Gender-Mod-Plugin-1.6.1.jar` and remains Java 21 bytecode even when the
build runs on a newer JDK.

## Project links

- [Source code](https://github.com/BF-GO/gendermodspigot)
- [Issue tracker](https://github.com/BF-GO/gendermodspigot/issues)
- [GitHub releases](https://github.com/BF-GO/gendermodspigot/releases)
- [Modrinth releases](https://modrinth.com/plugin/female-gender-spigot)

## Credits

Created by dbrighthd, with contributions from Stigstille, winnpixie, BF-GO, and original plugin-development help from
Flamgop.
