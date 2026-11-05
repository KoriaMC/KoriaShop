package fr.koriamc.shop;

import fr.koriamc.shop.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catégories et prix de shop.json.
 * <p>
 * Un objet s'écrit "MATERIAU" ou "MATERIAU:data". Sans data, il correspond à la data 0,
 * ou à toutes les data pour un objet à durabilité (outils, armures).
 */
public final class ShopCatalog {

    /** Un objet du shop. */
    public static final class Entry {
        public final String key;
        public final Material material;
        /** -1 : toutes les data (objet à durabilité sans data précisée). */
        public final int data;
        public final String name;
        /** Prix d'achat unitaire (0 = pas en vente). */
        public final double buy;
        /** Prix de vente unitaire (0 = le shop ne le reprend pas). */
        public final double sell;

        Entry(String key, Material material, int data, String name, double buy, double sell) {
            this.key = key;
            this.material = material;
            this.data = data;
            this.name = name;
            this.buy = buy;
            this.sell = sell;
        }

        @SuppressWarnings("deprecation")
        public ItemStack item(int amount) {
            return new ItemStack(material, amount, (short) Math.max(0, data));
        }

        public boolean matches(ItemStack item) {
            return item != null && item.getType() == material && (data < 0 || item.getDurability() == data);
        }
    }

    /** Une catégorie du menu. */
    public static final class Category {
        public final String id;
        public final String name;
        public final String icon;
        public final int slot;
        public final List<Entry> entries;

        Category(String id, String name, String icon, int slot, List<Entry> entries) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
            this.entries = Collections.unmodifiableList(entries);
        }
    }

    private final Map<String, Category> categories = new LinkedHashMap<String, Category>();
    private final List<String> problems = new ArrayList<String>();

    public void load(ConfigurationSection root) {
        categories.clear();
        problems.clear();
        ConfigurationSection cats = root.getConfigurationSection("categories");
        if (cats == null) {
            problems.add("shop.json : section categories manquante.");
            return;
        }
        for (String id : cats.getKeys(false)) {
            ConfigurationSection c = cats.getConfigurationSection(id);
            if (c == null || !c.getBoolean("actif", true)) {
                continue;
            }
            List<Entry> entries = new ArrayList<Entry>();
            ConfigurationSection objects = c.getConfigurationSection("objets");
            if (objects != null) {
                for (String key : objects.getKeys(false)) {
                    Entry e = read(id, key, objects);
                    if (e != null) {
                        entries.add(e);
                    }
                }
            }
            categories.put(id, new Category(id, c.getString("nom", id), c.getString("icone", "CHEST"), c.getInt("case", -1), entries));
        }
    }

    private Entry read(String category, String key, ConfigurationSection objects) {
        String[] p = key.trim().split(":");
        Material m = Material.matchMaterial(p[0].toUpperCase(Locale.ROOT));
        if (m == null) {
            problems.add("shop.json : " + category + "." + key + " : objet inconnu (ignoré).");
            return null;
        }
        int data;
        try {
            data = p.length > 1 ? Integer.parseInt(p[1]) : (m.getMaxDurability() > 0 ? -1 : 0);
        } catch (NumberFormatException e) {
            problems.add("shop.json : " + category + "." + key + " : data invalide.");
            return null;
        }
        ConfigurationSection s = objects.getConfigurationSection(key);
        double buy;
        double sell;
        String name;
        if (s == null) {
            // forme courte : "MATERIAU": prix de vente
            buy = 0;
            sell = objects.getDouble(key, 0);
            name = null;
        } else {
            buy = Math.max(0, s.getDouble("achat", 0));
            sell = Math.max(0, s.getDouble("vente", 0));
            name = s.getString("nom");
        }
        if (name == null) {
            name = "&f" + Character.toUpperCase(m.name().charAt(0)) + m.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        return new Entry(key, m, data, Text.color(name), buy, sell);
    }

    public Collection<Category> all() {
        return Collections.unmodifiableCollection(categories.values());
    }

    public Category get(String id) {
        return categories.get(id);
    }

    /** Entrée du shop qui reprend cet objet (prix de vente > 0), ou null. */
    public Entry sellEntry(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        // un objet renommé, enchanté ou décrit n'est pas repris (objets custom, outils améliorés)
        if (item.hasItemMeta() && (item.getItemMeta().hasDisplayName() || item.getItemMeta().hasLore()
                || item.getItemMeta().hasEnchants())) {
            return null;
        }
        Entry fallback = null;
        for (Category c : categories.values()) {
            for (Entry e : c.entries) {
                if (e.sell > 0 && e.matches(item)) {
                    if (e.data >= 0) {
                        return e;
                    }
                    fallback = e;
                }
            }
        }
        return fallback;
    }

    public List<String> getProblems() {
        return problems;
    }
}
