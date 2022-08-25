[![HuskBungeeRTP Banner](images/banner-graphic.png)](https://github.com/WiIIiam278/HuskBungeeRTP)
# HuskBungeeRTP
[![Discord](https://img.shields.io/discord/818135932103557162?color=7289da&logo=discord)](https://discord.gg/tVYhJfyDWG)

**HuskBungeeRTP** is a cross-server random teleport plugin that utilises [HuskHomes](https://github.com/WiIIiam278/HuskHomes), Redis and MySQL to allow players to randomly teleport to Spigot servers on a proxy network.

## Features
The system works as follows and requires a mySQL (8.0+) Database and Redis server. Note that I will be referring to the "origin" server (the server upon which the player or console runs the /rtp command) and the "destination" server (the server the player will be randomly teleported within)

* The plugin on the origin server will pick which server the player will be randomly teleported on.
    * It does this randomly, or if you have the Plan hook enabled it will check which server has the lowest playtime (to load-balance players onto the least populated server on your network).
    * The plugin picks from the servers in the "destination group" (a set of servers you define that players can be randomly teleported onto - you can set multiple groups and target which group you want to randomly teleport to onto via the command, as well as pick a target biome)
* The origin server plugin will communicate over Redis to the chosen server and ask the destination server plugin to find a safe random spot.
    * The plugin doesn't require, but I strongly recommend that you install JakesRTP on destination servers and enable the hook in the config as the destination server will then use the laser-fast and super configurable algorithm and settings provided by that plugin.
* The destination server will reply back to the origin server using Redis with the chosen random location.
* The origin server will use HuskHomes' cross-server teleportation functionality to efficiently teleport the player to the randomly chosen spot.

All that, hopefully, in a fraction of a second! It also makes use of mySQL to track this process and to enable the cool down feature. Usually this will happen very fast, but on slower servers - and very often if you specify a less common / rare / impossible biome, the rtp will "fail" and time out.

### Commands
BungeeRTP for HuskHomes provides one command to do this: `/brtp` (Permission node: `huskrtp.command.rtp`). This command can be run through console, too, allowing you to make some custom setups with other plugins. /rtp will also work for this command, but you will need to disable HuskHomes' built in RTP first.

There is also a `/huskbungeertp` (Permission node: `huskrtp.command.admin`) command to let you view the groups loaded on the server, Plan play time calculations & lets you reload the config files. You can also fully customise the /rtp command messages using hex color codes via the advanced, powerful MineDown formatting system.

## Setup
### Requirements
* A network of proxied Spigot-based game servers
* [HuskHomes](https://github.com/WiIIiam278/HuskHomes) installed and using Bungee mode on your spigot servers
* A Redis server
* A MySQL database (v8.0+)

### Installation
1. Download and install HuskHomes, HuskBungeeRTP & (Optional) [JakesRTP](https://www.spigotmc.org/resources/jakes-rtp.80201/) on all the spigot servers.
2. (Optional) Install Plan (Player Analytics) on all your spigot servers and your proxy server. Configure Plan, ensuring the server names are set correctly in the Plan config (e.g /server survival1 has it's server name as survival1 in the Plan config)
3. Configure HuskHomes as per these instructions if you have not already.
4. If you have not already, start and stop all of the servers you are installing it onto to let HuskBungeeRTP generate it's config files.
5. Open up the config.yml file generated in ~/plugins/HuskBungeeRTP/
6. Fill in your mySQL Database credentials under mysql_credentials and your Redis credentials under redis_credentials (You need a Redis server and mySQL Database for this). MySQL must be Version 8.0+, older versions are not supported and you will get an error.
7. Set this_server_id to match the ID of the server whose HuskBungeeRTP config you are currently editing (e.g for /server survival1, put survival1 here).
8. (Optional) If you want to use the Plan hook, set use_plan to true
9. Now you can set your groups. To start with, to have one group of servers, delete the entire group2 section from the file for now (including the group2: parent key)
10. Unless you really need to, you can leave the table name alone, but you can change the cooldown (for no cooldown, set it to 0). Groups contain servers_worlds: and your group needs to contain valid destination servers and the worlds within them.
      * For instance,lets say you have three survival servers and a spawn server, where each survival server contains a "world" (overworld) as well as a "world_nether" and "world_the_end".
      * Let's say you want players to be able to randomly teleport to survival1, survival2 or survival3, but not randomly teleport to the spawn server (but possibly from). In each config, you would then have, in your group, something like this.
      ```
      group1:
      table_name: huskrtp_group_1
      cooldown_minutes: 6
      servers_worlds:
      survival1:
      - world
        survival2:
      - world
        survival3:
      - world
      ```

    * In this way, a player teleporting to group_1 from any server with HuskBungeeRTP installed on the network will be taken to a random position on the survival1, survival2 or survival3 servers (in the world "world")
    * You can define multiple groups, and using the /brtp command you will be able to specify which group you wish to randomly teleport within.
    * You **must** set the default_rtp_group for each server in the config file too. If you only have one group, then that should be it, otherwise it's the group you want players to go to when they run the /rtp command without specifying a group. (e.g set it to group1 with the above example given)
11. Once you have configured your groups and saved your config files (group definitions should match between servers), you can restart each server and the plugin should function! Test it out with the /brtp command (usage: /brtp [player] [target group] [target biome])

## bStats
This plugin uses bStats to provide me with [metrics about its usage](https://bstats.org/plugin/bukkit/HuskBungeeRTP/12830).

You can turn metric collection off by navigating to `plugins/bStats/config.yml` and editing the config to disable plugin metrics.

## Support
* Report bugs: [Click here](https://github.com/WiIIiam278/HuskBungeeRTP/issues)
* Discord support: Join the [HuskHelp Discord](https://discord.gg/tVYhJfyDWG)!
