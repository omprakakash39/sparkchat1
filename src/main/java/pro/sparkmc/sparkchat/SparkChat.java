
package pro.sparkmc.sparkchat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class SparkChat extends JavaPlugin implements Listener {

    private LuckPerms luckPerms;
    private final Map<String, String> colors = new HashMap<>();

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            getLogger().severe("LuckPerms is required!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        loadColors();
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("hide-advancement-announcements", true)) {
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            }
        }

        getLogger().info("SparkChat enabled!");
    }

    private void loadColors() {
        if (!getConfig().isConfigurationSection("rank-colors")) {
            return;
        }

        for (String rank : getConfig()
                .getConfigurationSection("rank-colors").getKeys(false)) {
            colors.put(
                    rank.toLowerCase(),
                    getConfig().getString("rank-colors." + rank, "#FFFFFF")
            );
        }
    }

    private User getUser(Player player) {
        return luckPerms.getPlayerAdapter(Player.class).getUser(player);
    }

    private String getRank(Player player) {
        return getUser(player).getPrimaryGroup().toLowerCase();
    }

    private String getRankColor(Player player) {
        return colors.getOrDefault(getRank(player), "#FFFFFF");
    }

    private Component colored(String text, String hex) {
        TextColor color = TextColor.fromHexString(hex);

        if (color == null) {
            color = TextColor.color(0xFFFFFF);
        }

        return Component.text(text, color);
    }

    private String getPrefix(Player player) {
        User user = getUser(player);

        QueryOptions options = luckPerms.getContextManager()
                .getQueryOptions(user)
                .orElse(luckPerms.getContextManager()
                        .getStaticQueryOptions());

        String prefix = user.getCachedData()
                .getMetaData(options)
                .getPrefix();

        return prefix == null ? "" : prefix;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (getConfig().getBoolean("hide-join-messages", true)) {
            event.joinMessage(null);
        }

        player.playerListName(
                colored(player.getName(), getRankColor(player))
        );

        if (!player.hasPlayedBefore()) {
            String message = getConfig().getString(
                    "first-join-message",
                    "&eWelcome &f{player} &eto SparkMC!"
            );

            message = message.replace("{player}", player.getName());

            Bukkit.broadcast(LEGACY.deserialize(
                    ChatColor.translateAlternateColorCodes('&', message)
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (getConfig().getBoolean("hide-leave-messages", true)) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        String rankColor = getRankColor(player);

        String foundPrefix = "";

        if (getConfig().getBoolean("include-luckperms-prefix", true)) {
            foundPrefix = getPrefix(player);
        }

        final String prefix = foundPrefix;
        final Component playerName = colored(player.getName(), rankColor);
        final Component chatMessage = colored(message, rankColor);

        event.setCancelled(true);

        Bukkit.getScheduler().runTask(this, () -> {
            Component output = LEGACY.deserialize(prefix)
                    .append(playerName)
                    .append(Component.text(
                            " \u00BB ",
                            TextColor.color(0x555555)
                    ))
                    .append(chatMessage);

            Bukkit.getOnlinePlayers().forEach(viewer ->
                    viewer.sendMessage(output)
            );
        });
    }
}
