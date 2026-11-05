package fr.koriamc.shop.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Un joueur a vendu des objets au shop (après le paiement). Un évènement par type d'objet vendu.
 */
public final class ShopSellEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack item;
    private final int amount;
    private final double money;

    public ShopSellEvent(Player player, ItemStack item, int amount, double money) {
        this.player = player;
        this.item = item;
        this.amount = amount;
        this.money = money;
    }

    public Player getPlayer() {
        return player;
    }

    /** Type d'objet vendu (quantité 1). */
    public ItemStack getItem() {
        return item.clone();
    }

    public int getAmount() {
        return amount;
    }

    /** Montant gagné en $. */
    public double getMoney() {
        return money;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
