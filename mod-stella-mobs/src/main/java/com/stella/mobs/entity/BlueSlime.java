package com.stella.mobs.entity;

import com.stella.mobs.behavior.EliteState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;

/**
 * The blue slime: passive, never elite, never a boss. It never targets or hurts anything, a white slime's escort
 * included, and nothing targets it. Every blue slime carries {@link #HARMLESS_TAG}, so other mods can skip it by tag.
 * An escort is worth no experience.
 */
public final class BlueSlime extends StellaSlime {
	/** Marks a blue slime. Golems, patrols, mages, and hostile auras skip it. */
	public static final String HARMLESS_TAG = "mod-stella-mobs.harmless";

	public BlueSlime(final EntityType<? extends Slime> type, final Level level) {
		super(type, level);
		this.addTag(HARMLESS_TAG);
	}

	public static boolean isHarmless(final Entity entity) {
		return entity instanceof BlueSlime || entity != null && entity.entityTags().contains(HARMLESS_TAG);
	}

	/** True for a slime a white slime summoned. Read without creating a state, so a natural slime saves none. */
	public boolean isEscort() {
		EliteState state = ((AttachmentTarget) this).getAttached(EliteState.TYPE);
		return state != null && state.summoned();
	}

	@Override
	protected void addTargetingGoals() {
	}

	@Override
	protected boolean canDealDamage() {
		return false;
	}

	// A save from before every blue slime was tagged loads without the tag.
	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.entityTags().contains(HARMLESS_TAG)) {
			this.addTag(HARMLESS_TAG);
		}
	}

	@Override
	protected int getBaseExperienceReward(final ServerLevel level) {
		return isEscort() ? 0 : super.getBaseExperienceReward(level);
	}
}
