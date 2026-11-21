package com.kryos.mascotas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PetManager {

    public enum BuyResult { OK, NO_ECONOMY, NO_MONEY, OWNED, UNKNOWN }

    private final KryosMascotas plugin;
    private final NamespacedKey petKey;
    private final File file;

    private final Map<UUID, PlayerPets> data = new HashMap<>();
    private final Map<UUID, Mob> active = new HashMap<>();
    private final Map<UUID, LivingEntity> targets = new HashMap<>();
    private final Map<UUID, UUID> lastHit = new HashMap<>();
    private final Map<UUID, Long> attackCd = new HashMap<>();
    private final Map<String, Long> cuddleCd = new HashMap<>();
    private final Map<UUID, String> renaming = new HashMap<>();
    /** Dueños que están montando a su mascota -> escala original de la mascota. */
    private final Map<UUID, Double> riding = new HashMap<>();
    /** Dueños que llevan a su mascota de sombrero. */
    private final Set<UUID> hat = new HashSet<>();
    private final Set<UUID> staying = new HashSet<>();
    private final Set<UUID> resting = new HashSet<>();
    private final Map<UUID, Long> busy = new HashMap<>();
    private int rainbow = 0;
    private VaultHook vault;
    private int tick = 0;

    public PetManager(KryosMascotas plugin) {
        this.plugin = plugin;
        this.petKey = new NamespacedKey(plugin, "mascota_duena");
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ config

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public ConfigurationSection petSec(String id) {
        return cfg().getConfigurationSection("mascotas." + id);
    }

    public Set<String> petIds() {
        ConfigurationSection s = cfg().getConfigurationSection("mascotas");
        return s == null ? Set.of() : s.getKeys(false);
    }

    public int maxLevel() {
        return cfg().getInt("maximo-nivel", 50);
    }

    public int rideLevel() {
        return cfg().getInt("montar.nivel", 10);
    }

    public int hatLevel() {
        return cfg().getInt("sombrero.nivel", 5);
    }

    public double xpNeeded(int level) {
        return cfg().getDouble("xp.base", 50) + level * cfg().getDouble("xp.por-nivel", 30);
    }

    /** Daño = base de la mascota + un pequeño % por nivel (configurable, 0 = daño fijo). */
    public double damage(String petId, int level) {
        double base = petSec(petId).getDouble("danio-base", 4);
        return base * (1 + (level - 1) * cfg().getDouble("mascota.bonus-danio-por-nivel", 1.5) / 100.0);
    }

    public double bonusPercent(String petId, int level) {
        return (level - 1) * cfg().getDouble("mascota.bonus-danio-por-nivel", 1.5);
    }

    public double price(String petId) {
        return petSec(petId).getDouble("precio", 0);
    }

    public String money(double v) {
        return cfg().getString("moneda", "$") + String.format(Locale.US, "%,.0f", v);
    }

    /** OPCIONAL (bebé o adulta a elección), SIEMPRE (siempre bebé) o NINGUNA (no tiene edad). */
    public String crianza(String petId) {
        return petSec(petId).getString("crianza", "OPCIONAL").toUpperCase(Locale.ROOT);
    }

    public boolean isBaby(String petId, PlayerPets.Progress pr) {
        String c = crianza(petId);
        return c.equals("SIEMPRE") || (c.equals("OPCIONAL") && pr.baby);
    }

    public static int num(Map<?, ?> m, String k, int def) {
        Object o = m.get(k);
        return o instanceof Number n ? n.intValue() : def;
    }

    public static double dbl(Map<?, ?> m, String k, double def) {
        Object o = m.get(k);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ----------------------------------------------------------------- economía

    private VaultHook vault() {
        if (vault == null) vault = VaultHook.create();
        return vault;
    }

    public boolean hasEconomy() {
        return vault() != null;
    }

    public double balance(Player p) {
        VaultHook v = vault();
        return v == null ? 0 : v.balance(p);
    }

    public BuyResult buy(Player p, String petId) {
        if (petSec(petId) == null) return BuyResult.UNKNOWN;
        PlayerPets pp = data(p.getUniqueId());
        if (pp.unlocked.contains(petId)) return BuyResult.OWNED;
        double price = price(petId);
        if (price > 0) {
            VaultHook v = vault();
            if (v == null) return BuyResult.NO_ECONOMY;
            if (!v.has(p, price) || !v.withdraw(p, price)) return BuyResult.NO_MONEY;
        }
        pp.unlocked.add(petId);
        save();
        return BuyResult.OK;
    }

    // -------------------------------------------------------------------- data

    public void load() {
        data.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection jugadores = y.getConfigurationSection("jugadores");
        if (jugadores == null) return;
        for (String key : jugadores.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection s = jugadores.getConfigurationSection(key);
            if (s == null) continue;
            PlayerPets pp = new PlayerPets();
            pp.unlocked.addAll(s.getStringList("desbloqueadas"));
            pp.claimedGlobal.addAll(s.getIntegerList("reclamados-globales"));
            pp.active = s.getString("activa");
            ConfigurationSection ms = s.getConfigurationSection("mascotas");
            if (ms != null) {
                for (String pid : ms.getKeys(false)) {
                    PlayerPets.Progress pr = new PlayerPets.Progress();
                    pr.level = ms.getInt(pid + ".nivel", 1);
                    pr.xp = ms.getDouble(pid + ".xp", 0);
                    pr.particle = ms.getString(pid + ".estela", "auto");
                    pr.claimed.addAll(ms.getIntegerList(pid + ".reclamados"));
                    pr.kills = ms.getInt(pid + ".kills", 0);
                    pr.nickname = ms.getString(pid + ".apodo");
                    pr.baby = ms.getBoolean(pid + ".bebe", false);
                    ConfigurationSection cs = ms.getConfigurationSection(pid + ".cosmeticos");
                    if (cs != null) {
                        for (String ck : cs.getKeys(false)) pr.cosmetics.put(ck, cs.getString(ck, ""));
                    }
                    pp.progress.put(pid, pr);
                }
            }
            data.put(id, pp);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerPets> en : data.entrySet()) {
            String base = "jugadores." + en.getKey();
            PlayerPets pp = en.getValue();
            y.set(base + ".desbloqueadas", new ArrayList<>(pp.unlocked));
            y.set(base + ".reclamados-globales", new ArrayList<>(pp.claimedGlobal));
            y.set(base + ".activa", pp.active);
            for (Map.Entry<String, PlayerPets.Progress> pe : pp.progress.entrySet()) {
                String pb = base + ".mascotas." + pe.getKey();
                PlayerPets.Progress pr = pe.getValue();
                y.set(pb + ".nivel", pr.level);
                y.set(pb + ".xp", pr.xp);
                y.set(pb + ".estela", pr.particle);
                y.set(pb + ".reclamados", new ArrayList<>(pr.claimed));
                y.set(pb + ".kills", pr.kills);
                y.set(pb + ".apodo", pr.nickname);
                y.set(pb + ".bebe", pr.baby);
                for (Map.Entry<String, String> ce : pr.cosmetics.entrySet()) {
                    y.set(pb + ".cosmeticos." + ce.getKey(), ce.getValue());
                }
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar data.yml: " + ex.getMessage());
        }
    }

    public PlayerPets data(UUID id) {
        PlayerPets pp = data.computeIfAbsent(id, k -> new PlayerPets());
        for (String pid : petIds()) {
            if (petSec(pid).getBoolean("por-defecto", false)) pp.unlocked.add(pid);
        }
        return pp;
    }

    // ------------------------------------------------------------------ tareas

    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPets, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrails, 4L, 4L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRiding, 1L, 1L);
        long mins = Math.max(1, cfg().getInt("autoguardado-minutos", 5));
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, mins * 1200L, mins * 1200L);
    }

    public void cleanup() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (isPet(e)) e.remove();
            }
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) despawn(id);
        save();
    }

    private void tickPets() {
        tick++;
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player owner = Bukkit.getPlayer(id);
            if (owner == null || !owner.isOnline()) {
                despawn(id);
                continue;
            }
            PlayerPets pp = data(id);
            String petId = pp.active;
            if (petId == null || petSec(petId) == null) {
                despawn(id);
                continue;
            }
            Mob pet = active.get(id);
            if (pet == null || !pet.isValid()) {
                spawn(owner, petId, true);
                continue;
            }
            if (owner.isDead()) continue;
            PlayerPets.Progress pr = pp.get(petId);
            if (!riding.containsKey(id)) {
                if (hat.contains(id)) {
                    if (!owner.getPassengers().contains(pet)) owner.addPassenger(pet);
                } else if (busy.getOrDefault(id, 0L) > System.currentTimeMillis()) {
                    // está bailando
                } else if (resting.contains(id)) {
                    if (!pet.getWorld().equals(owner.getWorld())
                            || pet.getLocation().distanceSquared(owner.getLocation()) > 64) {
                        setRest(pet, false);
                        resting.remove(id);
                    }
                } else if (staying.contains(id)) {
                    if (!pet.getWorld().equals(owner.getWorld())
                            || pet.getLocation().distanceSquared(owner.getLocation()) > 1600) {
                        staying.remove(id);
                    }
                } else {
                    followAndFight(owner, pet, petId, pr);
                }
            }
            if (tick % 10 == 0) applyPassives(owner, petId, pr);
        }
        if (lastHit.size() > 500) lastHit.clear();
    }

    private void tickTrails() {
        rainbow++;
        for (Map.Entry<UUID, Mob> en : active.entrySet()) {
            Mob pet = en.getValue();
            if (!pet.isValid()) continue;
            PlayerPets pp = data(en.getKey());
            if (pp.active == null || petSec(pp.active) == null) continue;
            if (pet instanceof Sheep sh && "ARCOIRIS".equals(pp.get(pp.active).cosmetics.get("lana"))) {
                DyeColor[] dyes = DyeColor.values();
                sh.setColor(dyes[(rainbow / 3) % dyes.length]);
            }
            Particle p = selectedParticle(pp.active, pp.get(pp.active));
            if (p == null) continue;
            particle(pet.getWorld(), p, pet.getLocation().add(0, pet.getHeight() * 0.5, 0), 2, 0.3, 0.3, 0.3, 0.01);
        }
    }

    private void particle(World w, Particle p, Location l, int count, double dx, double dy, double dz, double extra) {
        try {
            w.spawnParticle(p, l, count, dx, dy, dz, extra);
        } catch (IllegalArgumentException ignored) {
            // partícula que necesita datos extra: se ignora
        }
    }

    // ------------------------------------------------------------- invocación

    public boolean isPet(Entity e) {
        return e.getPersistentDataContainer().has(petKey, PersistentDataType.STRING);
    }

    public UUID petOwner(Entity e) {
        String s = e.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean hasActive(Player p) {
        Mob m = active.get(p.getUniqueId());
        return m != null && m.isValid();
    }

    public Component petName(String petId, PlayerPets.Progress pr) {
        return plugin.color(displayName(petId, pr) + " <dark_gray>[<yellow>Nv." + pr.level + "<dark_gray>]");
    }

    public String displayName(String petId, PlayerPets.Progress pr) {
        if (pr != null && pr.nickname != null && !pr.nickname.isBlank()) {
            String c = pr.cosmetics.getOrDefault("color-nombre", "yellow");
            return "<" + c + ">" + MiniMessage.miniMessage().escapeTags(pr.nickname);
        }
        return petSec(petId).getString("nombre", petId);
    }

    public boolean spawn(Player owner, String petId, boolean silent) {
        ConfigurationSection sec = petSec(petId);
        PlayerPets pp = data(owner.getUniqueId());
        if (sec == null || !pp.unlocked.contains(petId)) return false;
        despawn(owner.getUniqueId());

        EntityType type;
        try {
            type = EntityType.valueOf(sec.getString("entidad", "WOLF").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Entidad inválida en la mascota " + petId);
            return false;
        }
        Location loc = owner.getLocation();
        Entity ent = loc.getWorld().spawnEntity(loc, type);
        if (!(ent instanceof Mob mob)) {
            ent.remove();
            plugin.getLogger().warning("La entidad de " + petId + " no es un Mob, no se puede usar.");
            return false;
        }
        PlayerPets.Progress pr = pp.get(petId);
        mob.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        // Sin IA propia: la mascota solo hace lo que el plugin le ordena (no huye, no vaga, no se esconde).
        Bukkit.getMobGoals().removeAllGoals(mob);
        mob.setPersistent(false);
        mob.setInvulnerable(true);
        mob.setCanPickupItems(false);
        mob.setCollidable(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCustomNameVisible(true);
        mob.customName(petName(petId, pr));
        mob.setTarget(null);
        applyAge(mob, petId, pr);
        if (mob instanceof Tameable t) {
            t.setTamed(true);
            t.setOwner(owner);
        }
        applyCosmetics(mob, pr);
        if (mob instanceof Chicken c) c.setEggLayTime(Integer.MAX_VALUE);
        if (mob instanceof Wolf w) w.setAngry(false);

        active.put(owner.getUniqueId(), mob);
        pp.active = petId;
        particle(mob.getWorld(), Particle.CLOUD, mob.getLocation().add(0, 0.5, 0), 20, 0.4, 0.4, 0.4, 0.02);
        if (!silent) plugin.msg(owner, "<green>Has invocado a tu mascota: " + displayName(petId, pr));
        return true;
    }

    private void applyAge(Mob mob, String petId, PlayerPets.Progress pr) {
        if (mob instanceof Ageable a) {
            if (isBaby(petId, pr)) a.setBaby();
            else a.setAdult();
            a.setAgeLock(true);
        }
    }

    public void despawn(UUID owner) {
        riding.remove(owner);
        hat.remove(owner);
        staying.remove(owner);
        resting.remove(owner);
        busy.remove(owner);
        Mob m = active.remove(owner);
        if (m != null) m.remove();
        targets.remove(owner);
    }

    public void dismiss(Player p) {
        PlayerPets pp = data(p.getUniqueId());
        despawn(p.getUniqueId());
        pp.active = null;
        plugin.msg(p, "<gray>Has guardado a tu mascota.");
    }

    public void updateName(UUID owner) {
        Mob m = active.get(owner);
        PlayerPets pp = data(owner);
        if (m != null && m.isValid() && pp.active != null && petSec(pp.active) != null) {
            m.customName(petName(pp.active, pp.get(pp.active)));
        }
    }

    // ------------------------------------------------------------ bebé / adulta

    /** Gratis y sin nivel. Solo mascotas con crianza OPCIONAL. */
    public boolean toggleBaby(Player p, String petId) {
        if (petSec(petId) == null || !crianza(petId).equals("OPCIONAL")) return false;
        PlayerPets pp = data(p.getUniqueId());
        PlayerPets.Progress pr = pp.get(petId);
        pr.baby = !pr.baby;
        Mob m = active.get(p.getUniqueId());
        if (m != null && m.isValid() && petId.equals(pp.active)) applyAge(m, petId, pr);
        plugin.msg(p, pr.baby ? "<green>Tu mascota ahora es un bebé." : "<green>Tu mascota ahora es adulta.");
        return true;
    }

    // ------------------------------------------------------------------ montar

    public boolean isRiding(UUID id) {
        return riding.containsKey(id);
    }

    public boolean isHat(UUID id) {
        return hat.contains(id);
    }

    public void startRide(Player owner) {
        UUID id = owner.getUniqueId();
        PlayerPets pp = data(id);
        Mob pet = active.get(id);
        if (pp.active == null || pet == null || !pet.isValid()) {
            plugin.msg(owner, "<red>Invoca primero a tu mascota.");
            return;
        }
        if (pp.get(pp.active).level < rideLevel()) {
            plugin.msg(owner, "<red>Podrás montarla al llegar al nivel <yellow>" + rideLevel() + "<red>.");
            return;
        }
        clearStates(id, pet);
        if (riding.containsKey(id)) return;
        if (owner.isInsideVehicle()) {
            plugin.msg(owner, "<red>Bájate de tu vehículo primero.");
            return;
        }
        if (hat.remove(id)) {
            owner.removePassenger(pet);
            pet.setCustomNameVisible(!"OCULTO".equals(pp.get(pp.active).cosmetics.get("nombre")));
        }
        AttributeInstance sc = pet.getAttribute(Attribute.SCALE);
        double orig = sc == null ? 1.0 : sc.getBaseValue();
        double mount = cfg().getDouble("montar.escala", 2.0);
        if (sc != null && mount > 0) sc.setBaseValue(mount);
        targets.remove(id);
        pet.getPathfinder().stopPathfinding();
        if (pet.addPassenger(owner)) {
            riding.put(id, orig);
            plugin.msg(owner, "<green>¡A montar! <gray>WASD para moverte, espacio para saltar, Shift para bajar.");
        } else if (sc != null) {
            sc.setBaseValue(orig);
        }
    }

    public void stopRide(UUID id) {
        Double orig = riding.remove(id);
        if (orig == null) return;
        Mob pet = active.get(id);
        if (pet != null && pet.isValid()) {
            AttributeInstance sc = pet.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(orig);
            Player p = Bukkit.getPlayer(id);
            if (p != null) pet.removePassenger(p);
        }
    }

    private void tickRiding() {
        for (UUID id : new ArrayList<>(riding.keySet())) {
            Player owner = Bukkit.getPlayer(id);
            Mob pet = active.get(id);
            if (owner == null || pet == null || !pet.isValid() || !pet.getPassengers().contains(owner)) {
                stopRide(id);
                continue;
            }
            Input in = owner.getCurrentInput();
            double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
            double r = (in.isRight() ? 1 : 0) - (in.isLeft() ? 1 : 0);
            double yaw = Math.toRadians(owner.getLocation().getYaw());
            double dx = -Math.sin(yaw) * f + -Math.cos(yaw) * r;
            double dz = Math.cos(yaw) * f + -Math.sin(yaw) * r;
            double len = Math.sqrt(dx * dx + dz * dz);
            Vector vel = pet.getVelocity();
            if (len > 0.001) {
                dx /= len;
                dz /= len;
                double speed = cfg().getDouble("montar.velocidad", 0.22) * (in.isSprint() ? 1.4 : 1.0);
                vel.setX(dx * speed);
                vel.setZ(dz * speed);
                pet.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0f);
                if (pet.isOnGround()) {
                    double reach = pet.getWidth() / 2 + 0.5;
                    Block b = pet.getLocation().add(dx * reach, 0.1, dz * reach).getBlock();
                    if (!b.isPassable() && b.getRelative(BlockFace.UP).isPassable()
                            && b.getRelative(0, 2, 0).isPassable()) {
                        vel.setY(0.5);
                    }
                }
            } else {
                vel.setX(0);
                vel.setZ(0);
            }
            if (in.isJump() && pet.isOnGround()) vel.setY(0.55);
            pet.setVelocity(vel);
            pet.getPathfinder().stopPathfinding();
        }
    }

    // ---------------------------------------------------------------- sombrero

    public void toggleHat(Player owner) {
        UUID id = owner.getUniqueId();
        PlayerPets pp = data(id);
        Mob pet = active.get(id);
        if (pp.active == null || pet == null || !pet.isValid()) {
            plugin.msg(owner, "<red>Invoca primero a tu mascota.");
            return;
        }
        if (pp.get(pp.active).level < hatLevel()) {
            plugin.msg(owner, "<red>Podrás usarla de sombrero al llegar al nivel <yellow>" + hatLevel() + "<red>.");
            return;
        }
        if (hat.remove(id)) {
            owner.removePassenger(pet);
            pet.setCustomNameVisible(!"OCULTO".equals(pp.get(pp.active).cosmetics.get("nombre")));
            plugin.msg(owner, "<gray>Tu mascota se bajó de tu cabeza.");
            return;
        }
        clearStates(id, pet);
        if (riding.containsKey(id)) stopRide(id);
        targets.remove(id);
        pet.getPathfinder().stopPathfinding();
        if (owner.addPassenger(pet)) {
            hat.add(id);
            pet.setCustomNameVisible(false);
            plugin.msg(owner, "<green>¡Tu mascota ahora es tu sombrero!");
        }
    }

    // ---------------------------------------------------------------- acariciar

    /** Minutos que faltan para poder acariciar de nuevo (0 = disponible). */
    public long cuddleMinutesLeft(UUID owner, String petId) {
        long left = cuddleCd.getOrDefault(owner + ":" + petId, 0L) - System.currentTimeMillis();
        return left <= 0 ? 0 : (left / 60000L) + 1;
    }

    public void cuddle(Player p) {
        UUID id = p.getUniqueId();
        PlayerPets pp = data(id);
        Mob pet = active.get(id);
        if (pp.active == null || pet == null || !pet.isValid()) {
            plugin.msg(p, "<red>Invoca primero a tu mascota.");
            return;
        }
        long wait = cuddleMinutesLeft(id, pp.active);
        if (wait > 0) {
            plugin.msg(p, "<gray>Tu mascota ya está feliz. Vuelve en <white>" + wait + " min<gray>.");
            return;
        }
        long mins = Math.max(1, cfg().getInt("acariciar.enfriamiento-minutos", 30));
        cuddleCd.put(id + ":" + pp.active, System.currentTimeMillis() + mins * 60000L);
        particle(pet.getWorld(), Particle.HEART, pet.getLocation().add(0, pet.getHeight() + 0.3, 0), 8, 0.4, 0.3, 0.4, 0);
        pet.setVelocity(pet.getVelocity().setY(0.4));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        double xp = cfg().getDouble("acariciar.xp", 15);
        addXp(p, pp.active, xp);
        plugin.msg(p, "<light_purple>Acaricias a tu mascota. <gray>(+" + (int) xp + " XP)");
    }

    // ------------------------------------------------------------------ estados

    public boolean isResting(UUID id) {
        return resting.contains(id);
    }

    public boolean isStaying(UUID id) {
        return staying.contains(id);
    }

    private void clearStates(UUID id, Mob pet) {
        if (resting.remove(id)) setRest(pet, false);
        staying.remove(id);
        busy.remove(id);
    }

    private void setRest(Mob m, boolean on) {
        if (m instanceof Fox f) f.setSleeping(on);
        else if (m instanceof Sittable s) s.setSitting(on);
    }

    private Mob livePet(Player p) {
        PlayerPets pp = data(p.getUniqueId());
        Mob pet = active.get(p.getUniqueId());
        if (pp.active == null || pet == null || !pet.isValid()) {
            plugin.msg(p, "<red>Invoca primero a tu mascota.");
            return null;
        }
        return pet;
    }

    private boolean busyWithOwner(Player p) {
        if (riding.containsKey(p.getUniqueId()) || hat.contains(p.getUniqueId())) {
            plugin.msg(p, "<red>Bájala primero (montura o sombrero).");
            return true;
        }
        return false;
    }

    public void toggleRest(Player p) {
        Mob pet = livePet(p);
        if (pet == null || busyWithOwner(p)) return;
        UUID id = p.getUniqueId();
        if (!(pet instanceof Sittable) && !(pet instanceof Fox)) {
            plugin.msg(p, "<red>Esta mascota no puede descansar.");
            return;
        }
        if (resting.remove(id)) {
            setRest(pet, false);
            plugin.msg(p, "<gray>Tu mascota se levantó.");
            return;
        }
        staying.remove(id);
        busy.remove(id);
        pet.getPathfinder().stopPathfinding();
        setRest(pet, true);
        resting.add(id);
        plugin.msg(p, "<green>Tu mascota se puso a descansar.");
    }

    public void toggleStay(Player p) {
        Mob pet = livePet(p);
        if (pet == null || busyWithOwner(p)) return;
        UUID id = p.getUniqueId();
        if (staying.remove(id)) {
            plugin.msg(p, "<green>Tu mascota vuelve a seguirte.");
            return;
        }
        if (resting.remove(id)) setRest(pet, false);
        busy.remove(id);
        targets.remove(id);
        pet.getPathfinder().stopPathfinding();
        staying.add(id);
        plugin.msg(p, "<green>Tu mascota se queda aquí. <gray>Vuelve a pulsar para que te siga.");
    }

    public void callPet(Player p) {
        Mob pet = livePet(p);
        if (pet == null || busyWithOwner(p)) return;
        clearStates(p.getUniqueId(), pet);
        pet.teleport(p.getLocation());
        particle(pet.getWorld(), Particle.CLOUD, pet.getLocation().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.02);
        plugin.msg(p, "<green>Tu mascota llegó a tu lado.");
    }

    public void dance(Player p) {
        Mob pet = livePet(p);
        if (pet == null || busyWithOwner(p)) return;
        UUID id = p.getUniqueId();
        clearStates(id, pet);
        pet.getPathfinder().stopPathfinding();
        busy.put(id, System.currentTimeMillis() + 3600L);
        int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!pet.isValid() || n[0] >= 32) {
                busy.remove(id);
                task.cancel();
                return;
            }
            Location l = pet.getLocation();
            pet.setRotation(l.getYaw() + 45f, 0f);
            if (n[0] % 6 == 0 && pet.isOnGround()) pet.setVelocity(pet.getVelocity().setY(0.38));
            if (n[0] % 4 == 0) {
                particle(pet.getWorld(), Particle.NOTE, l.clone().add(0, pet.getHeight() + 0.3, 0), 1, 0.3, 0.2, 0.3, 1);
                pet.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 0.7f + (n[0] % 12) / 12f);
            }
            n[0]++;
        }, 0L, 2L);
        plugin.msg(p, "<light_purple>¡Tu mascota baila!");
    }

    public void trick(Player p, Actions.Trick t) {
        Mob pet = livePet(p);
        if (pet == null) return;
        boolean baby = pet instanceof Ageable a && a.isBaby();
        Location l = pet.getLocation();
        pet.getWorld().playSound(l, t.sound(), 1f, baby ? 1.4f : 1.0f);
        particle(pet.getWorld(), t.particle(), l.clone().add(0, pet.getHeight() + 0.3, 0), 6, 0.3, 0.2, 0.3, 0.05);
        UUID id = p.getUniqueId();
        if (t.jump() > 0 && pet.isOnGround() && !riding.containsKey(id) && !hat.contains(id)) {
            pet.setVelocity(pet.getVelocity().setY(t.jump()));
        }
    }

    // --------------------------------------------------------------- cosméticos

    private static String typeName(Mob m) {
        return m.getType().getKey().getKey().toUpperCase(Locale.ROOT);
    }

    public String cosmetic(PlayerPets.Progress pr, Cosmetics.Option o) {
        return pr.cosmetics.getOrDefault(o.key(), o.choices().get(0).key());
    }

    public void applyCosmetics(Mob mob, PlayerPets.Progress pr) {
        for (Cosmetics.Option o : Cosmetics.forType(typeName(mob))) {
            try {
                o.applier().apply(mob, cosmetic(pr, o));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("No se pudo aplicar el cosmético " + o.key() + ": " + ex.getMessage());
            }
        }
    }

    public void setCosmetic(Player p, String petId, String optKey, String choiceKey) {
        PlayerPets pp = data(p.getUniqueId());
        pp.get(petId).cosmetics.put(optKey, choiceKey);
        Mob m = active.get(p.getUniqueId());
        if (m != null && m.isValid() && petId.equals(pp.active)) {
            for (Cosmetics.Option o : Cosmetics.forType(typeName(m))) {
                if (!o.key().equals(optKey)) continue;
                try {
                    o.applier().apply(m, choiceKey);
                } catch (RuntimeException ignored) {
                    // cosmético no compatible con esta versión
                }
            }
            updateName(p.getUniqueId());
        }
    }

    // ----------------------------------------------------------------- combate

    public void setTarget(Player owner, LivingEntity target) {
        UUID id = owner.getUniqueId();
        if (!active.containsKey(id) || riding.containsKey(id) || hat.contains(id)) return;
        if (target.equals(owner) || isPet(target) || !target.isValid()) return;
        if (target instanceof Player && !cfg().getBoolean("mascota.atacar-jugadores", false)) return;
        targets.put(id, target);
    }

    public UUID takeLastHit(UUID target) {
        return lastHit.remove(target);
    }

    private LivingEntity findTarget(Player owner) {
        UUID id = owner.getUniqueId();
        LivingEntity t = targets.get(id);
        double range = cfg().getDouble("mascota.rango-ataque", 12);
        if (t != null && (!t.isValid() || t.isDead() || !t.getWorld().equals(owner.getWorld())
                || t.getLocation().distanceSquared(owner.getLocation()) > range * range)) {
            targets.remove(id);
            t = null;
        }
        if (t == null && cfg().getBoolean("mascota.auto-defender", true)) {
            double r = cfg().getDouble("mascota.rango-defensa", 8);
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (Entity e : owner.getNearbyEntities(r, r, r)) {
                if (!(e instanceof Enemy) || !(e instanceof LivingEntity le)) continue;
                if (isPet(e) || !e.isValid()) continue;
                double d = e.getLocation().distanceSquared(owner.getLocation());
                if (d < bd) {
                    bd = d;
                    best = le;
                }
            }
            t = best;
            if (t != null) targets.put(id, t);
        }
        return t;
    }

    private void followAndFight(Player owner, Mob pet, String petId, PlayerPets.Progress pr) {
        Location ol = owner.getLocation();
        double tp = cfg().getDouble("mascota.distancia-teletransporte", 20);
        if (!pet.getWorld().equals(ol.getWorld()) || pet.getLocation().distanceSquared(ol) > tp * tp) {
            pet.teleport(ol);
            targets.remove(owner.getUniqueId());
            return;
        }
        pet.setTarget(null);
        double speed = petSec(petId).getDouble("velocidad", cfg().getDouble("mascota.velocidad", 1.3));
        LivingEntity target = findTarget(owner);
        if (target != null) {
            pet.getPathfinder().moveTo(target.getLocation(), speed);
            if (pet.getBoundingBox().expand(1.0).overlaps(target.getBoundingBox())) {
                attack(owner, pet, petId, pr, target);
            }
        } else {
            double follow = cfg().getDouble("mascota.distancia-seguir", 3.0);
            if (pet.getLocation().distanceSquared(ol) > follow * follow) {
                pet.getPathfinder().moveTo(ol, speed);
            } else {
                pet.getPathfinder().stopPathfinding();
            }
        }
    }

    private void attack(Player owner, Mob pet, String petId, PlayerPets.Progress pr, LivingEntity target) {
        long now = System.currentTimeMillis();
        long cd = (long) (cfg().getDouble("mascota.enfriamiento-ataque", 1.0) * 1000);
        if (now - attackCd.getOrDefault(owner.getUniqueId(), 0L) < cd) return;
        attackCd.put(owner.getUniqueId(), now);

        lastHit.put(target.getUniqueId(), owner.getUniqueId());
        pet.swingMainHand();
        target.damage(damage(petId, pr.level), pet);
    }

    private void applyPassives(Player owner, String petId, PlayerPets.Progress pr) {
        for (String s : petSec(petId).getStringList("efectos")) {
            String[] p = s.split(":");
            if (p.length < 3) continue;
            if (pr.level < parseInt(p[2], 1)) continue;
            PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(p[0].trim().toLowerCase(Locale.ROOT)));
            if (t == null) continue;
            owner.addPotionEffect(new PotionEffect(t, 400, parseInt(p[1], 0), true, false, true));
        }
    }

    // ------------------------------------------------------------------ estelas

    public List<String> unlockedParticles(String petId, int level) {
        List<String> out = new ArrayList<>();
        for (Map<?, ?> m : petSec(petId).getMapList("estela")) {
            if (level >= num(m, "nivel", 1)) out.add(String.valueOf(m.get("particula")).toUpperCase(Locale.ROOT));
        }
        return out;
    }

    private Particle selectedParticle(String petId, PlayerPets.Progress pr) {
        List<String> unlocked = unlockedParticles(petId, pr.level);
        if (unlocked.isEmpty() || pr.particle.equalsIgnoreCase("none")) return null;
        String name = pr.particle.equalsIgnoreCase("auto") || !unlocked.contains(pr.particle.toUpperCase(Locale.ROOT))
                ? unlocked.get(unlocked.size() - 1)
                : pr.particle.toUpperCase(Locale.ROOT);
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void cycleParticle(Player p, String petId) {
        PlayerPets.Progress pr = data(p.getUniqueId()).get(petId);
        List<String> options = new ArrayList<>();
        options.add("auto");
        options.addAll(unlockedParticles(petId, pr.level));
        options.add("none");
        int idx = options.indexOf(pr.particle.equalsIgnoreCase("auto") || pr.particle.equalsIgnoreCase("none")
                ? pr.particle.toLowerCase(Locale.ROOT) : pr.particle.toUpperCase(Locale.ROOT));
        pr.particle = options.get((idx + 1) % options.size());
    }

    // ---------------------------------------------------------------- renombrar

    public void startRename(Player p, String petId) {
        renaming.put(p.getUniqueId(), petId);
        plugin.msg(p, "<yellow>Escribe en el chat el nuevo nombre de tu mascota <gray>(máx. 16 letras).");
        plugin.msg(p, "<gray>Escribe <white>cancelar<gray> para salir o <white>quitar<gray> para restaurar el nombre original.");
    }

    public boolean isRenaming(UUID id) {
        return renaming.containsKey(id);
    }

    public void cancelRename(UUID id) {
        renaming.remove(id);
    }

    public void handleRename(Player p, String text) {
        String petId = renaming.remove(p.getUniqueId());
        if (petId == null || petSec(petId) == null) return;
        PlayerPets.Progress pr = data(p.getUniqueId()).get(petId);
        String t = text.trim();
        if (t.equalsIgnoreCase("cancelar")) {
            plugin.msg(p, "<gray>Renombrado cancelado.");
            return;
        }
        if (t.equalsIgnoreCase("quitar")) {
            pr.nickname = null;
            plugin.msg(p, "<green>Tu mascota recuperó su nombre original.");
        } else {
            t = t.replaceAll("[<>]", "").trim();
            if (t.length() > 16) t = t.substring(0, 16);
            if (t.isBlank()) {
                plugin.msg(p, "<red>Nombre inválido.");
                return;
            }
            pr.nickname = t;
            plugin.msg(p, "<green>Tu mascota ahora se llama <yellow>" + MiniMessage.miniMessage().escapeTags(t) + "<green>.");
        }
        updateName(p.getUniqueId());
    }

    // ---------------------------------------------------------------- niveles

    public void awardKill(Player owner, LivingEntity dead, boolean byPet) {
        PlayerPets pp = data(owner.getUniqueId());
        if (pp.active == null || !active.containsKey(owner.getUniqueId())) return;
        double xp;
        if (dead instanceof Boss) xp = cfg().getDouble("xp.por-matar-jefe", 500);
        else if (dead instanceof Player) xp = cfg().getDouble("xp.por-matar-jugador", 25);
        else if (dead instanceof Enemy) xp = cfg().getDouble("xp.por-matar-hostil", 10);
        else xp = cfg().getDouble("xp.por-matar-pasivo", 2);
        if (byPet) xp += cfg().getDouble("xp.bonus-ataque-mascota", 5);
        pp.get(pp.active).kills++;
        double gained = xp * cfg().getDouble("xp.multiplicador-global", 1.0);
        addXp(owner, pp.active, xp);
        PlayerPets.Progress pr = pp.get(pp.active);
        if (pr.level < maxLevel()) {
            owner.sendActionBar(plugin.color("<green>+" + String.format(Locale.US, "%.1f", gained)
                    + " XP <gray>» " + displayName(pp.active, pr)));
        }
    }

    public void addXp(Player owner, String petId, double amount) {
        PlayerPets pp = data(owner.getUniqueId());
        if (petSec(petId) == null || !pp.unlocked.contains(petId)) return;
        PlayerPets.Progress pr = pp.get(petId);
        if (pr.level >= maxLevel()) return;
        pr.xp += amount * cfg().getDouble("xp.multiplicador-global", 1.0);
        while (pr.level < maxLevel() && pr.xp >= xpNeeded(pr.level)) {
            pr.xp -= xpNeeded(pr.level);
            pr.level++;
            levelUp(owner, petId, pr);
        }
        if (pr.level >= maxLevel()) pr.xp = 0;
    }

    public void setLevel(Player owner, String petId, int target) {
        PlayerPets pp = data(owner.getUniqueId());
        if (petSec(petId) == null) return;
        pp.unlocked.add(petId);
        PlayerPets.Progress pr = pp.get(petId);
        target = Math.max(1, Math.min(maxLevel(), target));
        pr.xp = 0;
        if (target < pr.level) {
            pr.level = target;
            updateName(owner.getUniqueId());
            return;
        }
        while (pr.level < target) {
            pr.level++;
            levelUp(owner, petId, pr);
        }
    }

    public boolean unlock(Player owner, String petId) {
        if (petSec(petId) == null) return false;
        return data(owner.getUniqueId()).unlocked.add(petId);
    }

    private void levelUp(Player owner, String petId, PlayerPets.Progress pr) {
        ConfigurationSection sec = petSec(petId);
        String name = displayName(petId, pr);
        updateName(owner.getUniqueId());

        owner.showTitle(Title.title(
                plugin.color("<gold><bold>¡NIVEL " + pr.level + "!"),
                plugin.color(name),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        Mob pet = active.get(owner.getUniqueId());
        if (pet != null && pet.isValid()) {
            particle(pet.getWorld(), Particle.TOTEM_OF_UNDYING, pet.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.2);
        }

        if (pr.level == hatLevel()) {
            plugin.msg(owner, "<green>¡Desbloqueaste el <white>sombrero<green>! <gray>Usa /mascota sombrero");
        }
        if (pr.level == rideLevel()) {
            plugin.msg(owner, "<green>¡Ya puedes <white>montar<green> a tu mascota! <gray>Usa /mascota montar");
        }
        for (String s : sec.getStringList("efectos")) {
            String[] p = s.split(":");
            if (p.length >= 3 && parseInt(p[2], 1) == pr.level) {
                plugin.msg(owner, "<aqua>Nuevo efecto pasivo: <white>" + p[0].toLowerCase(Locale.ROOT).replace('_', ' ')
                        + " " + (parseInt(p[1], 0) + 1));
            }
        }
        for (Map<?, ?> m : sec.getMapList("estela")) {
            if (num(m, "nivel", 1) == pr.level) {
                plugin.msg(owner, "<yellow>Nueva estela de partículas: <white>"
                        + String.valueOf(m.get("particula")).toLowerCase(Locale.ROOT).replace('_', ' '));
            }
        }

        if (pr.claimed.add(pr.level)) grantReward(owner, petId, pr.level);
    }

    private void grantReward(Player owner, String petId, int level) {
        ConfigurationSection r = cfg().getConfigurationSection("recompensas." + level);
        if (r == null) return;
        String only = r.getString("solo-mascota");
        if (only != null && !only.equalsIgnoreCase(petId)) return;
        if (!cfg().getBoolean("recompensas-por-mascota", true)) {
            if (!data(owner.getUniqueId()).claimedGlobal.add(level)) return;
        }

        String msg = r.getString("mensaje");
        if (msg != null) plugin.msg(owner, msg);
        for (String cmd : r.getStringList("comandos")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd
                    .replace("%player%", owner.getName())
                    .replace("%pet%", petId)
                    .replace("%level%", String.valueOf(level)));
        }
    }
}
