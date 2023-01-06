package me.william278.huskbungeertp.randomtp;

import me.william278.huskbungeertp.HuskBungeeRTP;
import me.william278.huskbungeertp.HuskHomesExecutor;
import me.william278.huskbungeertp.MessageManager;
import me.william278.huskbungeertp.config.Group;
import me.william278.huskbungeertp.jedis.RedisMessage;
import me.william278.huskbungeertp.jedis.RedisMessenger;
import me.william278.huskbungeertp.mysql.DataHandler;
import me.william278.huskbungeertp.plan.PlanDataManager;
import me.william278.huskbungeertp.randomtp.processor.AbstractRtp;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.position.Server;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

public class RtpHandler {

    private static final HuskBungeeRTP plugin = HuskBungeeRTP.getInstance();
    public static HashSet<ProcessingRandomTeleport> rtpUsers = new HashSet<>();

    public static boolean isPlayerRtpProcessing(UUID uuid) {
        HashSet<ProcessingRandomTeleport> teleports = new HashSet<>(rtpUsers);
        for (ProcessingRandomTeleport teleport : teleports) {
            if (teleport.uuid.equals(uuid)) {
                if (teleport.hasTimedOut()) {
                    removeRtpUser(uuid);
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public static void removeRtpUser(UUID uuid) {
        HashSet<ProcessingRandomTeleport> teleports = new HashSet<>(rtpUsers);
        for (ProcessingRandomTeleport teleport : teleports) {
            if (teleport.uuid.equals(uuid)) {
                rtpUsers.remove(teleport);
                return;
            }
        }
    }

    public static class ProcessingRandomTeleport {
        private final long timeOutTime;
        public final UUID uuid;

        public ProcessingRandomTeleport(UUID uuid) {
            this.uuid = uuid;
            this.timeOutTime = Instant.now().getEpochSecond() + HuskBungeeRTP.getSettings().getRtpTimeOutSeconds();
        }

        public boolean hasTimedOut() {
            return Instant.now().getEpochSecond() >= timeOutTime;
        }
    }

    public static void processRtp(Player player, RtpProfile profile) {
        final UUID uuid = player.getUniqueId();
        final boolean canBypassCoolDown = player.hasPermission("huskrtp.bypass_cooldown");
        if (isPlayerRtpProcessing(uuid)) {
            MessageManager.sendMessage(player, "error_already_rtping");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final DataHandler.CoolDownResponse coolDownResponse = DataHandler.getPlayerCoolDown(player.getUniqueId(), profile.getDestinationGroup());
            if (coolDownResponse.isInCoolDown() && !canBypassCoolDown) {
                if (HuskBungeeRTP.getSettings().isUseLastRtpLocationOnCoolDown()) {
                    Position lastRtpPosition = DataHandler.getPlayerLastRtpPosition(uuid, profile.getDestinationGroup());
                    if (lastRtpPosition != null) {
                        if (coolDownResponse.timeLeft() <= 60) {
                            MessageManager.sendMessage(player, "last_rtp_cooldown_seconds", Long.toString(coolDownResponse.timeLeft()));
                        } else {
                            MessageManager.sendMessage(player, "last_rtp_cooldown_minutes", Integer.toString((int) (coolDownResponse.timeLeft() / 60)));
                        }
                        HuskHomesExecutor.teleportPlayer(player, lastRtpPosition);
                        return;
                    }
                }
                if (coolDownResponse.timeLeft() <= 60) {
                    MessageManager.sendMessage(player, "error_cooldown_seconds", Long.toString(coolDownResponse.timeLeft()));
                } else {
                    MessageManager.sendMessage(player, "error_cooldown_minutes", Integer.toString((int) (coolDownResponse.timeLeft() / 60)));
                }
                return;
            }
            MessageManager.sendMessage(player, "processing_rtp");
            rtpUsers.add(new ProcessingRandomTeleport(uuid));

            Group.Server targetServer = determineTargetServer(profile.getDestinationGroup().getServers());
            String targetWorld = determineTargetWorld(targetServer);
            String targetBiome = "ALL";
            Biome profileTargetBiome = profile.getTargetBiome();
            if (profileTargetBiome != null) {
                targetBiome = profileTargetBiome.toString();
            }

            if (targetServer.getName().equals(HuskBungeeRTP.getSettings().getServerId())) {
                final String finalTargetBiome = targetBiome;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    World targetLocalWorld = Bukkit.getWorld(targetWorld);
                    if (targetLocalWorld == null) {
                        targetLocalWorld = player.getWorld();
                    }
                    AbstractRtp.RandomResult result = HuskBungeeRTP.getAbstractRtp().getRandomLocation(targetLocalWorld, finalTargetBiome);
                    if (!result.successful()) {
                        MessageManager.sendMessage(player, "error_rtp_failed", Integer.toString(result.attemptsTaken()));
                        removeRtpUser(uuid);
                        return;
                    }
                    final Position targetPoint = new Position(HuskHomesAPI.getInstance().adaptLocation(result.location()), new Server(targetServer.getName()));
                    HuskHomesExecutor.teleportPlayer(player, targetPoint);
                    removeRtpUser(uuid);

                    // Apply cool down
                    if (!canBypassCoolDown) {
                        DataHandler.setPlayerOnCoolDown(uuid, profile.getDestinationGroup(), targetPoint);
                    }
                });
            } else {
                // Cross server RTP time!
                RedisMessenger.publish(new RedisMessage(targetServer.getName(), RedisMessage.RedisMessageType.REQUEST_RANDOM_LOCATION,
                        uuid + "#" + HuskBungeeRTP.getSettings().getServerId() + "#" + targetWorld + "#" + targetBiome + "#" + profile.getDestinationGroup().getGroupId()));
            }
        });
    }

    /* Determines the target server. If using plan integration, it finds the server with the lowest playtime.
       If two servers both have the lowest playtime, it picks one randomly between them.
       Otherwise, if the plan integration is not being used it picks a random server */
    private static Group.Server determineTargetServer(HashSet<Group.Server> servers) {
        final HashSet<Group.Server> possibleTargets = new HashSet<>(servers);
        switch (HuskBungeeRTP.getSettings().getLoadBalancingMethod()) {
            case PLAN -> {
                if (PlanDataManager.usePlanIntegration()) {
                    PlanDataManager.fetchPlanIfNeeded(); // Pull fresh plan data if needed
                    HashSet<String> targetServerIds = PlanDataManager.getServerIdsWithLowestPlayTime(servers);
                    possibleTargets.clear();
                    for (Group.Server server : servers) {
                        for (String serverId : targetServerIds) {
                            if (server.getName().equals(serverId)) {
                                possibleTargets.add(server);
                                break;
                            }
                        }
                    }
                }
            }
            case PLAYER_COUNTS -> {
                HuskBungeeRTP.updateServerPlayerCounts(); // Pull fresh player counts
                HashSet<String> targetServerIds = HuskBungeeRTP.getServerIdsWithFewestPlayers(servers);
                possibleTargets.clear();
                for (Group.Server server : servers) {
                    for (String serverId : targetServerIds) {
                        if (server.getName().equals(serverId)) {
                            possibleTargets.add(server);
                            break;
                        }
                    }
                }
            }
        }

        final ArrayList<Group.Server> shuffledServers = new ArrayList<>(possibleTargets);
        Collections.shuffle(shuffledServers);
        return shuffledServers.get(0);
    }

    private static String determineTargetWorld(Group.Server server) {
        final ArrayList<String> worlds = new ArrayList<>(server.getWorlds());
        Collections.shuffle(worlds);
        return worlds.get(0);
    }

}
