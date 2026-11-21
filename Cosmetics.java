package com.kryos.mascotas;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Cat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cosméticos gratuitos que dependen del tipo de mob (más algunos generales para todas). */
public final class Cosmetics {

    public record Choice(String key, String name, Material icon) { }

    @FunctionalInterface
    public interface Applier {
        void apply(Mob mob, String key);
    }

    public record Option(String key, String name, Material icon, String desc, List<Choice> choices, Applier applier) {
        public Choice choice(String k) {
            for (Choice c : choices) {
                if (c.key().equalsIgnoreCase(k)) return c;
            }
            return choices.get(0);
        }
    }

    private Cosmetics() { }

    public static List<Option> forType(String type) {
        List<Option> out = new ArrayList<>();
        switch (type) {
            case "WOLF" -> {
                out.add(wolfAspect());
                out.add(collar(Material.LEAD));
            }
            case "CAT" -> {
                out.add(catAspect());
                out.add(collar(Material.LEAD));
            }
            case "FOX" -> out.add(simpleEnum("aspecto", "Aspecto", Material.SWEET_BERRIES, "Cambia el pelaje del zorro.",
                    new String[][]{{"RED", "Rojo", "ORANGE_DYE"}, {"SNOW", "Nieve", "WHITE_DYE"}},
                    (m, k) -> {
                        if (m instanceof Fox f) f.setFoxType(Fox.Type.valueOf(k));
                    }));
            case "RABBIT" -> out.add(simpleEnum("aspecto", "Aspecto", Material.CARROT, "Cambia el pelaje del conejo.",
                    new String[][]{{"BROWN", "Marrón", "BROWN_DYE"}, {"WHITE", "Blanco", "WHITE_DYE"},
                            {"BLACK", "Negro", "BLACK_DYE"}, {"BLACK_AND_WHITE", "Blanco y negro", "GRAY_DYE"},
                            {"GOLD", "Dorado", "YELLOW_DYE"}, {"SALT_AND_PEPPER", "Sal y pimienta", "LIGHT_GRAY_DYE"}},
                    (m, k) -> {
                        if (m instanceof Rabbit r) r.setRabbitType(Rabbit.Type.valueOf(k));
                    }));
            case "SHEEP" -> {
                out.add(wool());
                out.add(toggle("esquilada", "Esquilada", Material.SHEARS, "Quita o devuelve la lana.",
                        (m, k) -> {
                            if (m instanceof Sheep s) s.setSheared(k.equals("SI"));
                        }));
            }
            case "PIG" -> out.add(toggle("montura", "Montura", Material.SADDLE, "Ponle una silla de montar.",
                    (m, k) -> {
                        if (m instanceof Pig p) p.setSaddle(k.equals("SI"));
                    }));
            case "HORSE" -> {
                out.add(simpleEnum("pelaje", "Pelaje", Material.LEATHER, "Color del potrillo.",
                        new String[][]{{"BROWN", "Marrón", "BROWN_DYE"}, {"WHITE", "Blanco", "WHITE_DYE"},
                                {"CREAMY", "Cremoso", "YELLOW_DYE"}, {"CHESTNUT", "Castaño", "ORANGE_DYE"},
                                {"BLACK", "Negro", "BLACK_DYE"}, {"GRAY", "Gris", "GRAY_DYE"},
                                {"DARK_BROWN", "Marrón oscuro", "RED_DYE"}},
                        (m, k) -> {
                            if (m instanceof Horse h) h.setColor(Horse.Color.valueOf(k));
                        }));
                out.add(simpleEnum("manchas", "Manchas", Material.PAINTING, "Dibujo del pelaje.",
                        new String[][]{{"NONE", "Ninguna", "BARRIER"}, {"WHITE", "Blanco", "WHITE_DYE"},
                                {"WHITEFIELD", "Campo blanco", "LIGHT_GRAY_DYE"}, {"WHITE_DOTS", "Puntos blancos", "WHITE_DYE"},
                                {"BLACK_DOTS", "Puntos negros", "BLACK_DYE"}},
                        (m, k) -> {
                            if (m instanceof Horse h) h.setStyle(Horse.Style.valueOf(k));
                        }));
                out.add(saddle());
                out.add(simpleEnum("armadura", "Armadura", Material.IRON_HORSE_ARMOR, "Armadura de caballo (solo estética).",
                        new String[][]{{"NINGUNA", "Ninguna", "BARRIER"}, {"CUERO", "Cuero", "LEATHER_HORSE_ARMOR"},
                                {"HIERRO", "Hierro", "IRON_HORSE_ARMOR"}, {"ORO", "Oro", "GOLDEN_HORSE_ARMOR"},
                                {"DIAMANTE", "Diamante", "DIAMOND_HORSE_ARMOR"}},
                        (m, k) -> {
                            if (m instanceof Horse h) {
                                Material mat = switch (k) {
                                    case "CUERO" -> Material.LEATHER_HORSE_ARMOR;
                                    case "HIERRO" -> Material.IRON_HORSE_ARMOR;
                                    case "ORO" -> Material.GOLDEN_HORSE_ARMOR;
                                    case "DIAMANTE" -> Material.DIAMOND_HORSE_ARMOR;
                                    default -> null;
                                };
                                h.getInventory().setArmor(mat == null ? null : new ItemStack(mat));
                            }
                        }));
            }
            case "DONKEY", "MULE" -> {
                out.add(saddle());
                out.add(chest());
            }
            case "LLAMA" -> {
                out.add(simpleEnum("color", "Color", Material.WHITE_WOOL, "Color de la llamita.",
                        new String[][]{{"CREAMY", "Cremoso", "YELLOW_DYE"}, {"WHITE", "Blanco", "WHITE_DYE"},
                                {"BROWN", "Marrón", "BROWN_DYE"}, {"GRAY", "Gris", "GRAY_DYE"}},
                        (m, k) -> {
                            if (m instanceof Llama l) l.setColor(Llama.Color.valueOf(k));
                        }));
                out.add(carpet());
                out.add(chest());
            }
            case "PANDA" -> out.add(simpleEnum("personalidad", "Personalidad", Material.BAMBOO, "Cambia el aspecto y carácter.",
                    new String[][]{{"NORMAL", "Normal", "WHITE_DYE"}, {"BROWN", "Marrón", "BROWN_DYE"},
                            {"LAZY", "Perezoso", "LIGHT_GRAY_DYE"}, {"PLAYFUL", "Juguetón", "YELLOW_DYE"},
                            {"WORRIED", "Preocupado", "LIGHT_BLUE_DYE"}, {"WEAK", "Débil", "GRAY_DYE"}},
                    (m, k) -> {
                        if (m instanceof Panda p) {
                            Panda.Gene g = Panda.Gene.valueOf(k);
                            p.setMainGene(g);
                            p.setHiddenGene(g);
                        }
                    }));
            case "MOOSHROOM" -> out.add(simpleEnum("tipo", "Tipo", Material.RED_MUSHROOM, "Hongos rojos o marrones.",
                    new String[][]{{"RED", "Rojo", "RED_DYE"}, {"BROWN", "Marrón", "BROWN_DYE"}},
                    (m, k) -> {
                        if (m instanceof MushroomCow c) c.setVariant(MushroomCow.Variant.valueOf(k));
                    }));
            default -> { }
        }
        out.addAll(general());
        return out;
    }

