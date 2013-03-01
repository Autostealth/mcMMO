package com.gmail.nossr50.util.skills;

import org.bukkit.Material;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.inventory.ItemStack;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.AbilityType;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.events.fake.FakeEntityDamageByEntityEvent;
import com.gmail.nossr50.events.fake.FakeEntityDamageEvent;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.party.PartyManager;
import com.gmail.nossr50.runnables.skills.AwardCombatXpTask;
import com.gmail.nossr50.runnables.skills.BleedTimerTask;
import com.gmail.nossr50.skills.SkillManagerStore;
import com.gmail.nossr50.skills.axes.AxeManager;
import com.gmail.nossr50.skills.swords.Swords;
import com.gmail.nossr50.skills.taming.Taming;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.ModUtils;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.UserManager;

public final class CombatUtils {
    private CombatUtils() {}

    /**
     * Apply combat modifiers and process and XP gain.
     *
     * @param event The event to run the combat checks on.
     */
    public static void combatChecks(EntityDamageByEntityEvent event, Entity attacker, LivingEntity target) {
        boolean targetIsPlayer = (target.getType() == EntityType.PLAYER);
        boolean targetIsTamedPet = (target instanceof Tameable) ? ((Tameable) target).isTamed() : false;
        Entity damager = event.getDamager();

        if (attacker instanceof Player && damager.getType() == EntityType.PLAYER) {
            Player player = (Player) attacker;

            if (Misc.isNPCEntity(player)) {
                return;
            }

            if (target instanceof Tameable && isFriendlyPet(player, (Tameable) target)) {
                return;
            }

            ItemStack heldItem = player.getItemInHand();
            Material heldItemType = heldItem.getType();

            if (ItemUtils.isSword(heldItem)) {
                if (targetIsPlayer || targetIsTamedPet) {
                    if (!SkillType.SWORDS.getPVPEnabled()) {
                        return;
                    }
                }
                else if (!SkillType.SWORDS.getPVEEnabled()) {
                    return;
                }

                if (Permissions.skillEnabled(player, SkillType.SWORDS)) {
                    McMMOPlayer mcMMOPlayer = UserManager.getPlayer(player);
                    PlayerProfile profile = mcMMOPlayer.getProfile();
                    String playerName = player.getName();
                    boolean canSerratedStrike = Permissions.serratedStrikes(player); // So we don't have to check the same permission twice

                    if (profile.getToolPreparationMode(ToolType.SWORD) && canSerratedStrike) {
                        SkillUtils.abilityCheck(player, SkillType.SWORDS);
                    }

                    if (Permissions.bleed(player)) {
                        SkillManagerStore.getInstance().getSwordsManager(playerName).bleedCheck(target);
                    }

                    if (profile.getAbilityMode(AbilityType.SERRATED_STRIKES) && canSerratedStrike) {
                        SkillManagerStore.getInstance().getSwordsManager(playerName).serratedStrikes(target, event.getDamage());
                    }

                    startGainXp(mcMMOPlayer, target, SkillType.SWORDS);
                }
            }
            else if (ItemUtils.isAxe(heldItem)) {
                if (((targetIsPlayer || targetIsTamedPet) && !SkillType.AXES.getPVPEnabled()) || (!targetIsPlayer && !targetIsTamedPet && !SkillType.AXES.getPVEEnabled())) {
                    return;
                }

                if (Permissions.skillEnabled(player, SkillType.AXES)) {
                    AxeManager axeManager = SkillManagerStore.getInstance().getAxeManager(player.getName());

                    if (axeManager.canActivateAbility()) {
                        SkillUtils.abilityCheck(player, SkillType.AXES);
                    }

                    if (axeManager.canUseAxeMastery()) {
                        event.setDamage(axeManager.axeMasteryCheck(event.getDamage()));
                    }

                    if (axeManager.canCriticalHit(target)) {
                        event.setDamage(axeManager.criticalHitCheck(target, event.getDamage()));
                    }

                    if (axeManager.canImpact(target)) {
                        axeManager.impactCheck(target);
                    }
                    else if (axeManager.canGreaterImpact(target)) {
                        event.setDamage(axeManager.greaterImpactCheck(target, event.getDamage()));
                    }

                    if (axeManager.canUseSkullSplitter(target)) {
                        axeManager.skullSplitterCheck(target, event.getDamage());
                    }

                    startGainXp(axeManager.getMcMMOPlayer(), target, SkillType.AXES);
                }
            }
            else if (heldItemType == Material.AIR) {
                if (targetIsPlayer || targetIsTamedPet) {
                    if (!SkillType.UNARMED.getPVPEnabled()) {
                        return;
                    }
                }
                else if (!SkillType.UNARMED.getPVEEnabled()) {
                    return;
                }

                if (Permissions.skillEnabled(player, SkillType.UNARMED)) {
                    McMMOPlayer mcMMOPlayer = UserManager.getPlayer(player);
                    PlayerProfile profile = mcMMOPlayer.getProfile();
                    String playerName = player.getName();

                    boolean canBerserk = Permissions.berserk(player); // So we don't have to check the same permission twice

                    if (profile.getToolPreparationMode(ToolType.FISTS) && canBerserk) {
                        SkillUtils.abilityCheck(player, SkillType.UNARMED);
                    }

                    if (Permissions.bonusDamage(player, SkillType.UNARMED)) {
                        event.setDamage(SkillManagerStore.getInstance().getUnarmedManager(playerName).ironArmCheck(event.getDamage()));
                    }

                    if (profile.getAbilityMode(AbilityType.BERSERK) && canBerserk) {
                        event.setDamage(SkillManagerStore.getInstance().getUnarmedManager(playerName).berserkDamage(event.getDamage()));
                    }

                    if (target instanceof Player && Permissions.disarm(player)) {
                        Player defender = (Player) target;

                        if (defender.getItemInHand().getType() != Material.AIR) {
                            SkillManagerStore.getInstance().getUnarmedManager(playerName).disarmCheck((Player) target);
                        }
                    }

                    startGainXp(mcMMOPlayer, target, SkillType.UNARMED);
                }
            }
            else if (heldItemType == Material.BONE && target instanceof Tameable && Permissions.beastLore(player)) {
                SkillManagerStore.getInstance().getTamingManager(player.getName()).beastLore(target);
            }
        }

        switch (damager.getType()) {
            case WOLF:
                Wolf wolf = (Wolf) damager;

                if (wolf.isTamed() && wolf.getOwner() instanceof Player) {
                    Player master = (Player) wolf.getOwner();

                    if (Misc.isNPCEntity(master)) {
                        return;
                    }

                    if (targetIsPlayer || targetIsTamedPet) {
                        if (!SkillType.TAMING.getPVPEnabled()) {
                            return;
                        }
                    }
                    else if (!SkillType.TAMING.getPVEEnabled()) {
                        return;
                    }

                    if (Permissions.skillEnabled(master, SkillType.TAMING)) {
                        McMMOPlayer mcMMOPlayer = UserManager.getPlayer(master);
                        int skillLevel = SkillManagerStore.getInstance().getTamingManager(master.getName()).getSkillLevel();

                        if (skillLevel >= Taming.fastFoodServiceUnlockLevel && Permissions.fastFoodService(master)) {
                            SkillManagerStore.getInstance().getTamingManager(master.getName()).fastFoodService(wolf, event.getDamage());
                        }

                        if (skillLevel >= Taming.sharpenedClawsUnlockLevel && Permissions.sharpenedClaws(master)) {
                            SkillManagerStore.getInstance().getTamingManager(master.getName()).sharpenedClaws(event);
                        }

                        if (Permissions.gore(master)) {
                            SkillManagerStore.getInstance().getTamingManager(master.getName()).gore(event);
                        }

                        startGainXp(mcMMOPlayer, target, SkillType.TAMING);
                    }
                }

                break;

            case ARROW:
                LivingEntity shooter = ((Arrow) damager).getShooter();

                /* Break instead of return due to Dodge/Counter/Deflect abilities */
                if (shooter == null || !(shooter instanceof Player)) {
                    break;
                }

                if (targetIsPlayer || targetIsTamedPet) {
                    if (!SkillType.ARCHERY.getPVPEnabled()) {
                        return;
                    }
                }
                else if (!SkillType.ARCHERY.getPVEEnabled()) {
                    return;
                }

                archeryCheck((Player) shooter, target, event);
                break;

            default:
                break;
        }

        if (targetIsPlayer) {
            Player player = (Player) target;

            if (Misc.isNPCEntity(player)) {
                return;
            }

            ItemStack heldItem = player.getItemInHand();

            if (SkillManagerStore.getInstance().getAcrobaticsManager(player.getName()).canDodge(damager)) {
                event.setDamage(SkillManagerStore.getInstance().getAcrobaticsManager(player.getName()).dodgeCheck(event.getDamage()));
            }

            if (damager instanceof Player) {
                if (SkillType.SWORDS.getPVPEnabled() && ItemUtils.isSword(heldItem) && Permissions.counterAttack(player)) {
                    SkillManagerStore.getInstance().getSwordsManager(player.getName()).counterAttackChecks((LivingEntity) damager, event.getDamage());
                }
            }
            else {
                if (SkillType.SWORDS.getPVEEnabled() && damager instanceof LivingEntity && ItemUtils.isSword(heldItem) && Permissions.counterAttack(player)) {
                    SkillManagerStore.getInstance().getSwordsManager(player.getName()).counterAttackChecks((LivingEntity) damager, event.getDamage());
                }
            }
        }
    }

