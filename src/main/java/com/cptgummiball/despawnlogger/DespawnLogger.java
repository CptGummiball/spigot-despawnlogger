package com.cptgummiball.despawnlogger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DespawnLogger extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private File logFolder;
    private File entityFile;
    private Map<UUID, String[]> savedEntities = new HashMap<>();
    private boolean restartCheckEnabled;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        config = getConfig();
        
        // Get the option to enable/disable restart check
        restartCheckEnabled = config.getBoolean("restart-check-enabled", true);

        // Log folder setup
        logFolder = new File(getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Check max log files and clean up if necessary
        checkMaxLogFiles();

        // Load saved entities from previous session if restart check is enabled
        if (restartCheckEnabled) {
            entityFile = new File(getDataFolder(), "entities_before_shutdown.yml");
            if (entityFile.exists()) {
                loadSavedEntities();
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
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        // Check if the entity is a loggable entity
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity entity = (LivingEntity) event.getEntity();
        logEntityDespawn(entity, null); // Despawn cause unknown (e.g., natural despawn, chunk unload)
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
        logEntry.append("[").append(timestamp).append("] ")
                .append(entityType).append(" despawned: ")
                .append("Cause=").append(cause != null ? cause : "UNKNOWN")
                .append(", Location=").append(location);

        // If nametag logging is enabled and the entity has a custom name
        if (config.getBoolean("log-nametags", false) && entity.getCustomName() != null) {
            logEntry.append(", Nametag='").append(entity.getCustomName()).append("'");
        }

        writeLog(logEntry.toString());
    }

    private void writeLog(String logEntry) {
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

        for (Entity entity : Bukkit.getWorlds().get(0).getEntities()) { // Assuming we work with the main world
            if (entity instanceof LivingEntity) {
                String[] entityInfo = {
                    entity.getType().toString(),
                    entity.getLocation().getBlockX() + "," +
                    entity.getLocation().getBlockY() + "," +
                    entity.getLocation().getBlockZ()
                };
                entityData.set(entity.getUniqueId().toString(), entityInfo);
            }
        }

        try {
            entityData.save(entityFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save entities before shutdown", e);
        }
    }

    private void loadSavedEntities() {
        YamlConfiguration entityData = YamlConfiguration.loadConfiguration(entityFile);

        for (String key : entityData.getKeys(false)) {
            String[] entityInfo = entityData.getStringList(key).toArray(new String[0]);
            savedEntities.put(UUID.fromString(key), entityInfo);
        }
    }

    private void compareEntitiesAfterRestart() {
        for (Map.Entry<UUID, String[]> entry : savedEntities.entrySet()) {
            UUID entityId = entry.getKey();
            String[] entityInfo = entry.getValue();

            Entity entity = Bukkit.getWorlds().get(0).getEntity(entityId); // Assuming we work with the main world
            if (entity == null) {
                String location = entityInfo[1];
                String type = entityInfo[0];
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String logEntry = "[" + timestamp + "] " + type + " was lost during restart at Location=" + location;
                writeLog(logEntry);
            }
        }
        entityFile.delete(); // Clean up after comparison
    }
}
