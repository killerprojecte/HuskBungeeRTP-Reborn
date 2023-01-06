package me.william278.huskbungeertp.randomtp.processor;

import me.william278.huskbungeertp.HuskBungeeRTP;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class DefaultRtp extends AbstractRtp {

    // List containing all unsafe blocks
    private static final Set<Material> UNSAFE_BLOCKS = Collections.unmodifiableSet(EnumSet.of(
            Material.LAVA, Material.FIRE, Material.MAGMA_BLOCK, Material.CACTUS, Material.WATER, Material.OBSIDIAN,
            Material.JUNGLE_LEAVES, Material.SPRUCE_LEAVES, Material.OAK_LEAVES,
            Material.BIRCH_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES
    ));

    // Maximum number of attempts to find a random location
    public static final int RADIUS = 5000;

    @Override
    public void initialize() {}

    @Override
    public RandomResult getRandomLocation(World world, String targetBiomeString) {
        int attempts;
        final int maxAttempts = HuskBungeeRTP.getSettings().getMaxRtpAttempts();
        for (attempts = 0; attempts < maxAttempts; attempts++) {
            Location randomLocation = randomLocation(world);
            boolean isLocationValid = isLocationSafe(randomLocation);
            if (!targetBiomeString.equalsIgnoreCase("ALL")) {
                Biome targetBiome = Biome.valueOf(targetBiomeString);
                if (world.getBiome(randomLocation.getBlockX(), randomLocation.getBlockY(), randomLocation.getBlockZ()) != targetBiome) {
                    isLocationValid = false;
                }
            }
            if (isLocationValid) {
                if (world.getEnvironment().equals(World.Environment.NETHER)) {
                    Block block = world.getBlockAt(randomLocation);
                    block.setType(Material.AIR);
                    Block top = world.getBlockAt(randomLocation.getBlockX(), randomLocation.getBlockY() + 1, randomLocation.getBlockZ());
                    top.setType(Material.AIR);
                }
                return new RandomResult(randomLocation, true, attempts);
            }
        }
        return new RandomResult(null, false, attempts);
    }

    private Location randomLocation(World world) {
        // Generate a random location
        Random random = ThreadLocalRandom.current();

        int x;
        int y = 64;
        int z;
        double blockCenterX = 0.5;
        double blockCenterZ = 0.5;

        // The furthest distance at which a player can be teleported to
        int rtpRange = Math.min(RADIUS,
                (int) world.getWorldBorder().getSize() - 1
        );

        // Calculate random X and Z coords
        x = random.nextInt(rtpRange);
        z = random.nextInt(rtpRange);

        // Determine if the plugin should teleport to positive or negative Z
        if (random.nextBoolean()) {
            x *= -1;
            blockCenterX *= -1;
        }
        if (random.nextBoolean()) {
            z *= -1;
            blockCenterZ *= -1;
        }

        // Put together random location, get the highest block plus one to determine Y
        Location randomLocation = new Location(world, (x + blockCenterX), y, (z + blockCenterZ));
        if (world.getEnvironment().equals(World.Environment.NETHER)) {
            boolean safe = false;
            for (int i = 0; i < HuskBungeeRTP.getSettings().getMaxRtpAttempts(); i++) {
                if (safe) break;
                int random_y = random.nextInt((120 - 2) + 1) + 2;
                randomLocation.setY(random_y);
                safe = isLocationSafe(randomLocation);
            }
        } else {
            y = world.getHighestBlockYAt(randomLocation) + 1;
            randomLocation.setY(y);
        }

        return randomLocation;
    }

    // Checks to see if the location is safe to teleport to
    private boolean isLocationSafe(Location location) {
        int x = location.getBlockX();
        int y = location.getBlockY()-1;
        int z = location.getBlockZ();

        World world = location.getWorld();

        // Failsafe check in case the world is null
        if (world != null) {
            // Ensure we are inside the world border.
            if (!world.getWorldBorder().isInside(location)) {
                return false;
            }

            // Get instances of the blocks around where the player would teleport
            Block block = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);
            Block above = world.getBlockAt(x, y + 1, z);

            if (world.getEnvironment().equals(World.Environment.NETHER)) {
                if (y >= 127) return false;
            }

            // Check to see if those blocks are safe to teleport to
            if (!UNSAFE_BLOCKS.contains(block.getType()) && block.getType().isSolid()) {
                if (!UNSAFE_BLOCKS.contains(above.getType())) {
                    return !UNSAFE_BLOCKS.contains(below.getType());
                }
            }
        }
        return false;
    }
}
