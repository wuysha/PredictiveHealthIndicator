package dev.phi.predictivehealth.update;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.mojang.blaze3d.Blaze3D;

import java.net.URI;

/**
 * The parts of the update dialog that depend on the version of the game.
 */
final class UpdateUi {
	private UpdateUi() {
	}

	static void openUrl(String url) {
		Blaze3D.openUri(URI.create(url));
	}

	static void setScreen(Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
