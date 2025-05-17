package io.mewb.mailplugin;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MailCommand implements CommandExecutor, TabCompleter {

    private final Mailplugin plugin;
    private final MailManager mailManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public MailCommand(Mailplugin plugin, MailManager mailManager) {
        this.plugin = plugin;
        this.mailManager = mailManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            // TODO: Add console commands for sending mail, etc.
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("open"))) {
            openMailBook(player);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("claim")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /mail claim <mail_id>");
                return true;
            }
            try {
                UUID mailId = UUID.fromString(args[1]);
                if (mailManager.claimMail(player, mailId)) {
                    // Success message handled in claimMail, refresh book
                    openMailBook(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Could not claim mail. It might be already claimed, expired, or not exist.");
                }
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Invalid mail ID format.");
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("send")) {
            // Usage: /mail send <player> <subject> <message...>
            if (args.length < 4) {
                player.sendMessage(ChatColor.RED + "Usage: /mail send <player> <subject> <message...>");
                return true;
            }
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                player.sendMessage(ChatColor.RED + "Player " + args[1] + " not found or offline (UUID lookup not implemented).");
                return true;
            }
            String subject = args[2];
            String messageBody = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

            mailManager.sendMail(targetPlayer.getUniqueId(), player.getName(), subject, messageBody, null);
            player.sendMessage(ChatColor.GREEN + "Mail sent to " + targetPlayer.getName() + "!");
            return true;
        }

        // Placeholder for /mail senditem <player>
        if (args.length > 0 && args[0].equalsIgnoreCase("senditem")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /mail senditem <player> [subject]");
                return true;
            }
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                player.sendMessage(ChatColor.RED + "Player " + args[1] + " not found or offline.");
                return true;
            }
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand == null || itemInHand.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "You must be holding an item to send.");
                return true;
            }
            String subject = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Item Delivery";

            mailManager.sendMail(targetPlayer.getUniqueId(), player.getName(), subject, "Please find the attached item.", List.of(itemInHand.clone()));
            player.getInventory().setItemInMainHand(null); // Remove item from sender's hand
            player.sendMessage(ChatColor.GREEN + "Item sent to " + targetPlayer.getName() + "!");
            return true;
        }


        player.sendMessage(ChatColor.YELLOW + "Available commands: /mail open, /mail claim <id>, /mail send <player> <subject> <message>");
        return true;
    }

    private void openMailBook(Player player) {
        List<MailMessage> mailList = mailManager.getMailForPlayer(player.getUniqueId())
                .stream()
                .filter(m -> !m.isClaimed() || !m.hasItems()) // Show unread, or messages that are just text even if read/claimed
                .sorted((m1, m2) -> Long.compare(m2.getSentTimestamp(), m1.getSentTimestamp())) // Newest first
                .collect(Collectors.toList());

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        if (bookMeta == null) { // Should not happen with WRITTEN_BOOK
            player.sendMessage(ChatColor.RED + "Error creating mail book.");
            return;
        }
        bookMeta.setTitle(plugin.getConfig().getString("book-ui.title", "Your Mailbox"));
        bookMeta.setAuthor(plugin.getConfig().getString("book-ui.author", "Server"));

        if (mailList.isEmpty()) {
            TextComponent pageContent = new TextComponent("Your mailbox is empty.");
            pageContent.setColor(net.md_5.bungee.api.ChatColor.GRAY);
            bookMeta.spigot().addPage(new BaseComponent[]{pageContent});
        } else {
            for (MailMessage mail : mailList) {
                List<BaseComponent> pageComponentsList = new ArrayList<>();

                TextComponent subject = new TextComponent("Subject: " + mail.getSubject() + "\n");
                subject.setBold(true);
                subject.setColor(mail.isRead() ? net.md_5.bungee.api.ChatColor.GRAY : net.md_5.bungee.api.ChatColor.DARK_AQUA);
                pageComponentsList.add(subject);

                TextComponent sender = new TextComponent("From: " + mail.getSenderName() + "\n");
                pageComponentsList.add(sender);

                TextComponent date = new TextComponent("Sent: " + dateFormat.format(new Date(mail.getSentTimestamp())) + "\n");
                pageComponentsList.add(date);

                if (mail.getExpiryTimestamp() > 0) {
                    TextComponent expiry = new TextComponent("Expires: " + dateFormat.format(new Date(mail.getExpiryTimestamp())) + "\n");
                    expiry.setColor(net.md_5.bungee.api.ChatColor.RED);
                    pageComponentsList.add(expiry);
                }

                pageComponentsList.add(new TextComponent("\n" + mail.getBody() + "\n\n"));

                if (mail.hasItems() && !mail.isClaimed()) {
                    TextComponent itemsLabel = new TextComponent("Items: ");
                    itemsLabel.setBold(true);
                    pageComponentsList.add(itemsLabel);
                    for (ItemStack item : mail.getItems()) {
                        TextComponent itemName = new TextComponent(item.getAmount() + "x " + getItemName(item) + " ");
                        itemName.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(getItemName(item) + "\n" + item.getType().toString())));
                        pageComponentsList.add(itemName);
                    }
                    pageComponentsList.add(new TextComponent("\n"));

                    TextComponent claimButton = new TextComponent("[Claim Items]");
                    claimButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                    claimButton.setBold(true);
                    claimButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mail claim " + mail.getMessageId().toString()));
                    claimButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to claim items")));
                    pageComponentsList.add(claimButton);
                } else if (mail.hasItems() && mail.isClaimed()) {
                    TextComponent claimedText = new TextComponent("[Items Claimed]");
                    claimedText.setColor(net.md_5.bungee.api.ChatColor.GRAY);
                    pageComponentsList.add(claimedText);
                }

                BaseComponent[] pageAsArray = pageComponentsList.toArray(new BaseComponent[0]);
                bookMeta.spigot().addPage(pageAsArray);
            }
        }
        book.setItemMeta(bookMeta);
        player.openBook(book);
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String materialName = item.getType().toString().toLowerCase().replace('_', ' ');
        return Arrays.stream(materialName.split(" "))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("open", "claim", "send", "senditem");
            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("send") || args[0].equalsIgnoreCase("senditem")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("claim")) {
                if(sender instanceof Player) {
                    Player player = (Player) sender;
                    mailManager.getMailForPlayer(player.getUniqueId()).stream()
                            .filter(m -> !m.isClaimed() && m.hasItems())
                            .forEach(m -> completions.add(m.getMessageId().toString()));
                }
            }
        }
        return completions;
    }
}