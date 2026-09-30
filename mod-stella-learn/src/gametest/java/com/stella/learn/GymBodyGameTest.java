package com.stella.learn;

import com.stella.learn.gym.Body;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The body of a bot comes from its seed, and Python computes the same numbers from it. */
public class GymBodyGameTest {
	/** seed, max health, armor, toughness, protection; the same rows sit in `training/tests/test_bodies.py`. */
	private static final long[][] VECTORS = {{1, 50, 20, 5, 2}, {42, 26, 5, 4, 0}, {1_000_000, 53, 13, 7, 4}, {4_294_967_295L, 35, 19, 0, 3}, {777, 46, 6, 6, 0}};

	@GameTest
	public void bodiesMatchThePythonVectors(final GameTestHelper helper) {
		for (long[] row : VECTORS) {
			Body body = Body.draw(row[0]);
			helper.assertTrue(body.maxHealth() == row[1] && body.armor() == row[2] && body.toughness() == row[3] && body.protection() == row[4],
				"seed " + row[0] + " gives " + body.detail());
		}

		Body.Range frail = new Body.Range(20, 26, 4, 0, 0, 0);
		for (long seed = 0; seed < 500; seed++) {
			Body body = Body.draw(seed, frail);
			helper.assertTrue(body.maxHealth() >= 20 && body.maxHealth() <= 26 && body.armor() <= 4 && body.toughness() == 0 && body.protection() == 0
				&& body.special() == Body.Special.NONE, "frail range broken: " + body.detail());
		}

		int specials = 0;
		for (long seed = 0; seed < 2000; seed++) {
			Body body = Body.draw(seed);
			helper.assertTrue(body.maxHealth() >= 20 && body.maxHealth() <= 60 && body.armor() <= 20 && body.toughness() <= 12 && body.protection() <= 4, "out of range: " + body.detail());
			if (body.special() != Body.Special.NONE) {
				specials++;
			}
		}

		helper.assertTrue(specials > 140 && specials < 260, "resistance or absorption in " + specials + " of 2000 bodies");
		helper.succeed();
	}
}
