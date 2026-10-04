package com.stella.equips.unique;

import com.stella.equips.mixin.AbstractArrowAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Bonewhisper Bow, the reference unique of the framework. Each shot has a chance to loose more arrows a few ticks
 * apart, at no arrow cost, that cannot be picked up. At +10 the extra arrows pierce one more mob. At +15 every fifth
 * shot loosens them for sure. Every number lives in {@link #TUNING}, and the effect and its tests read the same record.
 */
public final class BonewhisperBow {
	public static final String ID = "bonewhisper_bow";

	/** The tuning. The chance is {@code base}, {@code plus7} from +7 and {@code plus12} from +12. */
	public record Tuning(double base, double plus7, double plus12, int extraArrows, int spacingTicks, int guaranteedEvery, int piercePlus) {
	}

	public static final Tuning TUNING = new Tuning(0.15, 0.25, 0.35, 2, 3, 5, 1);

	/** Decides the chance roll of a shot. A test replaces it. */
	public interface Roller {
		boolean succeeds(ServerPlayer holder, double chance);
	}

	/** Sees each extra arrow the bow looses. A test records them. */
	public interface Recorder {
		void spawned(ServerPlayer holder, AbstractArrow extra);
	}

	private static final Roller FAIR = (holder, chance) -> holder.getRandom().nextDouble() < chance;
	private static final Map<UUID, Integer> SHOTS = new HashMap<>();
	private static volatile Roller roller = FAIR;
	private static volatile @Nullable Recorder recorder;

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
			ServerPlayer holder = c.holder();
			int shot = SHOTS.merge(holder.getUUID(), 1, Integer::sum);
			boolean sure = c.atLeast(Uniques.PLUS_15) && shot % TUNING.guaranteedEvery() == 0;
			if (sure || roller.succeeds(holder, chanceAt(c.refine()))) {
				scheduleExtras(c, arrow, power);
			}
		}
	};

	private BonewhisperBow() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Bonewhisper Bow", UniqueJob.ARCHER, UniqueKind.BOW,
			List.of(Identifier.parse("minecraft:skeleton")),
			percent(TUNING.base()) + " chance per shot to loose " + TUNING.extraArrows() + " more arrows, " + TUNING.spacingTicks()
				+ " ticks apart, at no arrow cost. Extra arrows cannot be picked up.",
			percent(TUNING.plus7()),
			"Extra arrows pierce " + TUNING.piercePlus() + " more mob",
			percent(TUNING.plus12()),
			"Every " + ordinal(TUNING.guaranteedEvery()) + " shot is a sure triple",
			null, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOTS.clear());
	}

	public static void setRoller(final @Nullable Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static void setRecorder(final @Nullable Recorder newRecorder) {
		recorder = newRecorder;
	}

	/** The shots a holder has loosed with the bow since the server started. */
	public static int shots(final ServerPlayer holder) {
		return SHOTS.getOrDefault(holder.getUUID(), 0);
	}

	/** The chance of the extra arrows for a shot at a refine level. */
	public static double chanceAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7() : TUNING.base();
	}

	private static void scheduleExtras(final UniqueContext c, final AbstractArrow shot, final float power) {
		ServerPlayer holder = c.holder();
		MinecraftServer server = holder.level().getServer();
		ItemStack weapon = c.stack().copy();
		ItemStack ammo = shot.getPickupItemStackOrigin().copyWithCount(1);
		boolean crit = shot.isCritArrow();
		int refine = c.refine();
		for (int extra = 1; extra <= TUNING.extraArrows(); extra++) {
			UniqueScheduler.after(server, extra * TUNING.spacingTicks(), () -> loose(holder, weapon, ammo, power, shot, crit, refine));
		}
	}

	private static void loose(final ServerPlayer holder, final ItemStack weapon, final ItemStack ammo, final float power, final AbstractArrow shot,
		final boolean crit, final int refine) {
		if (!holder.isAlive()) {
			return;
		}
		ServerLevel level = holder.level();
		ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem item ? item : (ArrowItem) Items.ARROW;
		AbstractArrow extra = arrowItem.createArrow(level, ammo, holder, weapon);
		extra.pickup = AbstractArrow.Pickup.DISALLOWED;
		extra.setCritArrow(crit);
		UniqueArrows.mark(extra, ID, refine);
		Projectile.spawnProjectile(extra, level, ammo, arrow -> arrow.shootFromRotation(holder, holder.getXRot(), holder.getYRot(), 0.0F, power, 1.0F));
		// After the spawn: the weapon enchantments set the pierce level during the spawn, so this is the final word.
		byte pierce = (byte) (shot.getPierceLevel() + (refine >= Uniques.PLUS_10 ? TUNING.piercePlus() : 0));
		((AbstractArrowAccessor) extra).stella$setPierceLevel(pierce);
		Recorder seen = recorder;
		if (seen != null) {
			seen.spawned(holder, extra);
		}
	}

	private static String percent(final double share) {
		return Math.round(share * 100.0) + "%";
	}

	private static String ordinal(final int number) {
		return number + switch (number % 10) {
			case 1 -> number % 100 == 11 ? "th" : "st";
			case 2 -> number % 100 == 12 ? "th" : "nd";
			case 3 -> number % 100 == 13 ? "th" : "rd";
			default -> "th";
		};
	}
}
