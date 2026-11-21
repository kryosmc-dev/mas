package com.kryos.mascotas;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Datos de mascotas de un jugador. */
public class PlayerPets {

    public final Set<String> unlocked = new LinkedHashSet<>();
    public final Map<String, Progress> progress = new HashMap<>();
    /** Niveles ya cobrados cuando las recompensas son una sola vez por jugador. */
    public final Set<Integer> claimedGlobal = new HashSet<>();
    public String active;

    public Progress get(String id) {
        return progress.computeIfAbsent(id, k -> new Progress());
    }

    public static class Progress {
        public int level = 1;
        public double xp = 0;
        /** "auto" = mejor estela desbloqueada, "none" = sin estela, o el nombre de una partícula. */
        public String particle = "auto";
        public final Set<Integer> claimed = new HashSet<>();
        public int kills = 0;
        public String nickname;
        /** Solo aplica a mascotas con crianza OPCIONAL. */
        public boolean baby = false;
        /** Cosméticos elegidos: clave de la opción -> clave de la elección. */
        public final Map<String, String> cosmetics = new HashMap<>();
    }
}