    /**
     * Attempt to damage target for value dmg with reason CUSTOM
     *
     * @param target LivingEntity which to attempt to damage
     * @param dmg Amount of damage to attempt to do
     */
    public static void dealDamage(LivingEntity target, int dmg) {
        dealDamage(target, dmg, EntityDamageEvent.DamageCause.CUSTOM);
    }

    /**
     * Apply Area-of-Effect ability actions.
     *
     * @param attacker The attacking player
     * @param target The defending entity
     * @param damage The initial damage amount
     * @param type The type of skill being used
     */
    public static void applyAbilityAoE(Player attacker, LivingEntity target, int damage, SkillType type) {
        int numberOfTargets = Misc.getTier(attacker.getItemInHand()); // The higher the weapon tier, the more targets you hit
        int damageAmount = damage;

        if (damageAmount < 1) {
            damageAmount = 1;
        }

        for (Entity entity : target.getNearbyEntities(2.5, 2.5, 2.5)) {
            if (Misc.isNPCEntity(entity) || !(entity instanceof LivingEntity) || !shouldBeAffected(attacker, entity)) {
                continue;
            }

            if (numberOfTargets <= 0) {
                break;
            }

            PlayerAnimationEvent armswing = new PlayerAnimationEvent(attacker);
            mcMMO.p.getServer().getPluginManager().callEvent(armswing);

            switch (type) {
                case SWORDS:
                    if (entity instanceof Player) {
                        ((Player) entity).sendMessage(LocaleLoader.getString("Swords.Combat.SS.Struck"));
                    }

                    BleedTimerTask.add((LivingEntity) entity, Swords.serratedStrikesBleedTicks);

                    break;

                case AXES:
                    if (entity instanceof Player) {
                        ((Player) entity).sendMessage(LocaleLoader.getString("Axes.Combat.Cleave.Struck"));
                    }

                    break;

                default:
                    break;
            }

            dealDamage((LivingEntity) entity, damageAmount, attacker);
            numberOfTargets--;
        }
    }

