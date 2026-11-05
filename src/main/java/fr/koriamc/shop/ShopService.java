package fr.koriamc.shop;

import fr.koriamc.shop.api.ShopSellEvent;
import fr.koriamc.shop.util.Text;
import fr.koriamc.shop.util.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Achat et vente : paiement Vault, multiplicateurs de vente par permission,
 * signalement aux quêtes et au Toucan.
 */
public final class ShopService {

    private final KoriaShop plugin;

    public ShopService(KoriaShop plugin) {
        this.plugin = plugin;
    }

    private VaultHook economy(Player player) {
        VaultHook economy = plugin.economy();
        if (economy == null) {
            plugin.msg().send(player, "erreurs.economie");
        }
        return economy;
    }

    /** Multiplicateur de vente du joueur (le plus grand de config.json : multiplicateurs-vente). */
    public double multiplier(Player player) {
        double best = 1;
        org.bukkit.configuration.ConfigurationSection m = plugin.config().getConfigurationSection("multiplicateurs-vente");
        if (m != null) {
            for (String perm : m.getKeys(false)) {
                double v = m.getDouble(perm, 1);
                if (v > best && player.hasPermission(perm)) {
                    best = v;
                }
            }
        }
        return best;
    }

    // ================================================================= Achat

    /** Achète {@code amount} objets. */
    public void buy(Player player, ShopCatalog.Entry entry, int amount) {
        if (entry.buy <= 0 || amount <= 0) {
            plugin.msg().send(player, "achat.indisponible");
            return;
        }
        VaultHook economy = economy(player);
        if (economy == null) {
            return;
        }
        int free = space(player, entry.item(1));
        amount = Math.min(amount, free);
        if (amount <= 0) {
            plugin.msg().send(player, "achat.inventaire-plein");
            return;
        }
        double price = entry.buy * amount;
        if (!economy.has(player, price)) {
            plugin.msg().send(player, "achat.pas-assez", "prix", money(price));
            return;
        }
        if (!economy.withdraw(player, price)) {
            plugin.msg().send(player, "erreurs.economie");
            return;
        }
        int left = amount;
        int stack = entry.material.getMaxStackSize();
        while (left > 0) {
            int n = Math.min(left, stack);
            player.getInventory().addItem(entry.item(n));
            left -= n;
        }
        plugin.msg().send(player, "achat.ok", "quantite", amount, "objet", entry.name, "prix", money(price));
    }

    /** Place disponible dans l'inventaire pour cet objet. */
    private static int space(Player player, ItemStack like) {
        int max = like.getMaxStackSize();
        int free = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                free += max;
            } else if (item.isSimilar(like)) {
                free += Math.max(0, max - item.getAmount());
            }
        }
        return free;
    }

    // ================================================================= Vente

    /**
     * Vend les objets repris par le shop parmi {@code slots} de l'inventaire (les autres restent).
     *
     * @param only si non null, seulement les objets de cette entrée
     * @return le montant gagné
     */
    public double sell(Player player, Inventory inventory, List<Integer> slots, ShopCatalog.Entry only, boolean announce) {
        VaultHook economy = economy(player);
        if (economy == null) {
            return 0;
        }
        double multiplier = multiplier(player);
        Map<ShopCatalog.Entry, Integer> sold = new LinkedHashMap<ShopCatalog.Entry, Integer>();
        Map<ShopCatalog.Entry, ItemStack> samples = new LinkedHashMap<ShopCatalog.Entry, ItemStack>();
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            ShopCatalog.Entry entry = plugin.catalog().sellEntry(item);
            if (entry == null || (only != null && entry != only)) {
                continue;
            }
            Integer n = sold.get(entry);
            sold.put(entry, (n == null ? 0 : n) + item.getAmount());
            if (!samples.containsKey(entry)) {
                ItemStack sample = item.clone();
                sample.setAmount(1);
                samples.put(entry, sample);
            }
            inventory.setItem(slot, null);
        }
        if (sold.isEmpty()) {
            if (announce) {
                plugin.msg().send(player, "vente.rien");
            }
            return 0;
        }
        double total = 0;
        int count = 0;
        for (Map.Entry<ShopCatalog.Entry, Integer> e : sold.entrySet()) {
            double gain = e.getKey().sell * e.getValue() * multiplier;
            total += gain;
            count += e.getValue();
        }
        economy.deposit(player, total);
        for (Map.Entry<ShopCatalog.Entry, Integer> e : sold.entrySet()) {
            double gain = e.getKey().sell * e.getValue() * multiplier;
            ItemStack sample = samples.get(e.getKey());
            plugin.hooks().onSale(player, sample, e.getValue(), gain);
            Bukkit.getPluginManager().callEvent(new ShopSellEvent(player, sample, e.getValue(), gain));
            if (announce && sold.size() <= plugin.config().getInt("detail-max-lignes", 5)) {
                plugin.msg().send(player, "vente.ligne", "quantite", e.getValue(), "objet", e.getKey().name, "prix", money(gain));
            }
        }
        if (announce) {
            plugin.msg().send(player, "vente.total", "quantite", count, "prix", money(total),
                    "bonus", multiplier > 1 ? Text.color(" &8(x" + Text.number(multiplier, 2) + ")") : "");
        }
        player.updateInventory();
        return total;
    }

    public static String money(double value) {
        return Text.number(value, 2);
    }
}
