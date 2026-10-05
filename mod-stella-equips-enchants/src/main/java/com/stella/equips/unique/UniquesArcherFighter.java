package com.stella.equips.unique;

import com.stella.equips.unique.af.AncientEyeBow;
import com.stella.equips.unique.af.MireBow;
import com.stella.equips.unique.af.NightwingBow;
import com.stella.equips.unique.af.RimefrostBow;
import com.stella.equips.unique.af.SunfireBow;
import com.stella.equips.unique.af.SunscorchBow;
import com.stella.equips.unique.af.WitherboneLongbow;
import com.stella.equips.unique.af.WailingBow;
import com.stella.equips.unique.af.LevitatorBow;
import com.stella.equips.unique.af.DragonbreathBow;
import com.stella.equips.unique.af.SonicBow;

/** The archer and fighter uniques of the second slice. W002 owns this file. */
public final class UniquesArcherFighter {
	private UniquesArcherFighter() {
	}

	public static void register() {
		RimefrostBow.register();
		MireBow.register();
		SunscorchBow.register();
		WitherboneLongbow.register();
		AncientEyeBow.register();
		SunfireBow.register();
		NightwingBow.register();
		WailingBow.register();
		LevitatorBow.register();
		DragonbreathBow.register();
		SonicBow.register();
	}
}
