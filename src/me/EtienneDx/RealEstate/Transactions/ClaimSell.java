package me.EtienneDx.RealEstate.Transactions;

import org.bukkit.entity.Player;
import com.earth2me.essentials.User;
import me.EtienneDx.RealEstate.Messages;
import me.EtienneDx.RealEstate.RealEstate;
import me.EtienneDx.RealEstate.Utils;
import me.EtienneDx.RealEstate.RealEstateSign;
import me.EtienneDx.RealEstate.ClaimAPI.IClaim;
import net.md_5.bungee.api.ChatColor;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;

/**
 * Represents a sell transaction for a claim.
 * <p>
 * This transaction is used when a claim is put up for sale. It handles updating the sign display with
 * sale information, processing the transaction, and transferring ownership.
 * </p>
 */
public class ClaimSell extends ClaimTransaction {

    /**
     * Constructs a new ClaimSell transaction.
     *
     * @param claim the claim being sold
     * @param player the player initiating the sale
     * @param price the sale price
     * @param sign the location of the sign representing the sale
     */
    public ClaimSell(IClaim claim, Player player, double price, Location sign) {
        super(claim, player, price, sign);
    }
    
    /**
     * Constructs a ClaimSell transaction from a serialized map.
     *
     * @param map the map containing serialized data for this sale transaction
     */
    public ClaimSell(Map<String, Object> map) {
        super(map);
    }

    /**
     * Updates the sign associated with this sale transaction.
     * <p>
     * If the sign is valid, it updates each line with the sale information (header, sale type, owner,
     * and price). If the sign is not valid, the transaction is canceled.
     * </p>
     *
     * @return false to indicate that the transaction remains active.
     */
    @Override
    public boolean update() {
        if (sign.getBlock().getState() instanceof Sign) {
            RealEstateSign s = new RealEstateSign((Sign) sign.getBlock().getState());
            s.setLine(0, Messages.getMessage(RealEstate.instance.config.cfgSignsHeader, false));
            s.setLine(1, ChatColor.DARK_GREEN + RealEstate.instance.config.cfgReplaceSell);
            s.setLine(2, owner != null ? Utils.getSignString(Bukkit.getOfflinePlayer(owner).getName()) : "SERVER");
            if (RealEstate.instance.config.cfgUseCurrencySymbol) {
                if (RealEstate.instance.config.cfgUseDecimalCurrency == false) {
                    s.setLine(3, RealEstate.instance.config.cfgCurrencySymbol + " " + (int) Math.round(price));
                } else {
                    s.setLine(3, RealEstate.instance.config.cfgCurrencySymbol + " " + price);
                }
            } else {
                if (RealEstate.instance.config.cfgUseDecimalCurrency == false) {
                    s.setLine(3, (int) Math.round(price) + " " + RealEstate.econ.currencyNamePlural());
                } else {
                    s.setLine(3, price + " " + RealEstate.econ.currencyNamePlural());
                }
            }
            s.update(true);
        } else {
            RealEstate.transactionsStore.cancelTransaction(this);
        }
        return false;
    }

    /**
     * Attempts to cancel the sale transaction.
     * <p>
     * In the case of a sale, the transaction can only be canceled by removing the sign.
     * </p>
     *
     * @param p the player attempting cancellation (unused in this context)
     * @param force if true, forces cancellation
     * @return true after the transaction is canceled
     */
    @Override
    public boolean tryCancelTransaction(Player p, boolean force) {
        // Nothing special here; this transaction can only be waiting for a buyer.
        RealEstate.transactionsStore.cancelTransaction(this);
        return true;
    }