    // ------------------------------------------------------------------ generales

    private static List<Option> general() {
        List<Option> l = new ArrayList<>();
        l.add(simpleEnum("tamano", "Tamaño", Material.SLIME_BALL, "Hazla más chiquita o un poco más grande.",
                new String[][]{{"NORMAL", "Normal", "LIME_DYE"}, {"MINI", "Mini", "PINK_DYE"}, {"GRANDE", "Un poco más grande", "ORANGE_DYE"}},
                (m, k) -> {
                    AttributeInstance sc = m.getAttribute(Attribute.SCALE);
                    if (sc != null) sc.setBaseValue(switch (k) {
                        case "MINI" -> 0.6;
                        case "GRANDE" -> 1.25;
                        default -> 1.0;
                    });
                }));
        l.add(toggle("brillo", "Brillo", Material.GLOWSTONE_DUST, "Un resplandor que se ve a distancia.",
                (m, k) -> m.setGlowing(k.equals("SI"))));
        l.add(simpleEnum("nombre", "Nombre sobre la cabeza", Material.OAK_SIGN, "Muestra u oculta su nombre.",
                new String[][]{{"VISIBLE", "Visible", "LIME_DYE"}, {"OCULTO", "Oculto", "GRAY_DYE"}},
                (m, k) -> m.setCustomNameVisible(k.equals("VISIBLE"))));
        l.add(simpleEnum("color-nombre", "Color del apodo", Material.NAME_TAG, "Color del apodo (si le pusiste uno).",
                new String[][]{{"yellow", "Amarillo", "YELLOW_DYE"}, {"gold", "Dorado", "ORANGE_DYE"},
                        {"red", "Rojo", "RED_DYE"}, {"light_purple", "Rosa", "PINK_DYE"},
                        {"aqua", "Celeste", "LIGHT_BLUE_DYE"}, {"green", "Verde", "LIME_DYE"},
                        {"white", "Blanco", "WHITE_DYE"}, {"gray", "Gris", "GRAY_DYE"}},
                (m, k) -> { }));
        return l;
    }

