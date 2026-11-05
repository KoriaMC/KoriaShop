package fr.koriamc.shop.gui;

import fr.koriamc.shop.KoriaShop;
import fr.koriamc.shop.ShopCatalog;
import fr.koriamc.shop.ShopService;
import fr.koriamc.shop.util.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menus du shop : accueil (catégories + tout vendre), catégorie (achat / vente au clic),
 * coffre "tout vendre" (on y dépose des objets, tout ce qui est repris est vendu à la fermeture).
 */
public final class ShopMenus {

    private final KoriaShop plugin;

    public ShopMenus(KoriaShop plugin) {
        this.plugin = plugin;
    }

    private static ItemStack material(String spec, Material fallback) {
        if (spec != null) {
            String[] p = spec.split(":");
            Material m = Material.matchMaterial(p[0].toUpperCase(Locale.ROOT));
            if (m != null) {
                short data = 0;
                try {
                    data = p.length > 1 ? Short.parseShort(p[1]) : 0;
                } catch (NumberFormatException ignored) {
                    // data invalide
                }
                return new ItemStack(m, 1, data);
            }
        }
        return new ItemStack(fallback);
    }

    // ================================================================ Accueil

    public void openMain(final Player player) {
        Menu menu = new Menu(5, plugin.msg().text("menu.titre"));
        int[] auto = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int next = 0;
        for (final ShopCatalog.Category category : plugin.catalog().all()) {
            int at;
            if (category.slot >= 0 && category.slot < 36) {
                at = category.slot;
            } else if (next < auto.length) {
                at = auto[next++];
            } else {
                continue;
            }
            menu.button(at, Menu.icon(material(category.icon, Material.CHEST), category.name,
                    plugin.msg().lines("menu.categorie-lore", "objets", category.entries.size())),
                    new Menu.Action() {
                        @Override
                        public void click(Player p, InventoryClickEvent event) {
                            openCategory(p, category);
                        }
                    });
        }
        menu.button(40, Menu.icon(new ItemStack(Material.HOPPER), plugin.msg().text("menu.tout-vendre"),
                plugin.msg().lines("menu.tout-vendre-lore")), new Menu.Action() {
            @Override
            public void click(Player p, InventoryClickEvent event) {
                openSellChest(p);
            }
        });
        menu.fill();
        menu.open(player);
    }

    // ============================================================== Catégorie

    public void openCategory(final Player player, final ShopCatalog.Category category) {
        final Menu menu = new Menu(6, category.name);
        int slot = 0;
        for (final ShopCatalog.Entry entry : category.entries) {
            if (slot >= 45) {
                break;
            }
            List<String> lore = new ArrayList<String>();
            if (entry.buy > 0) {
                lore.addAll(plugin.msg().lines("categorie.achat", "prix", ShopService.money(entry.buy),
                        "prix64", ShopService.money(entry.buy * entry.material.getMaxStackSize()),
                        "stack", entry.material.getMaxStackSize()));
            }
            if (entry.sell > 0) {
                double m = plugin.shop().multiplier(player);
                lore.addAll(plugin.msg().lines("categorie.vente", "prix", ShopService.money(entry.sell * m)));
            }
            if (entry.buy <= 0 && entry.sell <= 0) {
                lore.add(plugin.msg().text("categorie.indisponible"));
            }
            menu.button(slot++, Menu.icon(entry.item(1), entry.name, lore), new Menu.Action() {
                @Override
                public void click(Player p, InventoryClickEvent event) {
                    ClickType click = event.getClick();
                    if (click == ClickType.LEFT) {
                        plugin.shop().buy(p, entry, 1);
                    } else if (click == ClickType.SHIFT_LEFT) {
                        plugin.shop().buy(p, entry, entry.material.getMaxStackSize());
                    } else if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                        sellFromInventory(p, entry, click == ClickType.SHIFT_RIGHT);
                    }
                }
            });
        }
        menu.button(49, Menu.icon(new ItemStack(Material.ARROW), plugin.msg().text("menu.retour")), new Menu.Action() {
            @Override
            public void click(Player p, InventoryClickEvent event) {
                openMain(p);
            }
        });
        menu.fill();
        menu.open(player);
    }

    /** Clic droit : vend 1 objet ; shift + clic droit : vend tous ceux de l'inventaire. */
    private void sellFromInventory(Player player, ShopCatalog.Entry entry, boolean all) {
        if (entry.sell <= 0) {
            plugin.msg().send(player, "vente.non-repris");
            return;
        }
        Inventory inv = player.getInventory();
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < 36; i++) {
            if (entry.matches(inv.getItem(i)) && plugin.catalog().sellEntry(inv.getItem(i)) == entry) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) {
            plugin.msg().send(player, "vente.rien");
            return;
        }
        if (all) {
            plugin.shop().sell(player, inv, slots, entry, true);
            return;
        }
        // un seul : on isole un objet dans la première case trouvée
        int first = slots.get(0);
        ItemStack stack = inv.getItem(first);
        ItemStack rest = null;
        if (stack.getAmount() > 1) {
            rest = stack.clone();
            rest.setAmount(stack.getAmount() - 1);
            stack.setAmount(1);
            inv.setItem(first, stack);
        }
        List<Integer> one = new ArrayList<Integer>();
        one.add(first);
        plugin.shop().sell(player, inv, one, entry, true);
        if (rest != null) {
            inv.setItem(first, rest);
        }
    }

    // ============================================================ Tout vendre

    public void openSellChest(final Player player) {
        final Menu menu = new Menu(6, plugin.msg().text("tout-vendre.titre"));
        final List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < 54; i++) {
            menu.free(i);
            slots.add(i);
        }
        menu.allowShift();
        menu.onClose(new Runnable() {
            @Override
            public void run() {
                plugin.shop().sell(player, menu.getInventory(), slots, null, true);
                // ce qui n'est pas repris retourne au joueur
                menu.giveBackFree(player);
            }
        });
        menu.open(player);
    }
}
