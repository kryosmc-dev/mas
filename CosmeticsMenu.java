package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cosméticos gratuitos según el mob: aspectos, colores, accesorios, brillo, estelas... */
public class CosmeticsMenu extends MenuBase {

    private static final int CYCLE_MAX = 6;

    private final String petId;
    private final String type;
    private final Map<Integer, Cosmetics.Option> slotOption = new HashMap<>();

    public CosmeticsMenu(KryosMascotas plugin, PetManager pm, Player player, String petId) {
        super(plugin, pm, player);
        this.petId = petId;
        this.type = pm.petSec(petId).getString("entidad", "WOLF").toUpperCase();
        PlayerPets.Progress pr = pm.data(player.getUniqueId()).get(petId);
        create(54, title("menu.titulo-cosmeticos", "%mascota% <dark_gray>- Cosméticos", pm.displayName(petId, pr), 1, 1));
    }

    @Override
    public void build() {
        inv.clear();
        slotOption.clear();
        fillAll();
        PlayerPets pp = pm.data(player.getUniqueId());
        PlayerPets.Progress pr = pp.get(petId);
        boolean active = petId.equals(pp.active) && pm.hasActive(player);

        inv.setItem(4, item(petIcon(petId), pm.displayName(petId, pr) + " <white>Nv. <yellow>" + pr.level,
                List.of("<gray>Todos los cosméticos son gratis", "<gray>y se guardan para siempre.", SEP,
                        "<gray>Clic izquierdo: <white>siguiente", "<gray>Clic derecho: <white>anterior"), active, 1));

        inv.setItem(GRID[0], particleItem(pp, petId));
        List<Cosmetics.Option> options = Cosmetics.forType(type);
        for (int i = 0; i < options.size() && i + 1 < GRID.length; i++) {
            Cosmetics.Option o = options.get(i);
            int slot = GRID[i + 1];
            slotOption.put(slot, o);
            inv.setItem(slot, optionItem(o, pr));
        }

        inv.setItem(45, item(Material.ARROW, "<yellow><bold>Volver", List.of("<gray>Menú de la mascota."), false, 1));
        inv.setItem(49, closeItem());
    }

    private org.bukkit.inventory.ItemStack optionItem(Cosmetics.Option o, PlayerPets.Progress pr) {
        String cur = pm.cosmetic(pr, o);
        Cosmetics.Choice cc = o.choice(cur);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + o.desc());
        lore.add(SEP);
        lore.add("<white>Actual: <aqua>" + cc.name());
        if (o.choices().size() <= CYCLE_MAX) {
            lore.add("");
            for (Cosmetics.Choice c : o.choices()) {
                lore.add((c.key().equalsIgnoreCase(cur) ? "<green>» <white>" : "<dark_gray>   ") + c.name());
            }
            lore.add("");
            lore.add("<yellow>¡Clic para cambiar!");
        } else {
            lore.add("<white>Opciones: <yellow>" + o.choices().size());
            lore.add("");
            lore.add("<yellow>¡Clic para elegir!");
        }
        return item(o.icon(), "<aqua><bold>" + o.name(), lore, false, 1);
    }

    @Override
    public void click(int slot, ClickType clickType) {
        PlayerPets pp = pm.data(player.getUniqueId());
        if (slot == 45) {
            tick();
            later(() -> new PetMenu(plugin, pm, player, petId).open());
            return;
        }
        if (slot == 49) {
            later(player::closeInventory);
            return;
        }
        if (slot == GRID[0]) {
            pm.cycleParticle(player, petId);
            sound(Sound.UI_BUTTON_CLICK, 1.4f);
            build();
            return;
        }
        Cosmetics.Option o = slotOption.get(slot);
        if (o == null) return;
        tick();
        if (o.choices().size() > CYCLE_MAX) {
            later(() -> new ChoiceMenu(plugin, pm, player, petId, o.key()).open());
            return;
        }
        PlayerPets.Progress pr = pp.get(petId);
        String cur = pm.cosmetic(pr, o);
        int idx = 0;
        for (int i = 0; i < o.choices().size(); i++) {
            if (o.choices().get(i).key().equalsIgnoreCase(cur)) idx = i;
        }
        int n = o.choices().size();
        int next = clickType.isRightClick() ? (idx - 1 + n) % n : (idx + 1) % n;
        pm.setCosmetic(player, petId, o.key(), o.choices().get(next).key());
        build();
    }
}
