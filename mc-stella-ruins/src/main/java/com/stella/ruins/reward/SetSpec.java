package com.stella.ruins.reward;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** The numbers and bonuses of one armor set. Defense is per piece. The factor scales durability per piece. */
public record SetSpec(
	int durabilityFactor,
	int helmet,
	int chestplate,
	int leggings,
	int boots,
	float toughness,
	float knockbackResistance,
	int enchantValue,
	TagKey<Item> repair,
	Identifier equipmentAsset,
	List<SetBonus> bonuses
) {
	public SetSpec {
		bonuses = List.copyOf(bonuses);
		for (int i = 0; i < bonuses.size(); i++) {
			for (int j = i + 1; j < bonuses.size(); j++) {
				if (bonuses.get(i).pieces() == bonuses.get(j).pieces()) {
					throw new IllegalArgumentException("Two bonuses share the piece count " + bonuses.get(i).pieces());
				}
			}
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private int durabilityFactor = 15;
		private int helmet = 2;
		private int chestplate = 6;
		private int leggings = 5;
		private int boots = 2;
		private float toughness;
		private float knockbackResistance;
		private int enchantValue = 9;
		private TagKey<Item> repair;
		private Identifier equipmentAsset;
		private final List<SetBonus> bonuses = new ArrayList<>();

		private Builder() {
		}

		public Builder durabilityFactor(final int value) {
			durabilityFactor = value;
			return this;
		}

		public Builder defense(final int helmet, final int chestplate, final int leggings, final int boots) {
			this.helmet = helmet;
			this.chestplate = chestplate;
			this.leggings = leggings;
			this.boots = boots;
			return this;
		}

		public Builder toughness(final float value) {
			toughness = value;
			return this;
		}

		public Builder knockbackResistance(final float value) {
			knockbackResistance = value;
			return this;
		}

		public Builder enchantValue(final int value) {
			enchantValue = value;
			return this;
		}

		public Builder repair(final TagKey<Item> tag) {
			repair = tag;
			return this;
		}

		public Builder equipmentAsset(final Identifier asset) {
			equipmentAsset = asset;
			return this;
		}

		public Builder bonus(final SetBonus bonus) {
			bonuses.add(bonus);
			return this;
		}

		public SetSpec build() {
			if (repair == null || equipmentAsset == null) {
				throw new IllegalStateException("A set spec needs a repair tag and an equipment asset");
			}
			return new SetSpec(durabilityFactor, helmet, chestplate, leggings, boots, toughness, knockbackResistance, enchantValue, repair, equipmentAsset, bonuses);
		}
	}
}
