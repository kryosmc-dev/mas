package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Menú principal de una mascota: acceso a acciones, cosméticos, efectos, progreso y apodo. */
public class PetMenu extends MenuBase {

    private final String petId;

    public PetMenu(KryosMascotas plugin, PetManager pm, Player player, String petId) {
        super(plugin, pm, player);
        this.petId = petId;
        PlayerPets.Progress pr = pm.data(player.getUniqueId()).get(petId);
        create(54, title("menu.titulo-mascota", "%mascota% <dark_gray>- Menú", pm.displayName(petId, pr), 1, 1));
    }

    @Override
    public void build() {
        inv.clear();
        fillAll();
        PlayerPets pp = pm.data(player.getUniqueId());
        ConfigurationSection sec = pm.petSec(petId);
        PlayerPets.Progress pr = pp.get(petId);
        boolean active = petId.equals(pp.active) && pm.hasActive(player);
        String name = pm.displayName(petId, pr);

        inv.setItem(4, item(petIcon(petId), name + " <white>Nv. <yellow>" + pr.level,
                petTooltip(petId, pp, List.of("<yellow>¡Clic para ver la progresión de niveles!")), active, 1));

        inv.setItem(20, item(Material.SADDLE, "<green><bold>Acciones", List.of(
                "<gray>Montar, sombrero, bailar, edad,",
                "<gray>acariciar, descansar y los trucos",
                "<gray>propios de tu mascota.",
                SEP,
                "<white>Todas gratis.",
                "",
                "<yellow>¡Clic para abrir!"), false, 1));
        inv.setItem(22, item(Material.PAINTING, "<light_purple><bold>Cosméticos", List.of(
                "<gray>Aspectos, colores, accesorios,",
                "<gray>brillo, tamaño y estelas según",
                "<gray>el tipo de mascota.",
                SEP,
                "<white>Todos gratis.",
                "",
                "<yellow>¡Clic para abrir!"), true, 1));
        inv.setItem(24, effectsItem(sec, pr));

        List<String> st = new ArrayList<>();
        st.add("<white>Nivel: <yellow>" + pr.level + "<gray>/" + pm.maxLevel());
        st.addAll(progressLines(pr));
        st.add(SEP);
        st.add("<white>Daño actual: <red>" + fmt(pm.damage(petId, pr.level), 1));
        st.add("<white>Enemigos derrotados: <yellow>" + pr.kills);
        st.add("");
        st.add("<yellow>¡Clic para ver los niveles!");
        inv.setItem(29, item(Material.EXPERIENCE_BOTTLE, "<yellow><bold>Progreso", st, false, 1));

        inv.setItem(31, active
                ? item(Material.RED_DYE, "<red><bold>Guardar mascota",
                        List.of("<gray>Retira a tu mascota.", "", "<yellow>¡Clic para guardarla!"), false, 1)
                : item(Material.LIME_DYE, "<green><bold>Invocar mascota",
                        List.of("<gray>Haz que te acompañe.", "", "<yellow>¡Clic para invocarla!"), false, 1));

        List<String> rl = new ArrayList<>(nextRewards(petId, pr.level, 5));
        rl.add("");
        rl.add("<yellow>¡Clic para ver todos los niveles!");
        inv.setItem(33, item(Material.CHEST, "<gold><bold>Próximas recompensas", rl, false, 1));

        inv.setItem(40, item(Material.NAME_TAG, "<green><bold>Renombrar mascota", List.of(
                "<gray>Ponle un apodo a tu mascota.",
                SEP,
                "<white>Nombre: " + (pr.nickname == null ? "<gray>ninguno" : name),
                "<white>Tipo: " + sec.getString("nombre", petId),
                "",
                "<yellow>¡Clic para renombrar!"), false, 1));

        inv.setItem(45, item(Material.ARROW, "<yellow><bold>Volver", List.of("<gray>Lista de mascotas."), false, 1));
        inv.setItem(49, closeItem());
    }

    private ItemStack effectsItem(ConfigurationSection sec, PlayerPets.Progress pr) {
        List<String> l = new ArrayList<>();
        List<String> efectos = sec.getStringList("efectos");
        if (efectos.isEmpty()) l.add("<gray>Esta mascota no da efectos.");
        for (String s : efectos) {
            String[] p = s.split(":");
            if (p.length < 3) continue;
            int need = PetManager.parseInt(p[2], 1);
            String n = effectName(p[0]) + " " + roman(PetManager.parseInt(p[1], 0) + 1);
            l.add(pr.level >= need ? "<green>+ <white>" + n : "<dark_gray>- " + n + " (Nv." + need + ")");
        }
        l.add(SEP);
        l.add("<gray>Se aplican mientras tu mascota");
        l.add("<gray>está contigo.");
        return item(Material.BREWING_STAND, "<aqua><bold>Efectos pasivos", l, false, 1);
    }

    @Override
    public void click(int slot, ClickType type) {
        PlayerPets pp = pm.data(player.getUniqueId());
        switch (slot) {
            case 4, 29, 33 -> {
                tick();
                later(() -> new LevelsMenu(plugin, pm, player, petId, -1).open());
            }
            case 20 -> {
                tick();
                later(() -> new ActionsMenu(plugin, pm, player, petId).open());
            }
            case 22 -> {
                tick();
                later(() -> new CosmeticsMenu(plugin, pm, player, petId).open());
            }
            case 31 -> {
                boolean active = petId.equals(pp.active) && pm.hasActive(player);
                if (active) pm.dismiss(player);
                else pm.spawn(player, petId, false);
                tick();
                build();
            }
            case 40 -> {
                tick();
                pm.startRename(player, petId);
                later(player::closeInventory);
            }
            case 45 -> {
                tick();
                later(() -> new PetsMenu(plugin, pm, player, 0).open());
            }
            case 49 -> later(player::closeInventory);
            default -> { }
        }
    }
}
