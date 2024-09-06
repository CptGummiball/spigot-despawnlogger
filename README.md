# spigot-despawnlogger

DespawnLogger is a Spigot plugin for Minecraft (version 1.20 and higher) that logs the despawn events of various NPCs, including animals, mobs, and villagers. Each log entry records the time, despawn cause (e.g., naturally, killed by player, or environmental damage), the coordinates where the event occurred, and optionally, the entity's nametag (if it has one).

## Features

- Logs the despawn of selected entities (e.g., zombies, skeletons, villagers).
- Tracks the cause of despawn (e.g., natural, player kill, lava, etc.).
- Optionally logs nametags of entities.
- Saves logs as `.txt` files in the plugin folder.
- Configurable list of entities to log.
- Limits the number of log files retained (old files are automatically deleted).

## Requirements

- **Minecraft Version**: 1.20 or higher
- **Spigot API**: 1.20-R0.1-SNAPSHOT

## Installation

1. Download the latest release of DespawnLogger from the [Releases](https://github.com/yourusername/DespawnLogger/releases) page.
2. Place the `DespawnLogger.jar` file into the `plugins` folder of your Spigot server.
3. Start or reload your Minecraft server. The plugin will automatically create a `logs` folder and a `config.yml` file inside the `plugins/DespawnLogger` directory.

## Configuration

After the first startup, you can configure the plugin using the `config.yml` file located in `plugins/DespawnLogger/config.yml`.

```yaml
# config.yml
loggable-entities:
  - ZOMBIE
  - COW
  - VILLAGER
  # Add other entities you want to log
max-log-files: 10
log-nametags: true
restart-check-enabled: true  # New option to enable or disable restart checks
```

### Configurable Options

- **loggable-entities**: Define which entities' despawn events should be logged. You can specify any valid Minecraft entity name (e.g., `ZOMBIE`, `CREEPER`, `COW`).
- **max-log-files**: Set the maximum number of log files to retain. If the number of files exceeds this limit, the oldest file will be deleted.
- **log-nametags**: If `true`, the plugin will log the entity's nametag (if it has one) along with the other information. If `false`, the nametag will be omitted.
- **restart-check-enabled**: Option to enable or disable restart checks

## Log Output

Each time an entity despawns, a log entry is written in a `.txt` file inside the `plugins/DespawnLogger/logs` folder. The log format includes:

- The time of despawn
- The entity type
- The cause of despawn (e.g., killed by a player, fire damage, etc.)
- The despawn location (X, Y, Z coordinates)
- Optionally, the entity's nametag (if present and `log-nametags` is set to `true`)

Example log entry:

```
[2024-09-06 10:15:32] ZOMBIE despawned: Cause=FIRE, Location=[-100, 64, 200], Nametag='ZombieKing'
```

If **nametags** are not logged or the entity has no nametag:

```
[2024-09-06 10:15:32] ZOMBIE despawned: Cause=FIRE, Location=[-100, 64, 200]
```

If entities got lost during restart:

```
[2024-09-06 10:15:32] COW was lost during restart at Location=[100, 64, -200]
```

## Contributing

If you'd like to contribute to this project, feel free to open a pull request or an issue on GitHub. Contributions such as bug fixes, new features, or documentation improvements are always welcome!

### Local Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/cptgummiball/spigot-despawnlogger.git
   ```

2. Import the project into your favorite Java IDE (e.g., IntelliJ IDEA or Eclipse).

3. Build the project using Maven:
   ```bash
   mvn clean install
   ```

4. The compiled `.jar` file will be located in the `target` directory.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
