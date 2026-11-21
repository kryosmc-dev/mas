package com.kryos.mascotas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Base común de los menús: marco decorativo, utilidades y tooltips compartidos. */
public abstract class MenuBase implements InventoryHolder {

    protected static final int[] GRID = new int[21];

    static {
        int i = 0;
        for (int r = 1; r <= 3; r++) {
            for (int c = 1; c <= 7; c++) GRID[i++] = r * 9 + c;
        }
    }

    protected static final String SEP = "<dark_gray><strikethrough>                      ";

    protected final KryosMascotas plugin;
    protected final PetManager pm;
    protected final Player player;
    protected Inventory inv;

    protected MenuBase(KryosMascotas plugin, PetManager pm, Player player) {
        this.plugin = plugin;
        this.pm = pm;
        this.player = player;
    }

    protected void create(int size, String title) {
        inv = Bukkit.createInventory(this, size, plugin.color(title));
    }

    public abstract void build();

    public abstract void click(int slot, ClickType type);

    public void open() {
        build();
        player.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    // ----------------------------------------------------------------- utilidades

    protected String title(String key, String def, String pet, int page, int pages) {
        return plugin.getConfig().getString(key, def)
                .replace("%mascota%", pet)
                .replace("%pagina%", String.valueOf(page))
                .replace("%paginas%", String.valueOf(pages));
    }

    protected void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    protected void sound(Sound s, float pitch) {
        player.playSound(player.getLocation(), s, 1f, pitch);
    }

    protected void tick() {
        sound(Sound.UI_BUTTON_CLICK, 1f);
    }

    protected void deny() {
        sound(Sound.ENTITY_VILLAGER_NO, 1f);
    }

    protected static Material material(String name, Material def) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    protected Material petIcon(String id) {
        return material(pm.petSec(id).getString("icono", "PAPER"), Material.PAPER);
    }

    protected ItemStack item(Material mat, String name, List<String> lore, boolean glow, int amount) {
        ItemStack it = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plugin.color(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(plugin::color).toList());
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    /** Marco: fondo negro y borde superior/inferior alternando el color de acento. */
    protected void fillAll() {
        Material black = material(plugin.getConfig().getString("menu.relleno", "BLACK_STAINED_GLASS_PANE"),
                Material.BLACK_STAINED_GLASS_PANE);
        Material accent = material(plugin.getConfig().getString("menu.borde", "ORANGE_STAINED_GLASS_PANE"),
                Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack b = item(black, " ", List.of(), false, 1);
        ItemStack a = item(accent, " ", List.of(), false, 1);
        int rows = inv.getSize() / 9;
        for (int i = 0; i < inv.getSize(); i++) {
            int r = i / 9;
            int c = i % 9;
            boolean edge = r == 0 || r == rows - 1;
            inv.setItem(i, edge && c % 2 == 0 ? a : b);
        }
    }

    protected ItemStack closeItem() {
        return item(Material.BARRIER, "<red><bold>Cerrar", List.of("<gray>Cierra este menú."), false, 1);
    }

    protected static String fmt(double v, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    /** Entero si es casi entero, un decimal si no. */
    protected static String pct(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((long) Math.rint(v)) : fmt(v, 1);
    }

    protected static String bar(double ratio) {
        int total = 20;
        int filled = (int) Math.max(0, Math.min(total, Math.round(ratio * total)));
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(total - filled);
    }

    protected static String bullet(String s) {
        return "<dark_gray>» <gray>" + s;
    }

    protected static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    protected static String effectName(String key) {
        return switch (key.trim().toUpperCase(Locale.ROOT)) {
            case "SPEED" -> "Velocidad";
            case "STRENGTH" -> "Fuerza";
            case "RESISTANCE" -> "Resistencia";
            case "JUMP_BOOST" -> "Salto mejorado";
            case "NIGHT_VISION" -> "Visión nocturna";
            case "LUCK" -> "Suerte";
            case "HASTE" -> "Prisa";
            case "HEALTH_BOOST" -> "Salud mejorada";
            case "FIRE_RESISTANCE" -> "Resistencia al fuego";
            case "REGENERATION" -> "Regeneración";
            case "ABSORPTION" -> "Absorción";
            case "WATER_BREATHING" -> "Respiración acuática";
            case "SLOW_FALLING" -> "Caída lenta";
            case "DOLPHINS_GRACE" -> "Gracia del delfín";
            case "CONDUIT_POWER" -> "Poder del canal";
            case "SATURATION" -> "Saturación";
            default -> {
                String s = key.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
                yield s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }

    // ------------------------------------------------------------------- tooltips

    protected String crianzaLabel(String id, PlayerPets.Progress pr) {
        return switch (pm.crianza(id)) {
            case "SIEMPRE" -> "<aqua>Siempre bebé";
            case "OPCIONAL" -> pr == null ? "<aqua>Bebé o adulta a tu gusto"
                    : pr.baby ? "<aqua>Bebé <gray>(puedes hacerla adulta)" : "<aqua>Adulta <gray>(puedes hacerla bebé)";
            default -> "<gray>Tamaño único";
        };
    }

    /** Beneficios actuales de la mascota (sección "Efectos:" del tooltip). */
    protected List<String> effectLines(String id, int level) {
        ConfigurationSection sec = pm.petSec(id);
        List<String> out = new ArrayList<>();
        double bonus = pm.bonusPercent(id, level);
        if (bonus > 0) out.add(bullet("Daño aumentado en <green>+" + pct(bonus) + "%"));
        for (String s : sec.getStringList("efectos")) {
            String[] p = s.split(":");
            if (p.length < 3 || level < PetManager.parseInt(p[2], 1)) continue;
            out.add(bullet("Te da <green>" + effectName(p[0]) + " " + roman(PetManager.parseInt(p[1], 0) + 1)));
        }
        if (pm.hatLevel() > 1 && level >= pm.hatLevel()) out.add(bullet("Puedes usarla de <green>sombrero"));
        if (pm.rideLevel() > 1 && level >= pm.rideLevel()) out.add(bullet("Puedes <green>montarla"));
        if (out.isEmpty()) out.add(bullet("Ninguno todavía"));
        return out;
    }

    protected List<String> progressLines(PlayerPets.Progress pr) {
        List<String> l = new ArrayList<>();
        if (pr.level >= pm.maxLevel()) {
            l.add("<dark_gray>» <gold>Nivel máximo alcanzado");
            return l;
        }
        double need = pm.xpNeeded(pr.level);
        l.add("<dark_gray>» <yellow>" + fmt(pr.xp / need * 100, 2) + "%");
        l.add("<dark_gray>» <yellow>" + (int) pr.xp + "/" + (int) need + " XP");
        l.add(bar(pr.xp / need));
        return l;
    }

    /** Tooltip completo de una mascota (sin el nombre, que va aparte). */
    protected List<String> petTooltip(String id, PlayerPets pp, List<String> footer) {
        ConfigurationSection sec = pm.petSec(id);
        List<String> l = new ArrayList<>();
        l.add("<gray><italic>" + sec.getString("descripcion", ""));
        l.add(SEP);
        if (!pp.unlocked.contains(id)) {
            l.add("<white>Daño base: <red>" + fmt(pm.damage(id, 1), 1));
            l.add("<white>Edad: " + crianzaLabel(id, null));
            l.add("");
            double price = pm.price(id);
            l.add("<white>Precio: " + (price > 0 ? "<gold>" + pm.money(price) : "<green>Gratis"));
            if (price > 0 && pm.hasEconomy()) l.add("<white>Tu dinero: <yellow>" + pm.money(pm.balance(player)));
        } else {
            PlayerPets.Progress pr = pp.get(id);
            l.add("<white>Daño: <red>" + fmt(pm.damage(id, pr.level), 1));
            l.add("<white>Edad: " + crianzaLabel(id, pr));
            l.add("");
            l.add("<white>Efectos:");
            l.addAll(effectLines(id, pr.level));
            l.add("");
            l.add("<white>Progreso:");
            l.addAll(progressLines(pr));
        }
        if (!footer.isEmpty()) {
            l.add(SEP);
            l.addAll(footer);
        }
        return l;
    }

    protected List<String> nextRewards(String petId, int level, int limit) {
        List<String> lore = new ArrayList<>();
        ConfigurationSection rs = plugin.getConfig().getConfigurationSection("recompensas");
        List<Integer> levels = new ArrayList<>();
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                int lv = PetManager.parseInt(k, 0);
                String only = rs.getString(k + ".solo-mascota");
                if (lv > level && (only == null || only.equalsIgnoreCase(petId))) levels.add(lv);
            }
        }
        levels.sort(Integer::compare);
        int shown = 0;
        for (int lv : levels) {
            if (shown++ >= limit) break;
            lore.add("<yellow>Nv." + lv + " <dark_gray>» <white>" + rs.getString(lv + ".descripcion", "Recompensa"));
        }
        if (lore.isEmpty()) lore.add("<gray>¡Ya tienes todas las recompensas!");
        return lore;
    }

    protected ItemStack particleItem(PlayerPets pp, String petId) {
        if (petId == null || pm.petSec(petId) == null) {
            return item(Material.FIREWORK_STAR, "<light_purple><bold>Estela de partículas",
                    List.of("<gray>Invoca una mascota para elegir su estela."), false, 1);
        }
        PlayerPets.Progress pr = pp.get(petId);
        String actual = switch (pr.particle.toLowerCase(Locale.ROOT)) {
            case "auto" -> "automática (la mejor desbloqueada)";
            case "none" -> "ninguna";
            default -> pr.particle.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
        int total = pm.petSec(petId).getMapList("estela").size();
        return item(Material.FIREWORK_STAR, "<light_purple><bold>Estela de partículas", List.of(
                "<gray>Un rastro de partículas que deja",
                "<gray>tu mascota al caminar.",
                SEP,
                "<white>Actual: <aqua>" + actual,
                "<white>Desbloqueadas: <yellow>" + pm.unlockedParticles(petId, pr.level).size() + "<gray>/" + total,
                "",
                "<yellow>¡Clic para cambiar de estela!"), false, 1);
    }
}
