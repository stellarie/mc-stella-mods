package com.stella.ruins.site;
import com.stella.ruins.site.ambushcamp.AmbushCampSite;
import com.stella.ruins.site.crystalgrotto.CrystalGrottoSite;
import com.stella.ruins.site.deepforge.DeepForgeSite;
import com.stella.ruins.site.fallenwatchtower.FallenWatchtowerSite;
import com.stella.ruins.site.lostminerscamp.LostMinersCampSite;
import com.stella.ruins.site.meteorcrater.MeteorCraterSite;
import com.stella.ruins.site.provinggrounds.ProvingGroundsSite;
import com.stella.ruins.site.ruinedkeep.RuinedKeepSite;
import com.stella.ruins.site.sealedvault.SealedVaultSite;
import com.stella.ruins.site.spidernest.SpiderNestSite;
import com.stella.ruins.site.wayshrine.WayshrineSite;

/** Every site, in one place. Each slice owns its own package and its own register method. */
public final class Sites {
	private Sites() {
	}

	public static void register() {
		WayshrineSite.register();
		FallenWatchtowerSite.register();
		MeteorCraterSite.register();
		ProvingGroundsSite.register();
		DeepForgeSite.register();
		SpiderNestSite.register();
		LostMinersCampSite.register();
		CrystalGrottoSite.register();
		AmbushCampSite.register();
		RuinedKeepSite.register();
		SealedVaultSite.register();
	}
}
