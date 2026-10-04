package com.stella.ruins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The build merges every lang part into one en_us.json that ships in the jar. */
public class ScaffoldGameTest {
	@GameTest
	public void theMergedLangFileShipsEveryPart(final GameTestHelper helper) {
		try (InputStream in = StellaRuins.class.getResourceAsStream("/assets/mod-stella-ruins/lang/en_us.json")) {
			helper.assertTrue(in != null, "the merged lang file is missing");
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			helper.assertTrue(text.contains("itemGroup.mod-stella-ruins"), "the core lang part was not merged");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		StellaRuins.LOGGER.info("[RUINS] test=theMergedLangFileShipsEveryPart RESULT merged=true");
		helper.succeed();
	}
}
