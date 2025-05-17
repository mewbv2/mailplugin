package io.mewb.mailplugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MailManager {

    private final Mailplugin plugin;
    private final Map<UUID, List<MailMessage>> playerMailboxes = new ConcurrentHashMap<>();
    private final File mailDataFolder;
    private long defaultMailRetentionDays;

    public MailManager(Mailplugin plugin) {
        this.plugin = plugin;
        this.mailDataFolder = new File(plugin.getDataFolder(), "maildata");
        if (!mailDataFolder.exists()) {
            if (!mailDataFolder.mkdirs()) {
                plugin.getLogger().severe("Could not create maildata folder!");
            }
        }
        this.defaultMailRetentionDays = plugin.getConfig().getLong("mail.default-retention-days", 30);
    }

    private File getPlayerMailFile(UUID playerId) {
        return new File(mailDataFolder, playerId.toString() + ".yml");
    }

    public void loadMailForPlayer(UUID playerId) {
        File playerFile = getPlayerMailFile(playerId);
        if (!playerFile.exists()) {
            playerMailboxes.put(playerId, new ArrayList<>());
            return;
        }
        FileConfiguration mailConfig = YamlConfiguration.loadConfiguration(playerFile);
        List<?> rawMailList = mailConfig.getList("mail");
        List<MailMessage> mailList = new ArrayList<>();
        if (rawMailList != null) {
            for (Object obj : rawMailList) {
                if (obj instanceof MailMessage) {
                    mailList.add((MailMessage) obj);
                } else if (obj instanceof Map) {
                    try {
                        mailList.add(MailMessage.deserialize((Map<String, Object>) obj));
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Could not deserialize mail message from map for player " + playerId, e);
                    }
                }
            }
        }
        playerMailboxes.put(playerId, mailList.stream()
                                              .filter(m -> m != null)
                                              .collect(Collectors.toList()));
        plugin.getLogger().info("Loaded " + mailList.size() + " mail messages for player " + playerId);
    }

    public void saveMailForPlayer(UUID playerId) {
        List<MailMessage> mailList = playerMailboxes.get(playerId);
        if (mailList == null) { // No mail or player not loaded
            File playerFile = getPlayerMailFile(playerId);
            if (playerFile.exists()) {
                playerFile.delete();
                plugin.getLogger().info("Removed mail file for player " + playerId + " as they have no mail.");
            }
            return;
        }
        
        if (mailList.isEmpty()) { // Mail list exists but is empty
             File playerFile = getPlayerMailFile(playerId);
             if (playerFile.exists()) {
                 playerFile.delete();
                 plugin.getLogger().info("Player " + playerId + " has no mail. Deleted their mail file.");
             }
             return;
        }

        File playerFile = getPlayerMailFile(playerId);
        FileConfiguration mailConfig = YamlConfiguration.loadConfiguration(playerFile); // Load existing to not overwrite other data if any

        mailConfig.set("mail", mailList); // List of MailMessage objects

        try {
            mailConfig.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mail for player " + playerId, e);
        }
    }

    public void loadAllMail() {
        if (!mailDataFolder.exists() || !mailDataFolder.isDirectory()) {
            plugin.getLogger().info("Mail data folder not found. No mail loaded.");
            return;
        }
        File[] playerFiles = mailDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (playerFiles == null || playerFiles.length == 0) {
            plugin.getLogger().info("No player mail files found in maildata folder.");
            return;
        }
        int count = 0;
        for (File playerFile : playerFiles) {
            String fileName = playerFile.getName();
            try {
                UUID playerId = UUID.fromString(fileName.substring(0, fileName.length() - 4)); // Remove .yml
                loadMailForPlayer(playerId);
                count++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Found invalid mail file name: " + fileName);
            }
        }
        plugin.getLogger().info("Finished loading mail for " + count + " players.");
    }

    public void saveAllMail() {
        plugin.getLogger().info("Saving all player mail data...");
        int count = 0;
        for (UUID playerId : playerMailboxes.keySet()) {
            if(playerMailboxes.containsKey(playerId)){
                 saveMailForPlayer(playerId);
                 count++;
            }
        }
        plugin.getLogger().info("Finished saving mail for " + count + " players.");
    }

    public void sendMail(UUID recipientId, String senderName, String subject, String body, List<ItemStack> items) {
        if (!playerMailboxes.containsKey(recipientId)) {
            loadMailForPlayer(recipientId);
        }

        MailMessage mail = new MailMessage(recipientId, senderName, subject, body, items, defaultMailRetentionDays);
        playerMailboxes.computeIfAbsent(recipientId, k -> new ArrayList<>()).add(mail);
        saveMailForPlayer(recipientId); // Save after adding

        Player recipientPlayer = Bukkit.getPlayer(recipientId);
        if (recipientPlayer != null && recipientPlayer.isOnline()) {
            if (plugin.getConfig().getBoolean("notifications.notify-on-receive", true)) {
                recipientPlayer.sendMessage("§aYou have new mail! Type /mail to check.");
            }
        }
    }

    public List<MailMessage> getMailForPlayer(UUID playerId) {
        if (!playerMailboxes.containsKey(playerId)) {
            loadMailForPlayer(playerId);
        }
        return playerMailboxes.getOrDefault(playerId, new ArrayList<>())
                .stream()
                .filter(mail -> !mail.isExpired())
                .collect(Collectors.toList());
    }
    
    public MailMessage getMailById(UUID playerId, UUID mailId) {

        if (!playerMailboxes.containsKey(playerId)) {
            loadMailForPlayer(playerId);
        }
        List<MailMessage> mailList = getMailForPlayer(playerId); // This already filters expired
        for (MailMessage mail : mailList) {
            if (mail.getMessageId().equals(mailId)) {
                return mail;
            }
        }
        return null;
    }


    public void markAsRead(UUID playerId, UUID mailId) {
        MailMessage mail = getMailById(playerId, mailId);
        if (mail != null && !mail.isRead()) {
            mail.setRead(true);
            saveMailForPlayer(playerId);
        }
    }

    public boolean claimMail(Player player, UUID mailId) {
        UUID playerId = player.getUniqueId();
        MailMessage mail = getMailById(playerId, mailId);

        if (mail != null && !mail.isClaimed()) {
            if (mail.hasItems()) {
                for (ItemStack item : mail.getItems()) {
                    if (player.getInventory().firstEmpty() == -1) {
                        player.sendMessage("§cYour inventory is full. Cannot claim items.");
                        return false;
                    }
                    player.getInventory().addItem(item.clone());
                }
            }
            mail.setClaimed(true);
            player.sendMessage("§aMail claimed: " + mail.getSubject());
            saveMailForPlayer(playerId); // Save after modification
            return true;
        }
        return false;
    }

    public void cleanupExpiredMail() {
        int globallyRemovedCount = 0;
        // Iterate over a copy of keyset to avoid ConcurrentModificationException if a player's mail is loaded inside the loop
        List<UUID> playerIdsToCheck = new ArrayList<>(playerMailboxes.keySet());

        for (UUID playerId : playerIdsToCheck) {
            List<MailMessage> mailList = playerMailboxes.get(playerId);
            if (mailList == null) continue;

            int originalSize = mailList.size();
            boolean modified = mailList.removeIf(MailMessage::isExpired);
            int removedThisPlayer = originalSize - mailList.size();

            if (modified) {
                globallyRemovedCount += removedThisPlayer;
                if (mailList.isEmpty()) {
                    // If all mail for a player expired and was removed,
                    // saveMailForPlayer will handle deleting the file if the list is empty.
                     playerMailboxes.put(playerId, mailList); // Ensure the map reflects the emptied list
                }
                 saveMailForPlayer(playerId); // Save changes for this player
            }
        }
        if (globallyRemovedCount > 0) {
            plugin.getLogger().info("Removed " + globallyRemovedCount + " expired mail messages during cleanup.");
        }
    }
}