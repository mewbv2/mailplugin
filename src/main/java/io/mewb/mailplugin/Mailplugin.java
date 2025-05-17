package io.mewb.mailplugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class Mailplugin extends JavaPlugin {

    private MailManager mailManager;
    private BukkitTask expirationTask;

    @Override
    public void onLoad() {
        ConfigurationSerialization.registerClass(MailMessage.class, "MailMessage");
    }

    @Override
    public void onEnable() {

        getLogger().info("MailPlugin is enabling!");


        saveDefaultConfig();


        this.mailManager = new MailManager(this);
        mailManager.loadAllMail(); // Load mail after MailManager is initialized


        MailCommand mailCommand = new MailCommand(this, mailManager);
        getCommand("mail").setExecutor(mailCommand);
        getCommand("mail").setTabCompleter(mailCommand);


        getServer().getPluginManager().registerEvents(new MailListener(this, mailManager), this);


        long cleanupIntervalTicks = getConfig().getLong("mail.cleanup-interval-minutes", 60) * 60 * 20; // Default 1 hour
        if (cleanupIntervalTicks > 0) {
            this.expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                getLogger().info("Running scheduled mail cleanup...");
                mailManager.cleanupExpiredMail();
            }, 20L * 60, cleanupIntervalTicks);
        }

        getLogger().info("MailPlugin has been enabled successfully.");
    }

    @Override
    public void onDisable() {

        getLogger().info("MailPlugin is disabling.");

        if (expirationTask != null && !expirationTask.isCancelled()) {
            expirationTask.cancel();
        }


        if (mailManager != null) {
            mailManager.saveAllMail();
        }

        getLogger().info("MailPlugin has been disabled.");
    }

    public MailManager getMailManager() {
        return mailManager;
    }
}