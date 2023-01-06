package me.william278.huskbungeertp;

import me.william278.huskbungeertp.config.Settings;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.teleport.TimedTeleport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.StringJoiner;
import java.util.logging.Level;

public class HuskHomesExecutor {

    private static final HuskBungeeRTP plugin = HuskBungeeRTP.getInstance();

    public static void teleportPlayer(Player player, Position position) {
        if (Bukkit.getPluginManager().getPlugin("HuskHomes") == null) {
            plugin.getLogger().log(Level.SEVERE, "Could not find HuskHomes! Please install HuskHomes!");
            return;
        }
        HuskHomesAPI huskHomesAPI = HuskHomesAPI.getInstance();
        huskHomesAPI.teleportBuilder(huskHomesAPI.adaptUser(player))
                .setTarget(position).toTimedTeleport().thenAccept(TimedTeleport::execute);
        if (HuskBungeeRTP.getSettings().doDebugLogging()) {
            if (HuskBungeeRTP.getSettings().getLoadBalancingMethod() == Settings.LoadBalancingMethod.PLAYER_COUNTS) {
                HuskBungeeRTP.rtpLogger.info("RTP - Teleported " + player.getName() + " to server: " + position.server.name + " (world: " + position.world.name + ", with " + HuskBungeeRTP.serverPlayerCounts.get(position.server.name) + " players online)");
                StringJoiner playersOnlineJoiner = new StringJoiner(" | ");
                for (String server : HuskBungeeRTP.serverPlayerCounts.keySet()) {
                    playersOnlineJoiner.add(server + ": " + HuskBungeeRTP.serverPlayerCounts.get(server));
                }
                HuskBungeeRTP.rtpLogger.info("--> Player counts: " + playersOnlineJoiner);
            } else {
                HuskBungeeRTP.rtpLogger.info("RTP - Teleported " + player.getName() + " to server: " + position.server.name + " (world: " + position.world.name + ")");
            }
        }
    }

}
