package fr.koriamc.shop.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Signale les ventes aux autres plugins KoriaMC (par réflexion, tous facultatifs) :
 * <ul>
 *   <li>KoriaQuest : objectifs VENDRE (quantité) et VENDRE_ARGENT (montant en $) ;</li>
 *   <li>KoriaKrebbs : niveaux du Toucan dont l'objectif est VENDRE.</li>
 * </ul>
 */
public final class SaleHooks {

    private final Logger logger;
    private Method questOf;
    private Method questItem;
    private Method questProgress;
    private Method krebbsSale;
    /** Centimes de $ pas encore signalés à VENDRE_ARGENT, par joueur et objet. */
    private final Map<String, Double> moneyCarry = new HashMap<String, Double>();

    public SaleHooks(Logger logger) {
        this.logger = logger;
    }

    public void register() {
        questOf = null;
        krebbsSale = null;
        if (Bukkit.getPluginManager().isPluginEnabled("KoriaQuest")) {
            try {
                Class<?> api = Class.forName("fr.koriamc.quest.api.KoriaQuestAPI");
                Class<?> ctx = Class.forName("fr.koriamc.quest.objective.ProgressContext");
                questOf = ctx.getMethod("of", Player.class, String.class, long.class);
                questItem = ctx.getMethod("item", ItemStack.class);
                questProgress = api.getMethod("progress", ctx);
                logger.info("KoriaQuest détecté : les ventes comptent pour VENDRE et VENDRE_ARGENT.");
            } catch (Throwable t) {
                questOf = null;
                logger.warning("KoriaQuest présent mais incompatible : " + t);
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("KoriaKrebbs")) {
            try {
                krebbsSale = Class.forName("fr.koriamc.krebbs.api.KoriaKrebbsAPI")
                        .getMethod("progressSale", Player.class, ItemStack.class, int.class);
                logger.info("KoriaKrebbs détecté : les ventes comptent pour les outils du Toucan.");
            } catch (Throwable t) {
                krebbsSale = null;
                logger.warning("KoriaKrebbs présent mais sans progressSale : " + t);
            }
        }
    }

    /** Une vente : {@code amount} objets comme {@code item}, pour {@code money} $. */
    public void onSale(Player player, ItemStack item, int amount, double money) {
        if (questOf != null) {
            try {
                questProgress.invoke(null, questItem.invoke(questOf.invoke(null, player, "VENDRE", (long) amount), item));
                String key = player.getUniqueId() + ":" + item.getType() + ":" + item.getDurability();
                Double carried = moneyCarry.get(key);
                double total = money + (carried == null ? 0 : carried);
                long whole = (long) Math.floor(total);
                moneyCarry.put(key, total - whole);
                if (whole > 0) {
                    questProgress.invoke(null, questItem.invoke(questOf.invoke(null, player, "VENDRE_ARGENT", whole), item));
                }
            } catch (Throwable t) {
                questOf = null;
                logger.warning("Progression KoriaQuest impossible, intégration désactivée : " + t);
            }
        }
        if (krebbsSale != null) {
            try {
                krebbsSale.invoke(null, player, item, amount);
            } catch (Throwable t) {
                krebbsSale = null;
                logger.warning("Progression KoriaKrebbs impossible, intégration désactivée : " + t);
            }
        }
    }

    public void forget(UUID player) {
        String prefix = player + ":";
        moneyCarry.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
