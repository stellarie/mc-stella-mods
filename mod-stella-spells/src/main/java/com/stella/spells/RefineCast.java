package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One finished cast of a refined book, as a signature sees it: who cast it, at what, and which foes it
 * struck. The helpers resolve the aim point and hit or heal through the caster's own school, so a signature
 * scales with the same stats as the book.
 */
record RefineCast(
	ServerPlayer caster, SpellDefinition definition, @Nullable Mob target, @Nullable Vec3 point, @Nullable LivingEntity ally,
	List<Mob> victims, Vec3 departure, SpellModifiers.CastModifiers modifiers
) {
	/** The aim point, else the target, else the ally, else the caster. */
	Vec3 focus() {
		if (this.point != null) {
			return this.point;
		}
		if (this.target != null) {
			return this.target.position();
		}
		return this.ally != null ? this.ally.position() : this.caster.position();
	}

	/** Every hostile within the radius of the focus. */
	List<Mob> foes(final double radius) {
		return MoreSpells.hostiles(this.caster.level(), this.focus(), radius);
	}

	/** Every hostile within the radius of the focus, never the one given. */
	List<Mob> foesBeside(final Mob mob, final double radius) {
		List<Mob> found = new ArrayList<>(MoreSpells.hostiles(this.caster.level(), mob.position(), radius));
		found.remove(mob);
		found.sort(java.util.Comparator.comparingDouble((Mob other) -> other.distanceToSqr(mob)).thenComparingInt(Mob::getId));
		return found;
	}

	/** Every hostile within the radius of the caster. */
	List<Mob> foesAroundCaster(final double radius) {
		return MoreSpells.hostiles(this.caster.level(), this.caster.position(), radius);
	}

	/** Every ally within the radius of the caster, nearest first. */
	List<LivingEntity> allies(final double radius) {
		return SkillTargeting.alliesAround(this.caster, radius);
	}

	/** The caster, then every ally within the radius. */
	List<LivingEntity> party(final double radius) {
		List<LivingEntity> party = new ArrayList<>();
		party.add(this.caster);
		party.addAll(this.allies(radius));
		return party;
	}

	/** Every ally within the radius of this body, nearest first; the body itself and the caster are left out. */
	List<LivingEntity> alliesBeside(final LivingEntity center, final double radius) {
		List<LivingEntity> found = new ArrayList<>(this.caster.level().getEntitiesOfClass(
			LivingEntity.class, center.getBoundingBox().inflate(radius),
			candidate -> candidate != center && SkillTargeting.isAlly(this.caster, candidate)
				&& candidate.distanceToSqr(center) <= radius * radius
		));
		found.sort(java.util.Comparator.comparingDouble((LivingEntity other) -> other.distanceToSqr(center)).thenComparingInt(LivingEntity::getId));
		return found;
	}

	/** The caster, if near this point, then every ally near it. */
	List<LivingEntity> partyAt(final Vec3 center, final double radius) {
		List<LivingEntity> party = new ArrayList<>();
		if (this.caster.position().distanceToSqr(center) <= radius * radius) {
			party.add(this.caster);
		}
		for (LivingEntity ally : this.caster.level().getEntitiesOfClass(LivingEntity.class,
			new net.minecraft.world.phys.AABB(center, center).inflate(radius),
			candidate -> SkillTargeting.isAlly(this.caster, candidate) && candidate.position().distanceToSqr(center) <= radius * radius)) {
			party.add(ally);
		}
		return party;
	}

	/** The book's own damage number, or the fallback for a book that deals none. */
	float flat(final float fallback) {
		SpellNumbers numbers = SpellNumbers.find(this.definition.id());
		return numbers != null && numbers.damage() > 0.0F ? numbers.damage() : fallback;
	}

	/** One hit of the book's school: the flat number through the caster's own stat, at the cast's power. */
	void hit(final Mob foe, final float flat) {
		switch (this.definition.school()) {
			case MAGE -> MoreSpells.hurt(this.caster, foe, RpgBridge.damage(this.caster, flat, this.modifiers.powerMultiplier()));
			case FIGHTER -> FighterSkills.hit(this.caster, foe, flat, this.modifiers);
			case ARCHER -> ArcherSkills.hit(this.caster, foe, flat, this.modifiers);
			case PRIEST -> PriestSkills.hitHoly(this.caster, foe, flat, this.modifiers);
		}
	}

	/** The health a heal of this flat number gives, through the school's own stat and the cast's power. */
	float healAmount(final float flat) {
		return this.definition.school() == School.MAGE
			? RpgBridge.heal(this.caster, flat, this.modifiers.powerMultiplier())
			: RpgBridge.skillHeal(this.caster, this.definition.school(), flat, this.modifiers.powerMultiplier());
	}

	void heal(final LivingEntity who, final float amount) {
		CastHits.heal(this.caster, who, amount);
	}
}
