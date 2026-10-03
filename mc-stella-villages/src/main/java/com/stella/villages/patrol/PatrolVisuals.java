package com.stella.villages.patrol;

import java.util.Collection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * V5: a patrol member is told apart from a hostile illager with vanilla assets only, so a client needs
 * nothing extra. Pillagers and Vindicators wear the village banner, a lime field with a white globe and
 * a green border, where a raid captain wears the ominous one. Every member, the Ravager included, gives
 * off the green village sparkle.
 */
public final class PatrolVisuals {
	public static final int SPARKLE_INTERVAL_TICKS = 20;
	private static final int SPARKLE_COUNT = 3;
	private static final double SPARKLE_SPREAD = 0.3;
	private static final double SPARKLE_LIFT = 0.3;
	// Literal, because the village mod ships no language file and a vanilla client must read it too.
	private static final String BANNER_NAME = "Guard Banner";

	/** Test seam: each sparkle a member gave off, and how many players it reached. */
	public interface Recorder {
		default void sparkled(final Mob member, final int viewers) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private PatrolVisuals() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** Dresses a Pillager or Vindicator member in the village banner, replacing a rolled helmet. */
	public static void dress(final Mob mob, final ServerLevel level, final PatrolState.Role role) {
		if ((role != PatrolState.Role.PILLAGER && role != PatrolState.Role.RANGED_GUARD && role != PatrolState.Role.VINDICATOR
			&& role != PatrolState.Role.TRAINER)
			|| isVillageBanner(mob.getItemBySlot(EquipmentSlot.HEAD))) {
			return;
		}

		mob.setItemSlot(EquipmentSlot.HEAD, villageBanner(level));
		mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
	}

	public static ItemStack villageBanner(final ServerLevel level) {
		var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		ItemStack banner = new ItemStack(Items.BANNER.lime());
		banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
			.add(patterns.getOrThrow(BannerPatterns.GLOBE), DyeColor.WHITE)
			.add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.GREEN)
			.build());
		banner.set(DataComponents.ITEM_NAME, Component.literal(BANNER_NAME));
		return banner;
	}

	/** True when the stack is the village banner a patrol member wears. */
	public static boolean isVillageBanner(final ItemStack stack) {
		BannerPatternLayers layers = stack.get(DataComponents.BANNER_PATTERNS);
		return stack.is(Items.BANNER.lime()) && layers != null && layers.layers().size() == 2
			&& layers.layers().get(0).pattern().is(BannerPatterns.GLOBE);
	}

	/** Every SPARKLE_INTERVAL_TICKS of the level's own counter, each live member in this level sparkles. */
	public static void sparkle(final ServerLevel level, final long tick, final Collection<Mob> members) {
		if (tick % SPARKLE_INTERVAL_TICKS != 0L) {
			return;
		}

		for (Mob member : members) {
			if (member.level() != level || !member.isAlive()) {
				continue;
			}

			int viewers = level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER, member.getX(), member.getY() + member.getBbHeight() + SPARKLE_LIFT, member.getZ(),
				SPARKLE_COUNT, SPARKLE_SPREAD, SPARKLE_SPREAD, SPARKLE_SPREAD, 0.0
			);
			recorder.sparkled(member, viewers);
		}
	}
}
