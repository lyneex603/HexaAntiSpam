package hexa.antispam;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class HexaAntiSpam extends JavaPlugin implements Listener {

    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    private volatile Pattern adPattern;
    private volatile List<String> whitelist = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadSettings() {
        reloadConfig();
        whitelist = getConfig().getStringList("ads.whitelist").stream()
                .map(String::toLowerCase).toList();

        List<String> domains = getConfig().getStringList("ads.domains");
        String ip = "\\d{1,3}(\\s?\\.\\s?\\d{1,3}){3}";
        String regex = ip;
        if (!domains.isEmpty()) {
            String tlds = String.join("|", domains.stream().map(Pattern::quote).toList());
            regex += "|[\\p{L}\\d-]{2,}\\s?(\\.|\\(dot\\)|\\[dot\\])\\s?(" + tlds + ")(?![\\p{L}\\d])";
        }
        adPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("hexa.antispam.bypass")) return;

        String msg = e.getMessage();
        long now = System.currentTimeMillis();

        if (getConfig().getBoolean("delay.enabled", true)) {
            long delayMs = (long) (getConfig().getDouble("delay.seconds", 2) * 1000);
            Long last = lastMessage.get(p.getUniqueId());
            if (last != null && now - last < delayMs) {
                double left = (delayMs - (now - last)) / 1000.0;
                e.setCancelled(true);
                send(p, "delay", String.format("%.1f", left));
                return;
            }
        }

        if (getConfig().getBoolean("caps.enabled", true) && isCaps(msg)) {
            e.setCancelled(true);
            send(p, "caps", "");
            return;
        }

        if (getConfig().getBoolean("ads.enabled", true) && isAd(msg)) {
            e.setCancelled(true);
            send(p, "ad", "");
            return;
        }

        lastMessage.put(p.getUniqueId(), now);
    }

    private boolean isCaps(String msg) {
        int letters = 0;
        int upper = 0;
        for (char c : msg.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        if (letters < getConfig().getInt("caps.min-letters", 6)) return false;
        return upper * 100 >= letters * getConfig().getInt("caps.percent", 90);
    }

    private boolean isAd(String msg) {
        String m = msg.toLowerCase();
        for (String w : whitelist) {
            if (!w.isEmpty()) m = m.replace(w, "");
        }
        return adPattern.matcher(m).find();
    }

    private void send(Player p, String key, String time) {
        String text = getConfig().getString("messages." + key, "");
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', text.replace("%time%", time)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastMessage.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("hexa.antispam.reload")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getConfig().getString("messages.no-permission", "")));
            return true;
        }
        loadSettings();
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("messages.reload", "")));
        return true;
    }
}
