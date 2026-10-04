package com.stella.tower;

import com.stella.tower.RuneStats.Stat;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * An armor rune's signature, by its element, for a player who wears it. Each worn piece of the element adds
 * a quarter of the full value, and the caps apply to the sum. Fire and Dark answer an attacker, Ice
 * freezes one, Water regenerates, Lightning stores a charge per blow taken, Wind dodges, Void resists
 * magic, Holy resists undead.
 */
public final class RuneGuards {
	public static final int REGEN_INTERVAL_TICKS = 20;
	/** One tenth of the percent heals each interval, so the percent reads per ten seconds. */
	public static final double REGEN_PER_INTERVAL = 0.1;
	public static final int BURN_SECONDS = 3;
	public static final int FREEZE_TICKS = 20;
	public static final int FREEZE_SLOWNESS = 6;

	/** Set while a blow carries stored charges, so they are spent only once the blow has landed. */
	private static final AttachmentType<Boolean> KINDLING = AttachmentRegistry.create(StellaTower.id("rune_kindling"));

	private RuneGuards() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !dodges(entity, source));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && taken > 0.0F && !RuneEffects.dealing() && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
				answer(level, player, source, taken);
			}
			// Stored charges are spent by the blow that carried them, once that blow has landed.
			if (source.getEntity() instanceof Player striker && striker != entity && striker.hasAttached(KINDLING)) {
				striker.removeAttached(KINDLING);
				if (!blocked && taken > 0.0F) {
					RuneEffects.spendCharges(striker);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(RuneGuards::tick);
	}

	/** What a worn set holds of this element: a quarter for each piece, and 0 for a mob, which carries none. */
	public static double share(final LivingEntity wearer, final TowerElement element) {
		if (!(wearer instanceof Player)) {
			return 0.0;
		}
		int pieces = 0;
		for (EquipmentSlot slot : TowerRunes.ARMOR_SLOTS) {
			ItemStack piece = wearer.getItemBySlot(slot);
			RuneData rune = TowerRunes.slotOf(piece) == TowerRunes.Slot.ARMOR ? TowerRunes.runeOf(piece) : null;
			if (rune != null && rune.element() == element) {
				pieces++;
			}
		}
		return (double) pieces / TowerRunes.ARMOR_PIECES;
	}

	public static double dodgePercent(final LivingEntity wearer) {
		return RuneValues.windDodge(RuneStats.of(wearer, Stat.AGI), share(wearer, TowerElement.WIND));
	}

	public static double magicResistPercent(final LivingEntity wearer) {
		return RuneValues.voidResist(RuneStats.of(wearer, Stat.INT), share(wearer, TowerElement.VOID));
	}

	public static double undeadResistPercent(final LivingEntity wearer) {
		return RuneValues.holyUndead(RuneStats.of(wearer, Stat.VIT), share(wearer, TowerElement.HOLY));
	}

	private static boolean dodges(final LivingEntity victim, final DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || share(victim, TowerElement.WIND) <= 0.0) {
			return false;
		}
		return RuneEffects.rolls(victim, dodgePercent(victim));
	}

	/** What a blow taken sets off: Lightning stores a charge; Fire, Ice and Dark answer the attacker. */
	private static void answer(final ServerLevel level, final ServerPlayer player, final DamageSource source, final float taken) {
		if (share(player, TowerElement.LIGHTNING) > 0.0) {
			RuneEffects.addCharge(player);
		}
		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player) {
			return;
		}
		double fire = share(player, TowerElement.FIRE);
		if (fire > 0.0) {
			attacker.igniteForSeconds(BURN_SECONDS);
			RuneEffects.deal(level, attacker, player, (float) (taken * RuneValues.fireThorns(RuneStats.of(player, Stat.VIT), fire) / 100.0));
		}
		if (RuneEffects.rolls(player, RuneValues.iceFreeze(RuneStats.of(player, Stat.AGI), share(player, TowerElement.ICE)))) {
			attacker.setTicksFrozen(attacker.getTicksRequiredToFreeze());
			attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FREEZE_TICKS, FREEZE_SLOWNESS), player);
		}
		double dark = share(player, TowerElement.DARK);
		if (dark > 0.0) {
			int ticks = (int) Math.round(RuneValues.darkWeakSeconds(RuneStats.of(player, Stat.LUK), dark) * 20.0);
			attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), player);
		}
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % REGEN_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			regen(player);
		}
	}

	/** One interval of Water armor's regeneration. */
	public static void regen(final ServerPlayer player) {
		double percent = RuneValues.waterRegen(RuneStats.of(player, Stat.VIT), share(player, TowerElement.WATER));
		if (percent > 0.0 && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
			player.heal((float) (player.getMaxHealth() * percent / 100.0 * REGEN_PER_INTERVAL));
		}
	}

	/** A blow on a rune-armored wearer, once its armor signatures have had their say: magic and undead blows are cut. */
	public static float guard(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!(victim instanceof Player) || amount <= 0.0F) {
			return amount;
		}
		double kept = 1.0;
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
			kept *= 1.0 - magicResistPercent(victim) / 100.0;
		}
		if (source.getEntity() != null && source.getEntity().is(EntityTypeTags.UNDEAD)) {
			kept *= 1.0 - undeadResistPercent(victim) / 100.0;
		}
		return (float) (amount * kept);
	}

	/** A blow a Lightning-armored player strikes, with their stored charges added; spent once the blow lands. */
	public static float kindle(final LivingEntity victim, final DamageSource source, final float amount) {
		if (RuneEffects.dealing() || !(source.getEntity() instanceof Player player) || player == victim || source.is(DamageTypes.THORNS)) {
			return amount;
		}
		int charges = RuneEffects.chargesOf(player);
		double share = share(player, TowerElement.LIGHTNING);
		if (charges <= 0 || share <= 0.0) {
			return amount;
		}
		player.setAttached(KINDLING, true);
		return (float) (amount * (1.0 + charges * RuneValues.lightningCharge(RuneStats.of(player, Stat.DEX), share) / 100.0));
	}
}
