package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** Confirmación de compra de una mascota. */
public class ConfirmMenu extends MenuBase {

    private final String petId;
    private final int page;
    private final PetsMenu.Filter filter;

    public ConfirmMenu(KryosMascotas plugin, PetManager pm, Player player, String petId, int page, PetsMenu.Filter filter) {
        super(plugin, pm, player);
        this.petId = petId;
        this.page = page;
        this.filter = filter;
        create(27, plugin.getConfig().getString("menu.titulo-confirmar", "<gradient:#ffaa00:#ff5555><bold>Confirmar compra"));
    }

    @Override
    public void build() {
        inv.clear();
        fillAll();
        PlayerPets pp = pm.data(player.getUniqueId());
        double price = pm.price(petId);
        boolean afford = price <= 0 || !pm.hasEconomy() || pm.balance(player) >= price;

        List<String> footer = new ArrayList<>();
        if (price > 0 && pm.hasEconomy()) {
            footer.add("<white>Saldo después: <yellow>" + pm.money(Math.max(0, pm.balance(player) - price)));
        }
        inv.setItem(13, item(petIcon(petId), pm.petSec(petId).getString("nombre", petId),
                petTooltip(petId, pp, footer), true, 1));

        List<String> yes = new ArrayList<>();
        yes.add("<gray>Comprarás a <white>" + pm.petSec(petId).getString("nombre", petId));
        yes.add("<gray>por <gold>" + (price > 0 ? pm.money(price) : "gratis") + "<gray>.");
        yes.add("");
        yes.add(afford ? "<yellow>¡Clic para confirmar!" : "<red>No tienes suficiente dinero.");
        inv.setItem(11, item(afford ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                afford ? "<green><bold>Confirmar compra" : "<gray><bold>Sin fondos", yes, false, 1));
        inv.setItem(15, item(Material.RED_CONCRETE, "<red><bold>Cancelar",
                List.of("<gray>Volver a la lista de mascotas."), false, 1));
    }

    @Override
    public void click(int slot, ClickType type) {
        if (slot == 15) {
            tick();
            later(() -> new PetsMenu(plugin, pm, player, page, filter).open());
            return;
        }
        if (slot != 11) return;

        PetManager.BuyResult r = pm.buy(player, petId);
        switch (r) {
            case OK -> {
                plugin.msg(player, "<green>¡Compraste a <white>" + pm.petSec(petId).getString("nombre", petId)
                        + "<green>! Ya puedes invocarla.");
                sound(Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
                later(() -> new PetsMenu(plugin, pm, player, page, filter).open());
            }
            case NO_MONEY -> {
                plugin.msg(player, "<red>No tienes suficiente dinero.");
                deny();
            }
            case NO_ECONOMY -> {
                plugin.msg(player, "<red>La tienda no está disponible (falta Vault o un plugin de economía).");
                deny();
            }
            case OWNED -> {
                plugin.msg(player, "<yellow>Ya tienes esta mascota.");
                later(() -> new PetsMenu(plugin, pm, player, page, filter).open());
            }
            default -> deny();
        }
    }
}
