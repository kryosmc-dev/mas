package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Selector de una opción cosmética con muchas elecciones (colores, aspectos...). */
public class ChoiceMenu extends MenuBase {

    private final String petId;
    private final Cosmetics.Option option;
    private final Map<Integer, Cosmetics.Choice> slotChoice = new HashMap<>();

    public ChoiceMenu(KryosMascotas plugin, PetManager pm, Player player, String petId, String optionKey) {
        super(plugin, pm, player);
        this.petId = petId;
        String type = pm.petSec(petId).getString("entidad", "WOLF").toUpperCase();
        this.option = Cosmetics.find(type, optionKey);
        String name = option == null ? "Opciones" : option.name();
        create(54, title("menu.titulo-opcion", "<gradient:#ffaa00:#ff5555><bold>%mascota%", name, 1, 1));
    }

    @Override
    public void build() {
        inv.clear();
        slotChoice.clear();
        fillAll();
        if (option == null) return;
        PlayerPets.Progress pr = pm.data(player.getUniqueId()).get(petId);
        String cur = pm.cosmetic(pr, option);

        inv.setItem(4, item(option.icon(), "<aqua><bold>" + option.name(),
                List.of("<gray>" + option.desc(), SEP, "<white>Actual: <aqua>" + option.choice(cur).name()), true, 1));

        for (int i = 0; i < option.choices().size() && i < GRID.length; i++) {
            Cosmetics.Choice c = option.choices().get(i);
            boolean sel = c.key().equalsIgnoreCase(cur);
            slotChoice.put(GRID[i], c);
            inv.setItem(GRID[i], item(c.icon(), (sel ? "<green><bold>" : "<white>") + c.name(),
                    List.of(sel ? "<green>Seleccionado" : "<yellow>¡Clic para elegir!"), sel, 1));
        }
        inv.setItem(45, item(Material.ARROW, "<yellow><bold>Volver", List.of("<gray>Cosméticos."), false, 1));
        inv.setItem(49, closeItem());
    }

    @Override
    public void click(int slot, ClickType type) {
        if (slot == 45) {
            tick();
            later(() -> new CosmeticsMenu(plugin, pm, player, petId).open());
            return;
        }
        if (slot == 49) {
            later(player::closeInventory);
            return;
        }
        Cosmetics.Choice c = slotChoice.get(slot);
        if (c == null || option == null) return;
        pm.setCosmetic(player, petId, option.key(), c.key());
        sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.3f);
        build();
    }
}
