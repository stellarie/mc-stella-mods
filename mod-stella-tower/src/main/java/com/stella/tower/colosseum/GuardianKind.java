package com.stella.tower.colosseum;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * The two Tower Guardians. The melee one wears a slowing aura and drops a Bone Shield; the ranged one wears a weakening
 * aura and drops a Skeleton Crown. Both items belong to the equips mod and are found by id, so this mod needs neither.
 */
public enum GuardianKind {
	MELEE("mod-stella-tower.guardian_melee", "entity.mod-stella-tower.tower_guardian", BossEvent.BossBarColor.RED,
		MobEffects.SLOWNESS, Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "bone_shield")),
	RANGED("mod-stella-tower.guardian_ranged", "entity.mod-stella-tower.tower_guardian_archer", BossEvent.BossBarColor.PURPLE,
		MobEffects.WEAKNESS, Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "skeleton_crown"));

	private final String tag;
	private final String nameKey;
	private final BossEvent.BossBarColor barColor;
	private final Holder<MobEffect> aura;
	private final Identifier drop;

	GuardianKind(final String tag, final String nameKey, final BossEvent.BossBarColor barColor, final Holder<MobEffect> aura, final Identifier drop) {
		this.tag = tag;
		this.nameKey = nameKey;
		this.barColor = barColor;
		this.aura = aura;
		this.drop = drop;
	}

	/** The tag on the rider that says which guardian it is. */
	public String tag() {
		return this.tag;
	}

	public String nameKey() {
		return this.nameKey;
	}

	public BossEvent.BossBarColor barColor() {
		return this.barColor;
	}

	/** The effect given to every player near the guardian. */
	public Holder<MobEffect> aura() {
		return this.aura;
	}

	/** The item this guardian drops when it dies. */
	public Identifier drop() {
		return this.drop;
	}

	public static GuardianKind of(final net.minecraft.world.entity.Entity entity) {
		for (GuardianKind kind : values()) {
			if (entity.entityTags().contains(kind.tag)) {
				return kind;
			}
		}
		return null;
	}
}
