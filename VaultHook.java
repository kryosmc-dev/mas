package com.kryos.mascotas;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Aislado en su propia clase para que el plugin funcione aunque Vault no esté instalado. */
public class VaultHook {

    private final Economy eco;

    private VaultHook(Economy eco) {
        this.eco = eco;
    }

    public static VaultHook create() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            return rsp == null ? null : new VaultHook(rsp.getProvider());
        } catch (Throwable t) {
            return null;
        }
    }

    public double balance(Player p) {
        return eco.getBalance(p);
    }

    public boolean has(Player p, double amount) {
        return eco.has(p, amount);
    }

    public boolean withdraw(Player p, double amount) {
        return eco.withdrawPlayer(p, amount).transactionSuccess();
    }
}
