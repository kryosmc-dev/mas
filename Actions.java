package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.List;

/** Trucos gratuitos propios de cada tipo de mob (sonido, partículas y salto). */
public final class Actions {

    public record Trick(String key, String name, Material icon, String desc, Sound sound, Particle particle, double jump) { }

    private Actions() { }

    public static boolean canRest(String type) {
        return type.equals("WOLF") || type.equals("CAT") || type.equals("FOX");
    }

    public static List<Trick> forType(String type) {
        return switch (type) {
            case "WOLF" -> List.of(
                    new Trick("aullar", "Aullar", Material.GOAT_HORN, "Tu lobo aúlla a la luna.", Sound.ENTITY_WOLF_HOWL, Particle.NOTE, 0.3),
                    new Trick("sacudirse", "Sacudirse", Material.WATER_BUCKET, "Se sacude como si estuviera mojado.", Sound.ENTITY_WOLF_SHAKE, Particle.CLOUD, 0.2));
            case "CAT" -> List.of(
                    new Trick("ronronear", "Ronronear", Material.COD, "Ronronea feliz.", Sound.ENTITY_CAT_PURR, Particle.HEART, 0),
                    new Trick("maullar", "Maullar", Material.STRING, "Te pide atención.", Sound.ENTITY_CAT_AMBIENT, Particle.NOTE, 0.2));
            case "FOX" -> List.of(
                    new Trick("chillar", "Chillar", Material.SWEET_BERRIES, "Suelta un chillido astuto.", Sound.ENTITY_FOX_SCREECH, Particle.NOTE, 0.2),
                    new Trick("pirueta", "Pirueta", Material.RABBIT_FOOT, "Un salto de zorro.", Sound.ENTITY_FOX_AMBIENT, Particle.CLOUD, 0.75));
            case "RABBIT" -> List.of(
                    new Trick("salto", "Salto gigante", Material.RABBIT_FOOT, "Un gran salto de conejo.", Sound.ENTITY_RABBIT_JUMP, Particle.CLOUD, 0.95));
            case "CHICKEN" -> List.of(
                    new Trick("cacarear", "Cacarear", Material.FEATHER, "Canta con orgullo.", Sound.ENTITY_CHICKEN_AMBIENT, Particle.NOTE, 0.25),
                    new Trick("huevo", "Poner huevo", Material.EGG, "Hace como que pone un huevo.", Sound.ENTITY_CHICKEN_EGG, Particle.CLOUD, 0));
            case "PIG" -> List.of(
                    new Trick("oink", "Oink", Material.CARROT_ON_A_STICK, "Hace su ruido de cerdito.", Sound.ENTITY_PIG_AMBIENT, Particle.NOTE, 0.2));
            case "SHEEP" -> List.of(
                    new Trick("balar", "Balar", Material.WHITE_WOOL, "Bala feliz.", Sound.ENTITY_SHEEP_AMBIENT, Particle.NOTE, 0.2));
            case "COW", "MOOSHROOM" -> List.of(
                    new Trick("mugir", "Mugir", Material.MILK_BUCKET, "Muuuu.", Sound.ENTITY_COW_AMBIENT, Particle.NOTE, 0.2));
            case "HORSE" -> List.of(
                    new Trick("relinchar", "Relinchar", Material.SADDLE, "Relincha y da un brinco.", Sound.ENTITY_HORSE_AMBIENT, Particle.NOTE, 0.5),
                    new Trick("galope", "Galope", Material.LEATHER_HORSE_ARMOR, "Marca el paso del galope.", Sound.ENTITY_HORSE_GALLOP, Particle.CLOUD, 0.3));
            case "DONKEY" -> List.of(
                    new Trick("rebuznar", "Rebuznar", Material.WHEAT, "Hi-ho.", Sound.ENTITY_DONKEY_AMBIENT, Particle.NOTE, 0.3));
            case "MULE" -> List.of(
                    new Trick("rebuznar", "Rebuznar", Material.WHEAT, "Se queja con carácter.", Sound.ENTITY_MULE_AMBIENT, Particle.NOTE, 0.3));
            case "LLAMA" -> List.of(
                    new Trick("balar", "Llamear", Material.WHITE_CARPET, "Hace su sonido de llama.", Sound.ENTITY_LLAMA_AMBIENT, Particle.NOTE, 0.2),
                    new Trick("escupir", "Escupir", Material.SNOWBALL, "Un escupitajo inofensivo.", Sound.ENTITY_LLAMA_SPIT, Particle.CLOUD, 0));
            case "PANDA" -> List.of(
                    new Trick("estornudar", "Estornudar", Material.BAMBOO, "¡Achú!", Sound.ENTITY_PANDA_SNEEZE, Particle.CLOUD, 0.25),
                    new Trick("comer", "Mordisco de bambú", Material.BAMBOO, "Mastica bambú con gusto.", Sound.ENTITY_PANDA_EAT, Particle.HEART, 0));
            case "POLAR_BEAR" -> List.of(
                    new Trick("rugir", "Rugir", Material.SNOWBALL, "Un rugido tierno pero feroz.", Sound.ENTITY_POLAR_BEAR_WARNING, Particle.CRIT, 0.3));
            case "TURTLE" -> List.of(
                    new Trick("saludar", "Saludar", Material.TURTLE_HELMET, "Te saluda desde su caparazón.", Sound.ENTITY_TURTLE_AMBIENT_LAND, Particle.HEART, 0));
            case "ENDERMITE" -> List.of(
                    new Trick("chillar", "Chillar", Material.ENDER_PEARL, "Un chirrido del End.", Sound.ENTITY_ENDERMITE_AMBIENT, Particle.PORTAL, 0.6));
            case "CAVE_SPIDER" -> List.of(
                    new Trick("sisear", "Sisear", Material.SPIDER_EYE, "Sisea y da un brinco.", Sound.ENTITY_SPIDER_AMBIENT, Particle.CRIT, 0.5));
            case "SILVERFISH" -> List.of(
                    new Trick("escurrirse", "Escurrirse", Material.COBBLESTONE, "Se escurre nerviosa.", Sound.ENTITY_SILVERFISH_AMBIENT, Particle.CLOUD, 0.3));
            default -> List.of();
        };
    }
}
