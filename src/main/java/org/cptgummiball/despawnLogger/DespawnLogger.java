package org.cptgummiball.despawnLogger;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.*;

public class DespawnLogger extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private File logFolder;
    private File entityBeforeFile;
    private File entityAfterFile;
    private boolean restartCheckEnabled;
    private boolean chunkUnloadLoggingEnabled;
    private List<String>entityCauses = Arrays.asList(
            "DRAGON_BREATH",
            "ENTITY_ATTACK",
            "ENTITY_EXPLOSION",
            "ENTITY_SWEEP_ATTACK",
            "MAGIC",
            "POISON",
            "PROJECTILE",
            "SONIC_BOOM",
            "THORNS",
            "WITHER");
    private List<String>naturalCauses = Arrays.asList(
            "BLOCK_EXPLOSION",
            "CAMPFIRE",
            "CONTACT",
            "CRAMMING",
            "DROWNING",
            "FALL",
            "FALLING_BLOCK",
            "FIRE",
            "FIRE_TICK",
            "FLY_INTO_WALL",
            "FREEZE",
            "HOT_FLOOR",
            "LAVA",
            "LIGHTNING",
            "MELTING",
            "STARVATION",
            "SUFFOCATION",
            "SUICIDE",
            "VOID");

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        config = getConfig();

        // Get the option to enable/disable restart check
        restartCheckEnabled = config.getBoolean("restart-check-enabled", true);
        chunkUnloadLoggingEnabled = config.getBoolean("chunk-unload-logging", true);

        // Log folder setup
        logFolder = new File(getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Check max log files and clean up if necessary
        checkMaxLogFiles();

        // Load saved entities from previous session and compare after restart
        if (restartCheckEnabled) {
            entityBeforeFile = new File(getDataFolder(), "entities_before_shutdown.yml");
            entityAfterFile = new File(getDataFolder(), "entities_after_restart.yml");

            if (entityBeforeFile.exists()) {
                // Save entities after restart
                saveEntitiesAfterRestart();
                // Compare the two files
                compareEntitiesAfterRestart();
            }
        }
    }

    @Override
    public void onDisable() {
        // Save all living entities before shutdown
        if (restartCheckEnabled) {
            saveEntitiesBeforeShutdown();
            getLogger().info("Saved all entities before shutdown.");
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        logEntityDespawn(event.getEntity(), event.getEntity().getLastDamageCause() != null ? event.getEntity().getLastDamageCause().getCause() : null);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;
                logEntityDespawn(livingEntity, CUSTOM); // Cause is now ChunkUnload
            }
        }
    }

    private void logEntityDespawn(LivingEntity entity, EntityDamageEvent.DamageCause cause) {
        List<String> loggableEntities = config.getStringList("loggable-entities");
        EntityType entityType = entity.getType();
        if (!loggableEntities.contains(entityType.toString())) {
            return; // Not a loggable entity, return
        }

        String location = String.format("[%d, %d, %d]", entity.getLocation().getBlockX(),
                entity.getLocation().getBlockY(),
                entity.getLocation().getBlockZ());

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder logEntry = new StringBuilder();

        if (cause == KILL) {
            logEntry.append("[").append(timestamp).append("] ")
                    .append(entityType).append(" permanently removed: ")
                    .append("Cause=").append("Command")
                    .append(", Location=").append(location);

        } else if (cause == CUSTOM) {
            if (chunkUnloadLoggingEnabled) {
                logEntry.append("[").append(timestamp).append("] ")
                        .append(entityType).append(" temporarily removed: ")
                        .append("Cause=").append("ChunkUnload")
                        .append(", Location=").append(location);
            }

        } else if (entityCauses.contains(cause)) {
                logEntry.append("[").append(timestamp).append("] ")
                    .append(entityType).append(" permanently removed: ")
                    .append("Cause=").append("Killed")
                    .append(", Location=").append(location);

        } else if (naturalCauses.contains(cause)){
                logEntry.append("[").append(timestamp).append("] ")
                        .append("Cause=").append("Naturally")
                        .append(", Location=").append(location);

        } else {
            logEntry.append("[").append(timestamp).append("] ")
                    .append("Cause=").append(cause != null ? cause : "UNKNOWN")
                    .append(", Location=").append(location);
        }

        // If nametag logging is enabled and the entity has a custom name
        if (config.getBoolean("log-nametags", false) && entity.getCustomName() != null) {
            logEntry.append(", Nametag='").append(entity.getCustomName()).append("'");
        }
        writeLog(logEntry.toString());
    }

        private void writeLog(String logEntry){
            File logFile = new File(logFolder, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(logEntry);
                writer.newLine();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to write to log file", e);
            }
        }

        private void checkMaxLogFiles() {
            int maxLogFiles = config.getInt("max-log-files", 10);
            File[] logFiles = logFolder.listFiles((dir, name) -> name.endsWith(".txt"));

            if (logFiles != null && logFiles.length > maxLogFiles) {
                File oldestFile = logFiles[0];
                for (File file : logFiles) {
                    if (file.lastModified() < oldestFile.lastModified()) {
                        oldestFile = file;
                    }
                }
                if (oldestFile.delete()) {
                    getLogger().info("Deleted oldest log file: " + oldestFile.getName());
                }
            }
        }

        private void saveEntitiesBeforeShutdown() {
            YamlConfiguration entityData = new YamlConfiguration();

            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    String location = String.format("[%d, %d, %d]", entity.getLocation().getBlockX(),
                            entity.getLocation().getBlockY(),
                            entity.getLocation().getBlockZ());
                    //String customname = entity.getCustomName();
                    if (entity instanceof LivingEntity) {
                        String[] entityInfo = {
                                entity.getType().toString(),
                                location,
                                //customname
                        };
                        entityData.set(entity.getUniqueId().toString(), entityInfo);
                    }
                }
            }

            try {
                entityData.save(entityBeforeFile);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save entities before shutdown", e);
            }
        }

        private void saveEntitiesAfterRestart() {
            YamlConfiguration entityData = new YamlConfiguration();

            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    String location = String.format("[%d, %d, %d]", entity.getLocation().getBlockX(),
                            entity.getLocation().getBlockY(),
                            entity.getLocation().getBlockZ());
                    //String customname = entity.getCustomName();
                    if (entity instanceof LivingEntity) {
                        String[] entityInfo = {
                                entity.getType().toString(),
                                location,
                                //customname,
                        };
                        entityData.set(entity.getUniqueId().toString(), entityInfo);
                    }
                }
            }

            try {
                entityData.save(entityAfterFile);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save entities after restart", e);
            }
        }

        private void compareEntitiesAfterRestart() {
            YamlConfiguration beforeData = YamlConfiguration.loadConfiguration(entityBeforeFile);
            YamlConfiguration afterData = YamlConfiguration.loadConfiguration(entityAfterFile);

            for (String entityId : beforeData.getKeys(false)) {
                if (!afterData.contains(entityId)) {
                    String[] entityInfo = beforeData.getString(entityId).split(",");
                    String type = entityInfo[0];
                    String location = entityInfo[1];
                    //String customname = entityInfo[2];
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    String logEntry = "[" + timestamp + "] " + type + " was lost during restart at Location=" + location;
                    writeLog(logEntry);
                }
            }

            // Cleanup after comparison
            entityBeforeFile.delete();
            entityAfterFile.delete();
        }
    }