    // ------------------------------------------------------------------ builders

    private static Option simpleEnum(String key, String name, Material icon, String desc, String[][] rows, Applier ap) {
        List<Choice> c = new ArrayList<>();
        for (String[] r : rows) c.add(new Choice(r[0], r[1], Material.valueOf(r[2])));
        return new Option(key, name, icon, desc, c, ap);
    }

    private static Option toggle(String key, String name, Material icon, String desc, Applier ap) {
        return simpleEnum(key, name, icon, desc,
                new String[][]{{"NO", "No", "GRAY_DYE"}, {"SI", "Sí", "LIME_DYE"}}, ap);
    }

    private static Option saddle() {
        return toggle("montura", "Montura", Material.SADDLE, "Ponle una silla de montar.",
                (m, k) -> {
                    if (m instanceof AbstractHorse h) h.getInventory().setSaddle(k.equals("SI") ? new ItemStack(Material.SADDLE) : null);
                });
    }

    private static Option chest() {
        return toggle("cofre", "Cofre", Material.CHEST, "Alforjas con cofre (solo estética).",
                (m, k) -> {
                    if (m instanceof ChestedHorse c) c.setCarryingChest(k.equals("SI"));
                });
    }

    private static List<Choice> dyes(DyeColor first, boolean extraFirst, String extraKey, String extraName, Material extraIcon) {
        List<Choice> l = new ArrayList<>();
        if (extraFirst) l.add(new Choice(extraKey, extraName, extraIcon));
        l.add(new Choice(first.name(), dyeName(first), Material.valueOf(first.name() + "_DYE")));
        for (DyeColor d : DyeColor.values()) {
            if (d != first) l.add(new Choice(d.name(), dyeName(d), Material.valueOf(d.name() + "_DYE")));
        }
        return l;
    }

    private static Option collar(Material icon) {
        return new Option("collar", "Collar", icon, "Color del collar.",
                dyes(DyeColor.RED, false, "", "", Material.AIR),
                (m, k) -> {
                    DyeColor d = DyeColor.valueOf(k);
                    if (m instanceof Wolf w) w.setCollarColor(d);
                    else if (m instanceof Cat c) c.setCollarColor(d);
                });
    }

    private static Option wool() {
        List<Choice> c = dyes(DyeColor.WHITE, false, "", "", Material.AIR);
        c.add(new Choice("ARCOIRIS", "Arcoíris", Material.FIREWORK_STAR));
        return new Option("lana", "Color de lana", Material.WHITE_WOOL, "Tiñe la lana o actívale el modo arcoíris.", c,
                (m, k) -> {
                    if (k.equals("ARCOIRIS")) return;
                    if (m instanceof Sheep s) s.setColor(DyeColor.valueOf(k));
                });
    }

    private static Option carpet() {
        List<Choice> c = dyes(DyeColor.WHITE, true, "NINGUNA", "Ninguna", Material.BARRIER);
        return new Option("alfombra", "Alfombra", Material.WHITE_CARPET, "Una alfombra decorativa.", c,
                (m, k) -> {
                    if (m instanceof Llama l) {
                        l.getInventory().setDecor(k.equals("NINGUNA") ? null : new ItemStack(Material.valueOf(k + "_CARPET")));
                    }
                });
    }

