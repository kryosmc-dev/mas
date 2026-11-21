package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Acciones gratuitas: generales para todas las mascotas y trucos propios de cada mob. */
public class ActionsMenu extends MenuBase {

    private final String petId;
    private final String type;
    private final Map<Integer, String> slotKey = new HashMap<>();
    private final Map<String, Actions.Trick> tricks = new HashMap<>();

    public ActionsMenu(KryosMascotas plugin, PetManager pm, Player player, String petId) {
        super(plugin, pm, player);
        this.petId = petId;
        this.type = pm.petSec(petId).getString("entidad", "WOLF").toUpperCase();
        PlayerPets.Progress pr = pm.data(player.getUniqueId()).get(petId);
        create(54, title("menu.titulo-acciones", "%mascota% <dark_gray>- Acciones", pm.displayName(petId, pr), 1, 1));
    }

    @Override
    public void build() {
        inv.clear();
        slotKey.clear();
        tricks.clear();
        fillAll();
        PlayerPets pp = pm.data(player.getUniqueId());
        PlayerPets.Progress pr = pp.get(petId);
        boolean active = petId.equals(pp.active) && pm.hasActive(player);

        inv.setItem(4, item(petIcon(petId), pm.displayName(petId, pr) + " <white>Nv. <yellow>" + pr.level,
                List.of("<gray>Todas estas acciones son gratis.", SEP,
                        "<gray>Algunas son para todas las mascotas",
                        "<gray>y otras dependen de su especie."), active, 1));

        List<String> keys = new ArrayList<>(List.of("montar", "sombrero", "edad", "acariciar", "bailar", "llamar", "quedarse"));
        if (Actions.canRest(type)) keys.add("descansar");
        for (Actions.Trick t : Actions.forType(type)) {
            keys.add("t:" + t.key());
            tricks.put("t:" + t.key(), t);
        }
        for (int i = 0; i < keys.size() && i < GRID.length; i++) {
            slotKey.put(GRID[i], keys.get(i));
            inv.setItem(GRID[i], itemFor(keys.get(i), pr));
        }

        inv.setItem(45, item(Material.ARROW, "<yellow><bold>Volver", List.of("<gray>Menú de la mascota."), false, 1));
        inv.setItem(49, closeItem());
    }

    private ItemStack itemFor(String key, PlayerPets.Progress pr) {
        UUID id = player.getUniqueId();
        return switch (key) {
            case "montar" -> {
                int need = pm.rideLevel();
                if (pr.level < need) {
                    yield item(Material.SADDLE, "<red><bold>Montar", List.of("<gray>Controla a tu mascota y pasea.", SEP,
                            "<red>Se desbloquea en el nivel <yellow>" + need), false, 1);
                }
                yield item(Material.SADDLE, "<green><bold>Montar", List.of(
                        "<gray>Controla a tu mascota y pasea.", SEP,
                        "<white>WASD <gray>moverte", "<white>Espacio <gray>saltar", "<white>Shift <gray>bajar", "",
                        pm.isRiding(id) ? "<yellow>¡Clic para bajarte!" : "<yellow>¡Clic para montar!"), false, 1);
            }
            case "sombrero" -> {
                int need = pm.hatLevel();
                if (pr.level < need) {
                    yield item(Material.LEATHER_HELMET, "<red><bold>Sombrero", List.of("<gray>Lleva a tu mascota en la cabeza.", SEP,
                            "<red>Se desbloquea en el nivel <yellow>" + need), false, 1);
                }
                boolean on = pm.isHat(id);
                yield item(Material.LEATHER_HELMET, "<green><bold>Sombrero", List.of(
                        "<gray>Lleva a tu mascota en la cabeza.", SEP,
                        on ? "<yellow>¡Clic para bajarla!" : "<yellow>¡Clic para ponértela!"), on, 1);
            }
            case "edad" -> ageItem(pr);
            case "acariciar" -> {
                long wait = pm.cuddleMinutesLeft(id, petId);
                double xp = plugin.getConfig().getDouble("acariciar.xp", 15);
                yield item(Material.COOKIE, "<light_purple><bold>Acariciar", List.of(
                        "<gray>Dale cariño y gana experiencia.", SEP,
                        "<white>Recompensa: <yellow>+" + (int) xp + " XP", "",
                        wait > 0 ? "<red>Disponible en " + wait + " min" : "<yellow>¡Clic para acariciar!"), wait == 0, 1);
            }
            case "bailar" -> item(Material.NOTE_BLOCK, "<light_purple><bold>Bailar", List.of(
                    "<gray>Tu mascota baila unos segundos.", "", "<yellow>¡Clic para bailar!"), false, 1);
            case "llamar" -> item(Material.ENDER_PEARL, "<aqua><bold>Ven aquí", List.of(
                    "<gray>Teletransporta a tu mascota", "<gray>a tu lado.", "", "<yellow>¡Clic para llamarla!"), false, 1);
            case "quedarse" -> {
                boolean stay = pm.isStaying(id);
                yield item(Material.LEAD, stay ? "<green><bold>Seguirme" : "<gold><bold>Quedarse aquí", List.of(
                        stay ? "<gray>Tu mascota está quieta en su sitio." : "<gray>Tu mascota esperará donde está.", "",
                        stay ? "<yellow>¡Clic para que te siga!" : "<yellow>¡Clic para que se quede!"), stay, 1);
            }
            case "descansar" -> {
                boolean rest = pm.isResting(id);
                String how = type.equals("FOX") ? "duerme" : "se sienta";
                yield item(Material.RED_BED, rest ? "<green><bold>Levantarse" : "<blue><bold>Descansar", List.of(
                        "<gray>Tu mascota " + how + " a descansar.", "",
                        rest ? "<yellow>¡Clic para despertarla!" : "<yellow>¡Clic para que descanse!"), rest, 1);
            }
            default -> {
                Actions.Trick t = tricks.get(key);
                yield item(t.icon(), "<gold><bold>" + t.name(), List.of(
                        "<gray>" + t.desc(), SEP, "<dark_gray>Truco de " + type.toLowerCase().replace('_', ' '), "",
                        "<yellow>¡Clic para hacerlo!"), false, 1);
            }
        };
    }

