package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menú general: tus mascotas y la tienda, con filtro y páginas. */
public class PetsMenu extends MenuBase {

    public enum Filter {
        TODAS("Todas"), MIS("Mis mascotas"), TIENDA("Tienda");

        final String label;

        Filter(String label) {
            this.label = label;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final int[] PET_SLOTS = new int[21];

    static {
        int i = 0;
        for (int r = 1; r <= 3; r++) {
            for (int c = 1; c <= 7; c++) PET_SLOTS[i++] = r * 9 + c;
        }
    }

    private final List<String> ids = new ArrayList<>();
    private final Filter filter;
    private final int page;
    private final int pages;
    private final Map<Integer, String> slotToPet = new HashMap<>();

    public PetsMenu(KryosMascotas plugin, PetManager pm, Player player, int page, Filter filter) {
        super(plugin, pm, player);
        this.filter = filter;
        PlayerPets pp = pm.data(player.getUniqueId());
        List<String> owned = new ArrayList<>();
        List<String> shop = new ArrayList<>();
        for (String id : pm.petIds()) {
            if (pp.unlocked.contains(id)) owned.add(id);
            else shop.add(id);
        }
        shop.sort((a, b) -> Double.compare(pm.price(a), pm.price(b)));
        if (filter != Filter.TIENDA) ids.addAll(owned);
        if (filter != Filter.MIS) ids.addAll(shop);

        this.pages = Math.max(1, (int) Math.ceil(ids.size() / (double) PET_SLOTS.length));
        this.page = Math.max(0, Math.min(page, pages - 1));
        create(54, title("menu.titulo-general",
                "<gradient:#ffaa00:#ff5555><bold>Mascotas</bold></gradient> <dark_gray>(%pagina%/%paginas%)", "",
                this.page + 1, pages));
    }

    public PetsMenu(KryosMascotas plugin, PetManager pm, Player player, int page) {
        this(plugin, pm, player, page, Filter.TODAS);
    }

    @Override
    public void build() {
        inv.clear();
        slotToPet.clear();
        fillAll();
        PlayerPets pp = pm.data(player.getUniqueId());
        for (int s : PET_SLOTS) inv.setItem(s, null);

        inv.setItem(4, item(Material.NETHER_STAR, "<gradient:#ffaa00:#ff5555><bold>MASCOTAS", List.of(
                "<gray>Compra, sube de nivel y personaliza",
                "<gray>a tus compañeros de aventura.",
                SEP,
                "<yellow>Clic izquierdo: <white>activar, ver niveles o comprar",
                "<yellow>Clic derecho: <white>menú de la mascota"), true, 1));

        int start = page * PET_SLOTS.length;
        for (int i = 0; i < PET_SLOTS.length; i++) {
            if (start + i >= ids.size()) break;
            String id = ids.get(start + i);
            boolean owned = pp.unlocked.contains(id);
            boolean active = owned && id.equals(pp.active) && pm.hasActive(player);
            PlayerPets.Progress pr = pp.get(id);

            String name;
            List<String> footer = new ArrayList<>();
            if (owned) {
                name = pm.displayName(id, pr) + " <white>Nv. <yellow>" + pr.level;
                if (active) {
                    footer.add("<green>Mascota activa");
                    footer.add("<yellow>¡Clic para ver la progresión de niveles!");
                } else {
                    footer.add("<yellow>¡Clic para activar esta mascota!");
                }
                footer.add("<gray>Clic derecho: <white>menú de la mascota");
            } else {
                name = pm.petSec(id).getString("nombre", id) + " <dark_gray>[<gold>En venta<dark_gray>]";
                double price = pm.price(id);
                if (price > 0 && pm.hasEconomy() && pm.balance(player) < price) {
                    footer.add("<red>No tienes suficiente dinero");
                }
                footer.add("<yellow>¡Clic para comprar!");
            }
            inv.setItem(PET_SLOTS[i], item(petIcon(id), name, petTooltip(id, pp, footer), active, 1));
            slotToPet.put(PET_SLOTS[i], id);
        }
        if (ids.isEmpty()) {
            inv.setItem(22, item(Material.PAPER, "<gray>No hay mascotas aquí",
                    List.of("<dark_gray>Cambia el filtro para ver más."), false, 1));
        }

        // ---- barra inferior
        boolean has = pm.hasActive(player);
        inv.setItem(45, has
                ? item(Material.RED_DYE, "<red><bold>Guardar mascota",
                        List.of("<gray>Retira a tu mascota activa.", "", "<yellow>¡Clic para guardarla!"), false, 1)
                : item(Material.GRAY_DYE, "<gray>Guardar mascota",
                        List.of("<dark_gray>No tienes ninguna mascota invocada."), false, 1));

        List<String> fl = new ArrayList<>();
        fl.add("<gray>Elige qué mascotas ver:");
        for (Filter f : Filter.values()) {
            fl.add((f == filter ? "<green>» <white>" : "<dark_gray>   ") + f.label);
        }
        fl.add("");
        fl.add("<yellow>¡Clic para cambiar!");
        inv.setItem(46, item(Material.HOPPER, "<aqua><bold>Filtro", fl, false, 1));

        inv.setItem(47, particleItem(pp, pp.active));

        if (page > 0) {
            inv.setItem(48, item(Material.ARROW, "<yellow>Página anterior",
                    List.of("<gray>Ir a la página " + page + "."), false, 1));
        }
        inv.setItem(49, closeItem());
        if (page < pages - 1) {
            inv.setItem(50, item(Material.ARROW, "<yellow>Página siguiente",
                    List.of("<gray>Ir a la página " + (page + 2) + "."), false, 1));
        }
        inv.setItem(51, statsItem(pp));
        inv.setItem(52, rewardsItem(pp));
        inv.setItem(53, pp.active != null && pm.petSec(pp.active) != null
                ? item(Material.LEAD, "<gold><bold>Menú de tu mascota",
                        List.of("<gray>Acciones, cosméticos, apodo,", "<gray>efectos y más.", "", "<yellow>¡Clic para abrir!"), false, 1)
                : item(Material.GRAY_DYE, "<gray>Menú de tu mascota",
                        List.of("<dark_gray>Invoca una mascota primero."), false, 1));
    }

    private ItemStack rewardsItem(PlayerPets pp) {
        if (pp.active == null || pm.petSec(pp.active) == null) {
            return item(Material.BOOK, "<gold><bold>Próximas recompensas",
                    List.of("<gray>Invoca una mascota para ver", "<gray>sus próximas recompensas."), false, 1);
        }
        List<String> lore = new ArrayList<>(nextRewards(pp.active, pp.get(pp.active).level, 5));
        lore.add("");
        lore.add("<yellow>¡Clic para ver todos los niveles!");
        return item(Material.BOOK, "<gold><bold>Próximas recompensas", lore, false, 1);
    }

    private ItemStack statsItem(PlayerPets pp) {
        int owned = 0;
        int levels = 0;
        int kills = 0;
        List<String> all = new ArrayList<>(pm.petIds());
        for (String id : all) {
            if (!pp.unlocked.contains(id)) continue;
            owned++;
            levels += pp.get(id).level;
            kills += pp.get(id).kills;
        }
        String act = pp.active != null && pm.petSec(pp.active) != null
                ? pm.displayName(pp.active, pp.get(pp.active)) : "<gray>ninguna";
        List<String> l = new ArrayList<>();
        l.add("<white>Mascotas: <yellow>" + owned + "<gray>/" + all.size());
        l.add("<white>Niveles totales: <yellow>" + levels);
        l.add("<white>Enemigos derrotados: <yellow>" + kills);
        l.add("<white>Mascota activa: " + act);
        if (pm.hasEconomy()) {
            l.add(SEP);
            l.add("<white>Tu dinero: <gold>" + pm.money(pm.balance(player)));
        }
        return item(Material.NETHER_STAR, "<aqua><bold>Estadísticas", l, false, 1);
    }

    @Override
    public void click(int slot, ClickType type) {
        PlayerPets pp = pm.data(player.getUniqueId());
        String id = slotToPet.get(slot);

        if (id != null) {
            tick();
            if (!pp.unlocked.contains(id)) {
                later(() -> new ConfirmMenu(plugin, pm, player, id, page, filter).open());
                return;
            }
            if (type.isRightClick() || type.isShiftClick()) {
                later(() -> new PetMenu(plugin, pm, player, id).open());
                return;
            }
            boolean active = id.equals(pp.active) && pm.hasActive(player);
            if (active) {
                later(() -> new LevelsMenu(plugin, pm, player, id, -1).open());
            } else {
                pm.spawn(player, id, false);
                sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
                build();
            }
            return;
        }

        switch (slot) {
            case 45 -> {
                if (pm.hasActive(player)) {
                    pm.dismiss(player);
                    tick();
                    build();
                }
            }
            case 46 -> {
                tick();
                later(() -> new PetsMenu(plugin, pm, player, 0, filter.next()).open());
            }
            case 47 -> {
                if (pp.active != null && pm.petSec(pp.active) != null) {
                    pm.cycleParticle(player, pp.active);
                    sound(Sound.UI_BUTTON_CLICK, 1.4f);
                    build();
                }
            }
            case 48 -> {
                if (page > 0) {
                    tick();
                    later(() -> new PetsMenu(plugin, pm, player, page - 1, filter).open());
                }
            }
            case 49 -> later(player::closeInventory);
            case 50 -> {
                if (page < pages - 1) {
                    tick();
                    later(() -> new PetsMenu(plugin, pm, player, page + 1, filter).open());
                }
            }
            case 52 -> {
                if (pp.active != null && pm.petSec(pp.active) != null) {
                    tick();
                    String a = pp.active;
                    later(() -> new LevelsMenu(plugin, pm, player, a, -1).open());
                }
            }
            case 53 -> {
                if (pp.active != null && pm.petSec(pp.active) != null) {
                    tick();
                    String a = pp.active;
                    later(() -> new PetMenu(plugin, pm, player, a).open());
                }
            }
            default -> { }
        }
    }
}
