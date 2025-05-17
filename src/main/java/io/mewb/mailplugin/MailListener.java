package io.mewb.mailplugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.stream.Collectors;

public class MailListener implements Listener {

    private final Mailplugin plugin;
    private final MailManager mailManager;

    public MailListener(Mailplugin plugin, MailManager mailManager) {
        this.plugin = plugin;
        this.mailManager = mailManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("notifications.notify-on-login", true)) {
            return;
        }

        Player player = event.getPlayer();
        // Delay slightly to ensure other plugins/login processes complete
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            List<MailMessage> unreadMail = mailManager.getMailForPlayer(player.getUniqueId())
                    .stream()
                    .filter(mail -> !mail.isRead() && (!mail.hasItems() || !mail.isClaimed()))
                    .collect(Collectors.toList());

            if (!unreadMail.isEmpty()) {
                String message = plugin.getConfig().getString("notifications.login-notification-message",
                        "§eYou have %count% unread mail message(s). Type §f/mail §eto check your inbox.");
                message = message.replace("%count%", String.valueOf(unreadMail.size()));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }, 20L * 2); // 2 seconds delay
    }
}