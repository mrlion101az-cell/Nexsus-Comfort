package com.nexuscraft.nexuscomfort;

import java.util.UUID;

/**
 * What other Nexus plugins ask before they do something to a player. Look it up with
 * {@code Bukkit.getServicesManager().load(ComfortApi.class)}, or by reflection on
 * {@code com.nexuscraft.nexuscomfort.ComfortApi} (the usual soft link): one line before the effect.
 *
 * <pre>{@code
 * if (comfort == null || comfort.allows(player.getUniqueId(), "scary")) { ...do the scary thing... }
 * }</pre>
 */
public interface ComfortApi {

    String SCARY = "scary";
    String PRANKS = "pranks";
    String BODY = "body";
    String PVP = "pvp";
    String MATURE = "mature";

    /**
     * True if this thing may happen to this player. The category is one of the constants above; any other
     * name is not ours to judge, so the answer is true.
     */
    boolean allows(UUID player, String category);

    /** The name of the preset the player is on: "gentle", "standard" or "intense". */
    String preset(UUID player);
}
