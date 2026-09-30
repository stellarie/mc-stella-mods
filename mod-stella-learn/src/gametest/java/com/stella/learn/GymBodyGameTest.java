package com.stella.learn;

import com.stella.learn.gym.Body;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The body of a bot comes from its seed, and Python computes the same numbers from it. */
public class GymBodyGameTest {
	/** seed, max health, armor, toughness, protection; the same rows sit in `training/tests/test_bodies.py`. */
	private static final long[][] VECTORS = {{1, 50, 20, 5, 2}, {42, 26, 5, 4, 0}, {1_000_000, 53, 13, 7, 4}, {4_294_967_295L, 35, 19, 0, 3}, {777, 46, 6, 6, 0}};

	/** seed, archer speed and bot speed in ten-thousandths, for the range 600 to 1400 thousandths; the same rows sit in `test_bodies.py`. */
	private static final long[][] SPEEDS = {{1L, 10185, 8284}, {42L, 12405, 8719}, {1000000L, 6650, 12828}, {777L, 8670, 11519}};

	@GameTest
	public void bodiesMatchThePythonVectors(final GameTestHelper helper) {
		for (long[] row : VECTORS) {
			Body body = Body.draw(row[0]);
			helper.assertTrue(body.maxHealth() == row[1] && body.armor() == row[2] && body.toughness() == row[3] && body.protection() == row[4],
				"seed " + row[0] + " gives " + body.detail());
		}

		Body.Range wide = new Body.Range(20, 60, 20, 12, 4, 100, 600, 1400);
		for (long[] row : SPEEDS) {
			Body body = Body.draw(row[0], wide);
			helper.assertTrue(Math.round(body.archerSpeed() * 10000) == row[1] && Math.round(body.botSpeed() * 10000) == row[2], "seed " + row[0] + " gives " + body.detail());
		}

		helper.assertTrue(Body.draw(5).archerSpeed() == 1.0 && Body.draw(5).botSpeed() == 1.0, "a range without speed bounds changes the speed");
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
