package com.nexuscraft.nexusworldwar;

import java.util.UUID;

/**
 * What other plugins may ask NexusWorldWar (register lookup: {@code Bukkit.getServicesManager().load(WorldWarApi.class)},
 * or by reflection on {@code com.nexuscraft.nexusworldwar.WorldWarApi}).
 */
public interface WorldWarApi {

    /** True while a war is being fought. */
    boolean warActive();

    /** True if the player is fighting in the war that is being fought right now. */
    boolean isFighter(UUID player);

    /** True if the player has chosen to be in wars (whether or not one is on). */
    boolean hasJoined(UUID player);
}