    /**
     * Processes player interaction with the sale sign.
     * <p>
     * Checks that the claim exists, that the player has permission to buy the claim, and that the
     * player has enough claim blocks if required. If the payment succeeds, ownership is transferred
     * and appropriate messages are sent.
     * </p>
     *
     * @param player the player interacting with the sale sign
     */
    @Override
    public void interact(Player player) {
        IClaim claim = RealEstate.claimAPI.getClaimAt(sign);
        if (claim == null || claim.isWilderness()) {
            Messages.sendMessage(player, RealEstate.instance.messages.msgErrorClaimDoesNotExist);
            RealEstate.transactionsStore.cancelTransaction(claim);
            return;
        }
        String claimType = claim.isParentClaim() ? "claim" : "subclaim";
        String claimTypeDisplay = claim.isParentClaim() ? 
            RealEstate.instance.messages.keywordClaim : RealEstate.instance.messages.keywordSubclaim;
        
        if (player.getUniqueId().equals(owner)) {
            Messages.sendMessage(player, RealEstate.instance.messages.msgErrorClaimAlreadyOwner, claimTypeDisplay);
            return;
        }
        if (claim.isParentClaim() && owner != null && !owner.equals(claim.getOwner())) {
            Messages.sendMessage(player, RealEstate.instance.messages.msgErrorClaimNotSoldByOwner, claimTypeDisplay);
            RealEstate.transactionsStore.cancelTransaction(claim);
            return;
        }
        if (!player.hasPermission("realestate." + claimType + ".buy")) {
            Messages.sendMessage(player, RealEstate.instance.messages.msgErrorClaimNoBuyPermission, claimTypeDisplay);
            return;
        }
        // Check for sufficient claim blocks if necessary.
        if (claimType.equalsIgnoreCase("claim") && !RealEstate.instance.config.cfgTransferClaimBlocks &&
                RealEstate.claimAPI.getPlayerData(player.getUniqueId()).getRemainingClaimBlocks() < claim.getArea()) {
            int remaining = RealEstate.claimAPI.getPlayerData(player.getUniqueId()).getRemainingClaimBlocks();
            int area = claim.getArea();
            Messages.sendMessage(player, RealEstate.instance.messages.msgErrorClaimNoClaimBlocks,
                area + "", remaining + "", (area - remaining) + "");
            return;			
        }
        // Process payment and transfer ownership.
        if (Utils.makePayment(owner, player.getUniqueId(), price, false, true)) { // Payment succeeded
            Utils.transferClaim(claim, player.getUniqueId(), owner);
            // Log transaction if claim ownership transfer is successful.
            if (claim.isSubClaim() || claim.getOwner().equals(player.getUniqueId())) {
                String location = "[" + player.getLocation().getWorld() + ", " +
                    "X: " + player.getLocation().getBlockX() + ", " +
                    "Y: " + player.getLocation().getBlockY() + ", " +
                    "Z: " + player.getLocation().getBlockZ() + "]";
                Messages.sendMessage(player, RealEstate.instance.messages.msgInfoClaimBuyerSold,
                        claimTypeDisplay, RealEstate.econ.format(price));
                RealEstate.instance.addLogEntry(
                        "[" + RealEstate.transactionsStore.dateFormat.format(RealEstate.transactionsStore.date) + "] " +
                        player.getName() + " has purchased a " + claimType + " at " +
                        "[" + player.getLocation().getWorld() + ", " +
                        "X: " + player.getLocation().getBlockX() + ", " +
                        "Y: " + player.getLocation().getBlockY() + ", " +
                        "Z: " + player.getLocation().getBlockZ() + "] " +
                        "Price: " + price + " " + RealEstate.econ.currencyNamePlural());
                if (RealEstate.instance.config.cfgMessageOwner && owner != null) {
                    OfflinePlayer oldOwner = Bukkit.getOfflinePlayer(owner);
                    if (oldOwner.isOnline()) {
                        Messages.sendMessage(oldOwner.getPlayer(), RealEstate.instance.messages.msgInfoClaimOwnerSold,
                                player.getName(), claimTypeDisplay, RealEstate.econ.format(price), location);
                    } else if (RealEstate.instance.config.cfgMailOffline && RealEstate.ess != null) {
                        User u = RealEstate.ess.getUser(owner);
                        u.addMail(Messages.getMessage(RealEstate.instance.messages.msgInfoClaimOwnerSold,
                                player.getName(), claimTypeDisplay, RealEstate.econ.format(price), location));
                    }
                }
            } else {
                Messages.sendMessage(player, RealEstate.instance.messages.msgErrorUnexpected);
                return;
            }
            RealEstate.transactionsStore.cancelTransaction(claim);
        }
    }

    /**
     * Sends a detailed preview of the sale transaction to a player.
     * <p>
     * The preview includes the sale header, sale details (type and price), and the owner information.
     * </p>
     *
     * @param player the player to receive the preview
     */
    @Override
    public void preview(Player player) {
        IClaim claim = RealEstate.claimAPI.getClaimAt(sign);
        if (player.hasPermission("realestate.info")) {
            String claimType = claim.isParentClaim() ? "claim" : "subclaim";
            String claimTypeDisplay = claim.isParentClaim() ?
                RealEstate.instance.messages.keywordClaim : RealEstate.instance.messages.keywordSubclaim;
            String msg = Messages.getMessage(RealEstate.instance.messages.msgInfoClaimInfoSellHeader) + "\n";
            msg += Messages.getMessage(RealEstate.instance.messages.msgInfoClaimInfoSellGeneral,
                    claimTypeDisplay, RealEstate.econ.format(price)) + "\n";
            if (claimType.equalsIgnoreCase("claim")) {
                msg += Messages.getMessage(RealEstate.instance.messages.msgInfoClaimInfoOwner,
                        claim.getOwnerName()) + "\n";
            } else {
                msg += Messages.getMessage(RealEstate.instance.messages.msgInfoClaimInfoMainOwner,
                        claim.getParent().getOwnerName()) + "\n";
                msg += Messages.getMessage(RealEstate.instance.messages.msgInfoClaimInfoNote) + "\n";
            }
            Messages.sendMessage(player, msg, false);
        } else {
            Messages.sendMessage(player, RealEstate.instance.messages.msgErrorClaimNoInfoPermission);
        }
    }

    /**
     * Sends a one-line summary of the sale transaction to a command sender.
     *
     * @param cs the command sender to receive the sale summary
     */
    @Override
    public void msgInfo(CommandSender cs) {
        IClaim claim = RealEstate.claimAPI.getClaimAt(sign);
        if (claim == null || claim.isWilderness()) {
            tryCancelTransaction(null, true);
            return;
        }
        String location = "[" + claim.getWorld().getName() + ", " +
            "X: " + claim.getX() + ", " +
            "Y: " + claim.getY() + ", " +
            "Z: " + claim.getZ() + "]";
        Messages.sendMessage(cs, RealEstate.instance.messages.msgInfoClaimInfoSellOneline,
                claim.getArea() + "", location, RealEstate.econ.format(price));
    }
}
