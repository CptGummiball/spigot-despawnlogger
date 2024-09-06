package com.cptgummiball.despawnlogger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;

public class DespawnLogger extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private File logFolder;
    private boolean logNametags;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        config = getConfig();

        // Log folder setup
        logFolder = new File(getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Check max log files and clean up if necessary
        checkMaxLogFiles();

        // Load the setting for logging nametags
        logNametags = config.getBoolean("log-nametags", false);

        getLogger().info("DespawnLogger has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DespawnLogger has been disabled.");
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

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase();
        if (command.startsWith("/kill")) {
            // Handle the /kill command by logging the despawn of all affected entities
            Bukkit.getScheduler().runTaskLater(this, () -> {
                List<LivingEntity> entities = event.getPlayer().getWorld().getLivingEntities();
                for (LivingEntity entity : entities) {
                    if (!entity.isDead()) continue;
                    logEntityDespawn(entity, DamageCause.CUSTOM);
                }
            }, 1L);
        }
    }

    private void logEntityDespawn(LivingEntity entity, DamageCause cause) {
        // Check if the entity is in the list of loggable entities
        List<String> loggableEntities = config.getStringList("loggable-entities");
        EntityType entityType = entity.getType();
        if (!loggableEntities.contains(entityType.toString())) {
            return; // Not a loggable entity, return
        }

        // Get entity location
        String location = String.format("[%d, %d, %d]", entity.getLocation().getBlockX(),
                entity.getLocation().getBlockY(),
                entity.getLocation().getBlockZ());

        // Get current time
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Log entry format
        StringBuilder logEntry = new StringBuilder();
        logEntry.append("[").append(timestamp).append("] ")
                .append(entityType).append(" despawned: ")
                .append("Cause=").append(cause != null ? cause : "UNKNOWN")
                .append(", Location=").append(location);

        // If nametag logging is enabled and the entity has a custom name
        if (logNametags && entity.getCustomName() != null) {
            logEntry.append(", Nametag='").append(entity.getCustomName()).append("'");
        }

        // Write log entry
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
            // Sort log files by last modified date and delete the oldest
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
}
