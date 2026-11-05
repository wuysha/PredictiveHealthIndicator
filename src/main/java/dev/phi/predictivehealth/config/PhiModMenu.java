package dev.phi.predictivehealth.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Entry point of Mod Menu.
 *
 * <p>Mod Menu loads this class only when it is installed, so the mod works
 * without it as well.
 */
public final class PhiModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return PhiConfigScreen::build;
	}
}
