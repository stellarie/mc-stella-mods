package com.stella.end;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;

/**
 * The ten pillar crystals and the buff each gives the dragon while it stands. The pillar at index {@code i} in vanilla's
 * order gives buff {@code i + 1}. The dragon reads the pillars every {@link #PERIOD} ticks.
 */
public final class DragonBuffs {
	static final int PERIOD = 10;
	private static final Identifier KNOCKBACK_ID = StellaEnd.id("crystal_knockback");
	private static final Identifier ABSORPTION_ID = StellaEnd.id("crystal_absorption");

	private DragonBuffs() {
	}

	public static List<EndSpikeFeature.EndSpike> spikes(final ServerLevel end) {
		return EndSpikeFeature.getSpikesForLevel(end);
	}

	public static Set<Integer> live(final ServerLevel end) {
		Set<Integer> numbers = new TreeSet<>();
		List<EndSpikeFeature.EndSpike> pillars = spikes(end);
		for (int index = 0; index < pillars.size() && index < DragonRules.BUFFS; index++) {
			if (!end.getEntitiesOfClass(EndCrystal.class, pillars.get(index).getTopBoundingBox()).isEmpty()) {
				numbers.add(index + 1);
			}
		}
		return numbers;
	}

	public static Set<Integer> of(final EnderDragon dragon) {
		DragonState state = EndDragons.state(dragon);
		return state == null ? Set.of() : state.buffs();
	}

	/** Reads the pillars and brings the dragon's attributes in line with what stands. */
	static void refresh(final ServerLevel end, final EnderDragon dragon, final DragonState state) {
		Set<Integer> standing = live(end);
		if (standing.equals(state.buffs)) {
			return;
		}
		BehaviorLog.info("[END] dragon buffs {} -> {}", state.buffs, standing);
		state.buffs = standing;
		AttributeInstance knockback = dragon.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			knockback.removeModifier(KNOCKBACK_ID);
			double resist = DragonRules.knockbackResistance(standing);
			if (resist > 0.0) {
				knockback.addPermanentModifier(new AttributeModifier(KNOCKBACK_ID, resist, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		AttributeInstance room = dragon.getAttribute(Attributes.MAX_ABSORPTION);
		if (room != null) {
			room.removeModifier(ABSORPTION_ID);
			float absorption = DragonRules.absorption(standing) * (float) HealthBridge.scale();
			if (absorption > 0.0F) {
				room.addPermanentModifier(new AttributeModifier(ABSORPTION_ID, absorption, AttributeModifier.Operation.ADD_VALUE));
				dragon.setAbsorptionAmount(absorption);
			} else {
				dragon.setAbsorptionAmount(Math.min(dragon.getAbsorptionAmount(), dragon.getMaxAbsorption()));
			}
		}
	}

	/** Regeneration: a share of the dragon's health back every second. Set directly, so no sibling's heal rule multiplies it. */
	static void regenerate(final EnderDragon dragon, final DragonState state, final long now) {
		float share = DragonRules.regenPerSecond(state.buffs);
		if (share > 0.0F && now % DragonRules.REGEN_PERIOD == 0 && dragon.getHealth() < dragon.getMaxHealth()) {
			dragon.setHealth(Math.min(dragon.getMaxHealth(), dragon.getHealth() + dragon.getMaxHealth() * share));
		}
	}
}