    /**
     * Start the task that gives combat XP.
     *
     * @param mcMMOPlayer The attacking player
     * @param target The defending entity
     * @param skillType The skill being used
     */
    public static void startGainXp(McMMOPlayer mcMMOPlayer, LivingEntity target, SkillType skillType) {
        double baseXP = 0;

        if (target instanceof Player) {
            if (!Config.getInstance().getExperienceGainsPlayerVersusPlayerEnabled()) {
                return;
            }

            Player defender = (Player) target;

            if (System.currentTimeMillis() >= UserManager.getPlayer(defender).getProfile().getRespawnATS() + 5) {
                baseXP = 20 * Config.getInstance().getPlayerVersusPlayerXP();
            }
        }
        else if (!target.hasMetadata(mcMMO.entityMetadataKey)) {
            if (target instanceof Animals) {
                if (ModUtils.isCustomEntity(target)) {
                    baseXP = ModUtils.getCustomEntity(target).getXpMultiplier();
                }
                else {
                    baseXP = Config.getInstance().getAnimalsXP();
                }
            }
            else {
                EntityType type = target.getType();

                switch (type) {
                    case BAT:
                        baseXP = Config.getInstance().getAnimalsXP();
                        break;

                    case BLAZE:
                    case CAVE_SPIDER:
                    case CREEPER:
                    case ENDER_DRAGON:
                    case ENDERMAN:
                    case GHAST:
                    case GIANT:
                    case MAGMA_CUBE:
                    case PIG_ZOMBIE:
                    case SILVERFISH:
                    case SLIME:
                    case SPIDER:
                    case WITCH:
                    case WITHER:
                    case ZOMBIE:
                        baseXP = Config.getInstance().getCombatXP(type);
                        break;

                    // Temporary workaround for custom entities
                    case UNKNOWN:
                        baseXP = 1.0;
                        break;

                    case SKELETON:
                        switch (((Skeleton) target).getSkeletonType()) {
                            case WITHER:
                                baseXP = Config.getInstance().getWitherSkeletonXP();
                                break;
                            default:
                                baseXP = Config.getInstance().getCombatXP(type);
                                break;
                        }
                        break;

                    case IRON_GOLEM:
                        if (!((IronGolem) target).isPlayerCreated()) {
                            baseXP = Config.getInstance().getCombatXP(type);
                        }
                        break;

                    default:
                        if (ModUtils.isCustomEntity(target)) {
                            baseXP = ModUtils.getCustomEntity(target).getXpMultiplier();
                        }
                        break;
                }
            }

            baseXP *= 10;
        }

        if (baseXP != 0) {
            mcMMO.p.getServer().getScheduler().scheduleSyncDelayedTask(mcMMO.p, new AwardCombatXpTask(mcMMOPlayer, skillType, baseXP, target), 0);
        }
    }