    private ItemStack ageItem(PlayerPets.Progress pr) {
        String c = pm.crianza(petId);
        if (c.equals("SIEMPRE")) {
            return item(Material.EGG, "<aqua><bold>Edad", List.of("<gray>Esta mascota es siempre un bebé.", SEP, "<white>Edad: <aqua>Bebé"), false, 1);
        }
        if (!c.equals("OPCIONAL")) {
            return item(Material.EGG, "<gray><bold>Edad", List.of("<gray>Esta mascota no cambia de tamaño."), false, 1);
        }
        return item(pr.baby ? Material.EGG : Material.WHEAT, "<aqua><bold>Edad", List.of(
                "<gray>Cámbiala cuando quieras, gratis.", SEP,
                "<white>Ahora es: " + (pr.baby ? "<aqua>Bebé" : "<aqua>Adulta"), "",
                pr.baby ? "<yellow>¡Clic para hacerla adulta!" : "<yellow>¡Clic para hacerla bebé!"), false, 1);
    }

    private void ensureActive(PlayerPets pp) {
        if (!petId.equals(pp.active) || !pm.hasActive(player)) pm.spawn(player, petId, true);
    }

    @Override
    public void click(int slot, ClickType type) {
        if (slot == 45) {
            tick();
            later(() -> new PetMenu(plugin, pm, player, petId).open());
            return;
        }
        if (slot == 49) {
            later(player::closeInventory);
            return;
        }
        String key = slotKey.get(slot);
        if (key == null) return;
        PlayerPets pp = pm.data(player.getUniqueId());
        UUID id = player.getUniqueId();

        if (key.startsWith("t:")) {
            ensureActive(pp);
            tick();
            pm.trick(player, tricks.get(key));
            build();
            return;
        }
        switch (key) {
            case "montar" -> {
                if (pp.get(petId).level < pm.rideLevel()) {
                    deny();
                    plugin.msg(player, "<red>Se desbloquea en el nivel <yellow>" + pm.rideLevel() + "<red>.");
                    return;
                }
                tick();
                if (pm.isRiding(id)) {
                    later(() -> {
                        player.closeInventory();
                        pm.stopRide(id);
                    });
                    return;
                }
                ensureActive(pp);
                later(() -> {
                    player.closeInventory();
                    pm.startRide(player);
                });
            }
            case "sombrero" -> {
                if (pp.get(petId).level < pm.hatLevel()) {
                    deny();
                    plugin.msg(player, "<red>Se desbloquea en el nivel <yellow>" + pm.hatLevel() + "<red>.");
                    return;
                }
                tick();
                ensureActive(pp);
                later(() -> {
                    player.closeInventory();
                    pm.toggleHat(player);
                });
            }
            case "edad" -> {
                if (pm.toggleBaby(player, petId)) {
                    tick();
                    build();
                } else {
                    deny();
                }
            }
            case "acariciar" -> {
                ensureActive(pp);
                pm.cuddle(player);
                build();
            }
            case "bailar" -> {
                ensureActive(pp);
                tick();
                pm.dance(player);
                later(player::closeInventory);
            }
            case "llamar" -> {
                ensureActive(pp);
                tick();
                pm.callPet(player);
            }
            case "quedarse" -> {
                ensureActive(pp);
                tick();
                pm.toggleStay(player);
                build();
            }
            case "descansar" -> {
                ensureActive(pp);
                tick();
                pm.toggleRest(player);
                build();
            }
            default -> { }
        }
    }
}
