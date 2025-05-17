package io.mewb.mailplugin;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit; // Needed for deserializing ItemStacks safely

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Collectors;

public class MailMessage implements ConfigurationSerializable {
    private final UUID messageId;
    private final String senderName; // "SERVER" or player name
    private final UUID recipientId;
    private String subject;
    private String body;
    private List<ItemStack> items;
    private final long sentTimestamp;
    private long expiryTimestamp; // 0 for no expiry
    private boolean claimed;
    private boolean read;

    public MailMessage(UUID recipientId, String senderName, String subject, String body, List<ItemStack> items, long retentionDays) {
        this.messageId = UUID.randomUUID();
        this.recipientId = recipientId;
        this.senderName = senderName;
        this.subject = subject == null ? "New Mail" : subject;
        this.body = body == null ? "" : body;
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items); // Use mutable list
        this.sentTimestamp = Instant.now().toEpochMilli();
        if (retentionDays > 0) {
            this.expiryTimestamp = Instant.now().plusSeconds(retentionDays * 24 * 60 * 60).toEpochMilli();
        } else {
            this.expiryTimestamp = 0; // Never expires
        }
        this.claimed = false;
        this.read = false;
    }

    // Private constructor for deserialization
    private MailMessage(UUID messageId, UUID recipientId, String senderName, String subject, String body,
                        List<ItemStack> items, long sentTimestamp, long expiryTimestamp,
                        boolean claimed, boolean read) {
        this.messageId = messageId;
        this.recipientId = recipientId;
        this.senderName = senderName;
        this.subject = subject;
        this.body = body;
        this.items = items;
        this.sentTimestamp = sentTimestamp;
        this.expiryTimestamp = expiryTimestamp;
        this.claimed = claimed;
        this.read = read;
    }

    // Getters
    public UUID getMessageId() { return messageId; }
    public String getSenderName() { return senderName; }
    public UUID getRecipientId() { return recipientId; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public List<ItemStack> getItems() { return items; }
    public long getSentTimestamp() { return sentTimestamp; }
    public long getExpiryTimestamp() { return expiryTimestamp; }
    public boolean isClaimed() { return claimed; }
    public boolean isRead() { return read; }

    // Setters
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
    public void setRead(boolean read) { this.read = read; }
    public boolean hasItems() { return items != null && !items.isEmpty(); }
    public boolean isExpired() { return expiryTimestamp != 0 && Instant.now().toEpochMilli() > expiryTimestamp; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("messageId", this.messageId.toString());
        map.put("senderName", this.senderName);
        map.put("recipientId", this.recipientId.toString());
        map.put("subject", this.subject);
        map.put("body", this.body);
        map.put("items", this.items);
        map.put("sentTimestamp", this.sentTimestamp);
        map.put("expiryTimestamp", this.expiryTimestamp);
        map.put("claimed", this.claimed);
        map.put("read", this.read);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static MailMessage deserialize(Map<String, Object> args) {
        UUID messageId = UUID.fromString((String) args.get("messageId"));
        String senderName = (String) args.get("senderName");
        UUID recipientId = UUID.fromString((String) args.get("recipientId"));
        String subject = (String) args.get("subject");
        String body = (String) args.get("body");
        List<ItemStack> items = (List<ItemStack>) args.get("items");
        if (items == null) {
            items = new ArrayList<>();
        }

        long sentTimestamp = ((Number) args.get("sentTimestamp")).longValue();
        long expiryTimestamp = ((Number) args.get("expiryTimestamp")).longValue();
        boolean claimed = (boolean) args.get("claimed");
        boolean read = (boolean) args.get("read");

        return new MailMessage(messageId, recipientId, senderName, subject, body, items,
                               sentTimestamp, expiryTimestamp, claimed, read);
    }
}