    /**
     * Check to see if the given LivingEntity should be affected by a combat ability.
     *
     * @param player The attacking Player
     * @param entity The defending Entity
     * @return true if the Entity should be damaged, false otherwise.
     */
    public static boolean shouldBeAffected(Player player, Entity entity) {
        if (entity instanceof Player) {
            Player defender = (Player) entity;

            if (!defender.getWorld().getPVP() || defender == player || UserManager.getPlayer(defender).getProfile().getGodMode()) {
                return false;
            }

            if (PartyManager.inSameParty(player, defender) && !(Permissions.friendlyFire(player) && Permissions.friendlyFire(defender))) {
                return false;
            }

            // It may seem a bit redundant but we need a check here to prevent bleed from being applied in applyAbilityAoE()
            EntityDamageEvent ede = new FakeEntityDamageByEntityEvent(player, entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1);
            mcMMO.p.getServer().getPluginManager().callEvent(ede);

            if (ede.isCancelled()) {
                return false;
            }
        }
        else if (entity instanceof Tameable) {
            if (isFriendlyPet(player, (Tameable) entity)) {
                // isFriendlyPet ensures that the Tameable is: Tamed, owned by a player, and the owner is in the same party
                // So we can make some assumptions here, about our casting and our check
                Player owner = (Player) ((Tameable) entity).getOwner();
                if (!(Permissions.friendlyFire(player) && Permissions.friendlyFire(owner))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks to see if an entity is currently invincible.
     *
     * @param entity The {@link LivingEntity} to check
     * @param eventDamage The damage from the event the entity is involved in
     * @return true if the entity is invincible, false otherwise
     */
    public static boolean isInvincible(LivingEntity entity, int eventDamage) {

        /*
         * So apparently if you do more damage to a LivingEntity than its last damage int you bypass the invincibility.
         * So yeah, this is for that.
         */
        if ((entity.getNoDamageTicks() > entity.getMaximumNoDamageTicks() / 2.0F) && (eventDamage <= entity.getLastDamage())) {
            return true;
        }

        return false;
    }

    /**
     * Checks to see if an entity is currently friendly toward a given player.
     *
     * @param attacker The player to check.
     * @param pet The entity to check.
     * @return true if the entity is friendly, false otherwise
     */
    public static boolean isFriendlyPet(Player attacker, Tameable pet) {
        if (pet.isTamed()) {
            AnimalTamer tamer = pet.getOwner();

            if (tamer instanceof Player) {
                Player owner = (Player) tamer;

                if (owner == attacker || PartyManager.inSameParty(attacker, owner)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Process archery abilities.
     *
     * @param shooter The player shooting
     * @param target The defending entity
     * @param event The event to run the archery checks on.
     */
    private static void archeryCheck(Player shooter, LivingEntity target, EntityDamageByEntityEvent event) {
        if (Misc.isNPCEntity(shooter)) {
            return;
        }

        if (Permissions.skillEnabled(shooter, SkillType.ARCHERY)) {
            String playerName = shooter.getName();

            if (SkillManagerStore.getInstance().getArcheryManager(playerName).canSkillShot()) {
                event.setDamage(SkillManagerStore.getInstance().getArcheryManager(playerName).skillShotCheck(event.getDamage()));
            }

            if (target instanceof Player && SkillType.UNARMED.getPVPEnabled() && ((Player) target).getItemInHand().getType() == Material.AIR && Permissions.arrowDeflect((Player) target)) {
                event.setCancelled(SkillManagerStore.getInstance().getUnarmedManager(((Player) target).getName()).deflectCheck());

                if (event.isCancelled()) {
                    return;
                }
            }

            if (SkillManagerStore.getInstance().getArcheryManager(playerName).canDaze(target)) {
                event.setDamage(SkillManagerStore.getInstance().getArcheryManager(playerName).dazeCheck((Player) target, event.getDamage()));
            }

            if (SkillManagerStore.getInstance().getArcheryManager(playerName).canTrackArrows()) {
                SkillManagerStore.getInstance().getArcheryManager(playerName).trackArrows(target);
            }

            SkillManagerStore.getInstance().getArcheryManager(playerName).distanceXpBonus(target);
            startGainXp(UserManager.getPlayer(shooter), target, SkillType.ARCHERY);
        }
    }

    /**
     * Attempt to damage target for value dmg with reason cause
     *
     * @param target LivingEntity which to attempt to damage
     * @param dmg Amount of damage to attempt to do
     * @param cause DamageCause to pass to damage event
     */
    private static void dealDamage(LivingEntity target, int dmg, DamageCause cause) {
        if (Config.getInstance().getEventCallbackEnabled()) {
            EntityDamageEvent ede = new FakeEntityDamageEvent(target, cause, dmg);
            mcMMO.p.getServer().getPluginManager().callEvent(ede);

            if (ede.isCancelled()) {
                return;
            }

            target.damage(ede.getDamage());
        }
        else {
            target.damage(dmg);
        }
    }

    /**
     * Attempt to damage target for value dmg with reason ENTITY_ATTACK with damager attacker
     *
     * @param target LivingEntity which to attempt to damage
     * @param dmg Amount of damage to attempt to do
     * @param attacker Player to pass to event as damager
     */
    private static void dealDamage(LivingEntity target, int dmg, Player attacker) {
        if (Config.getInstance().getEventCallbackEnabled()) {
            EntityDamageEvent ede = new FakeEntityDamageByEntityEvent(attacker, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, dmg);
            mcMMO.p.getServer().getPluginManager().callEvent(ede);

            if (ede.isCancelled()) {
                return;
            }

            target.damage(ede.getDamage());
        }
        else {
            target.damage(dmg);
        }
    }
}
