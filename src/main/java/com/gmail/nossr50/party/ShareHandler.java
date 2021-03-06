package com.gmail.nossr50.party;

import java.util.List;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.config.party.ItemWeightConfig;
import com.gmail.nossr50.datatypes.party.ItemShareType;
import com.gmail.nossr50.datatypes.party.Party;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.commands.CommandUtils;
import com.gmail.nossr50.util.player.UserManager;

public final class ShareHandler {
    public enum ShareMode {
        NONE,
        EQUAL,
        RANDOM;

        public static ShareMode getShareMode(String string) {
            try {
                return valueOf(string);
            }
            catch (IllegalArgumentException ex) {
                if (string.equalsIgnoreCase("even")) {
                    return EQUAL;
                }
                else if (CommandUtils.shouldDisableToggle(string)) {
                    return NONE;
                }

                return null;
            }
        }
    };

    private ShareHandler() {}

    private static List<Player> nearMembers;
    private static int partySize;

    /**
     * Distribute Xp amongst party members.
     *
     * @param xp Xp without party sharing
     * @param mcMMOPlayer Player initiating the Xp gain
     * @param skillType Skill being used
     * @return True is the xp has been shared
     */
    public static boolean handleXpShare(float xp, McMMOPlayer mcMMOPlayer, SkillType skillType) {
        Party party = mcMMOPlayer.getParty();

        switch (party.getXpShareMode()) {
            case EQUAL:
                Player player = mcMMOPlayer.getPlayer();
                nearMembers = PartyManager.getNearMembers(player, party, Config.getInstance().getPartyShareRange());

                if (nearMembers.isEmpty()) {
                    return false;
                }

                double partySize = nearMembers.size() + 1;
                double shareBonus = Math.min(Config.getInstance().getPartyShareBonusBase() + partySize * Config.getInstance().getPartyShareBonusIncrease(), Config.getInstance().getPartyShareBonusCap());
                float splitXp = (float) (xp / partySize * shareBonus);

                for (Player member : nearMembers) {
                    UserManager.getPlayer(member).beginUnsharedXpGain(skillType, splitXp);
                }

                mcMMOPlayer.beginUnsharedXpGain(skillType, splitXp);
                return true;

            case NONE:
            default:
                return false;
        }
    }

    /**
     * Distribute Items amongst party members.
     *
     * @param item Item that will get shared
     * @param mcMMOPlayer Player who picked up the item
     * @return True if the item has been shared
     */
    public static boolean handleItemShare(Item drop, McMMOPlayer mcMMOPlayer) {
        ItemStack itemStack = drop.getItemStack();
        ItemShareType dropType = ItemShareType.getShareType(itemStack);

        if (dropType == null) {
            return false;
        }

        Party party = mcMMOPlayer.getParty();

        if (!party.sharingDrops(dropType)) {
            return false;
        }

        ShareMode shareMode = party.getItemShareMode();

        if (shareMode == ShareMode.NONE) {
            return false;
        }

        Player player = mcMMOPlayer.getPlayer();

        nearMembers = PartyManager.getNearMembers(player, party, Config.getInstance().getPartyShareRange());

        if (nearMembers.isEmpty()) {
            return false;
        }

        Player winningPlayer = null;
        ItemStack newStack = itemStack.clone();

        nearMembers.add(player);
        partySize = nearMembers.size();

        drop.remove();
        newStack.setAmount(1);

        switch (shareMode) {
            case EQUAL:
                int itemWeight = ItemWeightConfig.getInstance().getItemWeight(itemStack.getType());

                for (int i = 0; i < itemStack.getAmount(); i++) {
                    int highestRoll = 0;

                    for (Player member : nearMembers) {
                        McMMOPlayer mcMMOMember = UserManager.getPlayer(member);
                        int itemShareModifier = mcMMOMember.getItemShareModifier();
                        int diceRoll = Misc.getRandom().nextInt(itemShareModifier);

                        if (diceRoll <= highestRoll) {
                            mcMMOMember.setItemShareModifier(itemShareModifier + itemWeight);
                            continue;
                        }

                        highestRoll = diceRoll;

                        if (winningPlayer != null) {
                            McMMOPlayer mcMMOWinning = UserManager.getPlayer(winningPlayer);
                            mcMMOWinning.setItemShareModifier(mcMMOWinning.getItemShareModifier() + itemWeight);
                        }

                        winningPlayer = member;
                    }

                    McMMOPlayer mcMMOTarget = UserManager.getPlayer(winningPlayer);
                    mcMMOTarget.setItemShareModifier(mcMMOTarget.getItemShareModifier() - itemWeight);
                    awardDrop(winningPlayer, newStack);
                }

                return true;

            case RANDOM:
                for (int i = 0; i < itemStack.getAmount(); i++) {
                    winningPlayer = nearMembers.get(Misc.getRandom().nextInt(partySize));
                    awardDrop(winningPlayer, newStack);
                }

                return true;

            default:
                return false;
        }
    }

    private static void awardDrop(Player winningPlayer, ItemStack drop) {
        if (winningPlayer.getInventory().addItem(drop).size() != 0) {
            winningPlayer.getWorld().dropItemNaturally(winningPlayer.getLocation(), drop);
        }

        winningPlayer.updateInventory();
    }
}
