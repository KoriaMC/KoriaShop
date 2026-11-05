package fr.koriamc.shop;

import fr.koriamc.shop.config.JsonFile;
import fr.koriamc.shop.config.Messages;
import fr.koriamc.shop.gui.ShopMenus;
import fr.koriamc.shop.hook.SaleHooks;
import fr.koriamc.shop.util.Menu;
import fr.koriamc.shop.util.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * KoriaShop : shop du serveur (achat et vente), "tout vendre", /sell.
 * Les ventes comptent pour les quêtes (VENDRE, VENDRE_ARGENT) et les outils du Toucan.
 * <p>
 * Fichiers : config.json (réglages), shop.json (catégories et prix), messages.json.
 */
public final class KoriaShop extends JavaPlugin implements Listener {

    private static KoriaShop instance;

    private JsonFile configFile;
    private JsonFile shopFile;
    private JsonFile messagesFile;
    private Configuration config = new MemoryConfiguration();
    private final Messages messages = new Messages();
    private final ShopCatalog catalog = new ShopCatalog();
    private ShopService shop;
    private ShopMenus menus;
    private SaleHooks hooks;
    private VaultHook vault;
    private boolean vaultChecked;

    public static KoriaShop get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        configFile = new JsonFile(this, "config.json", "multiplicateurs-vente");
        shopFile = new JsonFile(this, "shop.json", "categories", "categories.*", "categories.*.objets", "categories.*.objets.*");
        messagesFile = new JsonFile(this, "messages.json");
        shop = new ShopService(this);
        menus = new ShopMenus(this);
        hooks = new SaleHooks(getLogger());
        for (String problem : reload()) {
            getLogger().warning(problem);
        }
        Bukkit.getPluginManager().registerEvents(new Menu.Handler(this), this);
        Bukkit.getPluginManager().registerEvents(this, this);
        // les autres plugins sont activés : on peut s'y accrocher
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                hooks.register();
            }
        });
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                // la fermeture déclenche la vente du coffre "tout vendre" ou rend les objets
                player.closeInventory();
            }
        }
        instance = null;
    }

    public List<String> reload() {
        List<String> problems = new ArrayList<String>();
        for (JsonFile file : new JsonFile[]{configFile, shopFile, messagesFile}) {
            String error = file.load();
            if (error != null) {
                problems.add(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messages.load(messagesFile.toConfiguration());
        // shop.json sans valeurs du jar : un objet retiré reste retiré
        Configuration shopConfig = shopFile.toConfiguration();
        shopConfig.setDefaults(new MemoryConfiguration());
        catalog.load(shopConfig);
        problems.addAll(catalog.getProblems());
        return problems;
    }

    public VaultHook economy() {
        if (vault == null && !vaultChecked && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            vaultChecked = true;
            vault = VaultHook.create();
        }
        return vault;
    }

    // ============================================================= Commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("shop") && args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("koriashop.admin")) {
                messages.send(sender, "erreurs.permission");
                return true;
            }
            List<String> problems = reload();
            hooks.register();
            messages.send(sender, "commande.recharge", "problemes", problems.size());
            for (String p : problems) {
                sender.sendMessage("§c- " + p);
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            messages.send(sender, "erreurs.joueur");
            return true;
        }
        Player player = (Player) sender;
        if (name.equals("shop")) {
            if (args.length >= 1) {
                ShopCatalog.Category category = catalog.get(args[0].toLowerCase(Locale.ROOT));
                if (category != null) {
                    menus.openCategory(player, category);
                    return true;
                }
            }
            menus.openMain(player);
            return true;
        }
        // /sell [hand|all]
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("hand") || sub.equals("main")) {
            shop.sell(player, player.getInventory(), Collections.singletonList(player.getInventory().getHeldItemSlot()), null, true);
        } else if (sub.equals("all") || sub.equals("tout")) {
            if (!player.hasPermission("koriashop.sell.all")) {
                messages.send(player, "erreurs.permission");
                return true;
            }
            List<Integer> slots = new ArrayList<Integer>();
            for (int i = 0; i < 36; i++) {
                slots.add(i);
            }
            shop.sell(player, player.getInventory(), slots, null, true);
        } else {
            menus.openSellChest(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length != 1) {
            return out;
        }
        if (command.getName().equalsIgnoreCase("sell")) {
            out.add("hand");
            out.add("all");
        } else {
            for (ShopCatalog.Category c : catalog.all()) {
                out.add(c.id);
            }
            if (sender.hasPermission("koriashop.admin")) {
                out.add("reload");
            }
        }
        return fr.koriamc.shop.util.Text.complete(args[0], out);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hooks.forget(event.getPlayer().getUniqueId());
    }

    // ============================================================ Accesseurs

    public Configuration config() {
        return config;
    }

    public Messages msg() {
        return messages;
    }

    public ShopCatalog catalog() {
        return catalog;
    }

    public ShopService shop() {
        return shop;
    }

    public SaleHooks hooks() {
        return hooks;
    }
}