    private static Option wolfAspect() {
        List<Choice> c = List.of(
                new Choice("PALE", "Pálido", Material.WHITE_DYE),
                new Choice("SPOTTED", "Moteado", Material.ORANGE_DYE),
                new Choice("SNOWY", "Nevado", Material.LIGHT_BLUE_DYE),
                new Choice("BLACK", "Negro", Material.BLACK_DYE),
                new Choice("ASHEN", "Cenizo", Material.GRAY_DYE),
                new Choice("RUSTY", "Oxidado", Material.RED_DYE),
                new Choice("WOODS", "Del bosque", Material.GREEN_DYE),
                new Choice("CHESTNUT", "Castaño", Material.BROWN_DYE),
                new Choice("STRIPED", "Rayado", Material.YELLOW_DYE));
        return new Option("aspecto", "Aspecto", Material.BONE, "Cambia el pelaje de tu lobo.", c,
                (m, k) -> {
                    if (!(m instanceof Wolf w)) return;
                    Wolf.Variant v = switch (k) {
                        case "PALE" -> Wolf.Variant.PALE;
                        case "SPOTTED" -> Wolf.Variant.SPOTTED;
                        case "SNOWY" -> Wolf.Variant.SNOWY;
                        case "BLACK" -> Wolf.Variant.BLACK;
                        case "ASHEN" -> Wolf.Variant.ASHEN;
                        case "RUSTY" -> Wolf.Variant.RUSTY;
                        case "WOODS" -> Wolf.Variant.WOODS;
                        case "CHESTNUT" -> Wolf.Variant.CHESTNUT;
                        case "STRIPED" -> Wolf.Variant.STRIPED;
                        default -> null;
                    };
                    if (v != null) w.setVariant(v);
                });
    }

    private static Option catAspect() {
        List<Choice> c = List.of(
                new Choice("TABBY", "Atigrado", Material.ORANGE_DYE),
                new Choice("BLACK", "Negro", Material.BLACK_DYE),
                new Choice("RED", "Rojo", Material.RED_DYE),
                new Choice("SIAMESE", "Siamés", Material.LIGHT_GRAY_DYE),
                new Choice("BRITISH_SHORTHAIR", "Británico", Material.GRAY_DYE),
                new Choice("CALICO", "Calicó", Material.YELLOW_DYE),
                new Choice("PERSIAN", "Persa", Material.WHITE_DYE),
                new Choice("RAGDOLL", "Ragdoll", Material.LIGHT_BLUE_DYE),
                new Choice("WHITE", "Blanco", Material.WHITE_DYE),
                new Choice("JELLIE", "Jellie", Material.PINK_DYE),
                new Choice("ALL_BLACK", "Negro total", Material.BLACK_DYE));
        return new Option("aspecto", "Aspecto", Material.COD, "Cambia el pelaje de tu gato.", c,
                (m, k) -> {
                    if (!(m instanceof Cat cat)) return;
                    Cat.Type t = switch (k) {
                        case "TABBY" -> Cat.Type.TABBY;
                        case "BLACK" -> Cat.Type.BLACK;
                        case "RED" -> Cat.Type.RED;
                        case "SIAMESE" -> Cat.Type.SIAMESE;
                        case "BRITISH_SHORTHAIR" -> Cat.Type.BRITISH_SHORTHAIR;
                        case "CALICO" -> Cat.Type.CALICO;
                        case "PERSIAN" -> Cat.Type.PERSIAN;
                        case "RAGDOLL" -> Cat.Type.RAGDOLL;
                        case "WHITE" -> Cat.Type.WHITE;
                        case "JELLIE" -> Cat.Type.JELLIE;
                        case "ALL_BLACK" -> Cat.Type.ALL_BLACK;
                        default -> null;
                    };
                    if (t != null) cat.setCatType(t);
                });
    }

    private static String dyeName(DyeColor d) {
        return switch (d) {
            case WHITE -> "Blanco";
            case ORANGE -> "Naranja";
            case MAGENTA -> "Magenta";
            case LIGHT_BLUE -> "Celeste";
            case YELLOW -> "Amarillo";
            case LIME -> "Lima";
            case PINK -> "Rosa";
            case GRAY -> "Gris";
            case LIGHT_GRAY -> "Gris claro";
            case CYAN -> "Cian";
            case PURPLE -> "Morado";
            case BLUE -> "Azul";
            case BROWN -> "Café";
            case GREEN -> "Verde";
            case RED -> "Rojo";
            case BLACK -> "Negro";
        };
    }

    public static Option find(String type, String key) {
        for (Option o : forType(type)) {
            if (o.key().equals(key)) return o;
        }
        return null;
    }

    public static String keyOf(String s) {
        return s.toUpperCase(Locale.ROOT);
    }
}
