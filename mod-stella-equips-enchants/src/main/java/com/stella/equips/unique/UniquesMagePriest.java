package com.stella.equips.unique;

import com.stella.equips.unique.mp.BlazingMantle;
import com.stella.equips.unique.mp.EchoCirclet;
import com.stella.equips.unique.mp.EctoplasmVeil;
import com.stella.equips.unique.mp.EndflameRobe;
import com.stella.equips.unique.mp.FangcallerRobe;
import com.stella.equips.unique.mp.HexweaversHat;
import com.stella.equips.unique.mp.MagmaGreaves;
import com.stella.equips.unique.mp.MirrorMask;
import com.stella.equips.unique.mp.PrismarineLeggings;
import com.stella.equips.unique.mp.SpectralCirclet;
import com.stella.equips.unique.mp.SplitGelBoots;
import com.stella.equips.unique.mp.StarfallCrown;
import com.stella.equips.unique.mp.SovereignCrown;
import com.stella.equips.unique.mp.SulfurCrown;
import com.stella.equips.unique.mp.TarShroud;
import com.stella.equips.unique.mp.VolatileHood;
import com.stella.equips.unique.mp.VoidstepBoots;

/** The mage and priest uniques of the third slice. W003 owns this file. */
public final class UniquesMagePriest {
	private UniquesMagePriest() {
	}

	public static void register() {
		HexweaversHat.register();
		BlazingMantle.register();
		SulfurCrown.register();
		SpectralCirclet.register();
		TarShroud.register();
		StarfallCrown.register();
		FangcallerRobe.register();
		MagmaGreaves.register();
		VolatileHood.register();
		SplitGelBoots.register();
		EndflameRobe.register();
		EchoCirclet.register();
		VoidstepBoots.register();
		MirrorMask.register();
		EctoplasmVeil.register();
		PrismarineLeggings.register();
		SovereignCrown.register();
	}
}
