package me.william278.huskbungeertp.mysql;

import me.william278.huskbungeertp.HuskBungeeRTP;
import me.william278.huskbungeertp.config.Group;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.position.Server;
import net.william278.huskhomes.position.World;
import org.bukkit.Bukkit;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;

public class DataHandler {

    private static HuskBungeeRTP plugin;
    private static Database database;

    private static Connection getConnection() throws SQLException {
        return database.getConnection();
    }

    public static void loadDatabase(HuskBungeeRTP instance) {
        database = new MySQL(instance);
        database.load();
        plugin = instance;
    }

    public static void closeDatabase() {
        database.close();
    }

    public static void addPlayerIfNotExist(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = getConnection()) {
                try (PreparedStatement checkIfPlayerExist = connection.prepareStatement(
                        "SELECT * FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1;")) {
                    checkIfPlayerExist.setString(1, uuid.toString());
                    final ResultSet playerExistResultSet = checkIfPlayerExist.executeQuery();

                    // If the player does not exist yet
                    if (!playerExistResultSet.next()) {
                        try (PreparedStatement createPlayerStatement = connection.prepareStatement(
                                "INSERT INTO " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " (`user_uuid`) VALUES (?)")) {
                            createPlayerStatement.setString(1, uuid.toString());
                            createPlayerStatement.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred adding a player", e);
            }
        });
    }

    /*
    Not thread safe, perform inside a RunTaskAsynchronously!
     */
    public static CoolDownResponse getPlayerCoolDown(UUID uuid, Group group) {
        boolean isPlayerOnCoolDown = false;
        long coolDownTimeLeft = 0L;

        try (Connection connection = getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM " + group.getGroupDatabaseTableName() + " WHERE `player_id`=(SELECT `id` FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1) LIMIT 1;")) {
                preparedStatement.setString(1, uuid.toString());
                final ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    Timestamp lastRtpTimestamp = resultSet.getTimestamp("last_rtp");
                    coolDownTimeLeft = lastRtpTimestamp.toInstant().getEpochSecond() + (60L * group.coolDownTimeMinutes()) - Instant.now().getEpochSecond();
                    if (coolDownTimeLeft <= 0) {
                        try (PreparedStatement deletePlayerCoolDownStatement = connection.prepareStatement(
                                "DELETE FROM " + group.getGroupDatabaseTableName() + " WHERE `player_id`=(SELECT `id` FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1) LIMIT 1;")) {
                            deletePlayerCoolDownStatement.setString(1, uuid.toString());
                            deletePlayerCoolDownStatement.executeUpdate();
                        }
                    } else {
                        isPlayerOnCoolDown = true;
                    }
                }
            }
        }  catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred returning player cooldown data", e);
        }

        return new CoolDownResponse(isPlayerOnCoolDown, coolDownTimeLeft);
    }

    /*
        Not thread safe, perform inside a RunTaskAsynchronously!
     */
    public static Position getPlayerLastRtpPosition(UUID uuid, Group group) {
        Position point = null;
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM " + group.getGroupDatabaseTableName() + " WHERE `player_id`=(SELECT `id` FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1) LIMIT 1;")) {
                statement.setString(1, uuid.toString());
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    point = new Position(
                            resultSet.getDouble("dest_x"),
                            resultSet.getDouble("dest_y"),
                            resultSet.getDouble("dest_z"),
                            0F, 0F,
                            new World(resultSet.getString("dest_world"), UUID.randomUUID()),
                            new Server(resultSet.getString("dest_server")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred returning last RTP position data", e);
        }
        return point;
    }

    public static void setPlayerOnCoolDown(UUID uuid, Group group, Position point) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = getConnection()) {
                try (PreparedStatement isPlayerNotAlreadySetCheck = connection.prepareStatement(
                        "SELECT * FROM " + group.getGroupDatabaseTableName() + " WHERE `player_id`=(SELECT `id` FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=?);")) {
                    isPlayerNotAlreadySetCheck.setString(1, uuid.toString());
                    ResultSet playerSetResultSet = isPlayerNotAlreadySetCheck.executeQuery();
                    if (!playerSetResultSet.next()) {
                        try (PreparedStatement getPlayerIdStatement = connection.prepareStatement(
                                "SELECT * FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1;")) {
                            getPlayerIdStatement.setString(1, uuid.toString());
                            ResultSet playerIdSet = getPlayerIdStatement.executeQuery();
                            if (playerIdSet.next()) {
                                int playerId = playerIdSet.getInt("id");
                                try (PreparedStatement preparedStatement = connection.prepareStatement(
                                        "INSERT INTO " + group.getGroupDatabaseTableName() + " (`player_id`,`dest_world`,`dest_x`,`dest_y`,`dest_z`,`dest_server`) VALUES(?,?,?,?,?,?);")) {
                                    preparedStatement.setInt(1, playerId);
                                    preparedStatement.setString(2, point.world.name);
                                    preparedStatement.setDouble(3, point.x);
                                    preparedStatement.setDouble(4, point.y);
                                    preparedStatement.setDouble(5, point.z);
                                    preparedStatement.setString(6, point.server.name);
                                    preparedStatement.executeUpdate();
                                }
                            }
                        }

                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred updating player cooldown data", e);
            }
        });
    }

    /**
     * Record used to hold information about the cool down status of a player
     */
    public record CoolDownResponse(boolean isInCoolDown, long timeLeft) {
    }

}
