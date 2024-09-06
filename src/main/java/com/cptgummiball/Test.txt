package com.cptgummiball.despawnlogger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
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
    private File logFolder;
    private FileConfiguration config;
    private List<String> loggableEntities;
    private int maxLogFiles;

    @Override
    public void onEnable() {
        // Load the config file
        saveDefaultConfig();
        config = getConfig();
        loggableEntities = config.getStringList("loggable-entities");
        maxLogFiles = config.getInt("max-log-files");

        // Create log folder if it doesn't exist
        logFolder = new File(getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        // Manage old logs
        manageOldLogs();

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, this);

        // Create a new log file on server start
        createNewLog();
    }

    @Override
    public void onDisable() {
        getLogger().info("DespawnLogger disabled.");
    }

    private void manageOldLogs() {
        File[] logFiles = logFolder.listFiles();
        if (logFiles != null && logFiles.length > maxLogFiles) {
            // Sort by age and delete the oldest files
            File oldest = logFiles[0];
            for (File file : logFiles) {
                if (file.lastModified() < oldest.lastModified()) {
                    oldest = file;
                }
            }
            if (!oldest.delete()) {
                getLogger().log(Level.WARNING, "Could not delete old log file: " + oldest.getName());
            } else {
                getLogger().log(Level.INFO, "Deleted old log file: " + oldest.getName());
            }
        }
    }

    private File currentLogFile;

    private void createNewLog() {
        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        currentLogFile = new File(logFolder, "log_" + timeStamp + ".txt");
        try {
            if (currentLogFile.createNewFile()) {
                getLogger().log(Level.INFO, "Created new log file: " + currentLogFile.getName());
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error creating log file.", e);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        EntityType entityType = event.getEntityType();
        if (loggableEntities.contains(entityType.name())) {
            String deathCause = "naturally";
            if (event.getEntity().getLastDamageCause() != null) {
                DamageCause cause = event.getEntity().getLastDamageCause().getCause();
                deathCause = cause.name();
            }
            logDespawn(entityType.name(), deathCause, event.getEntity().getLocation().getBlockX(),
                    event.getEntity().getLocation().getBlockY(), event.getEntity().getLocation().getBlockZ());
        }
    }

    private void logDespawn(String entityName, String cause, int x, int y, int z) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentLogFile, true))) {
            String logEntry = String.format("[%s] %s despawned: Cause=%s, Location=[%d, %d, %d]",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    entityName, cause, x, y, z);
            writer.write(logEntry);
            writer.newLine();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error writing to log file.", e);
        }
    }
}
