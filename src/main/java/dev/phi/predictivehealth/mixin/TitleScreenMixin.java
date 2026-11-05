package dev.phi.predictivehealth.mixin;

import dev.phi.predictivehealth.update.UpdateCheck;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starts the update check when the title screen appears and shows the dialog
 * with the new version once the answer has arrived.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
	@Inject(method = "init", at = @At("RETURN"))
	private void phi$checkForUpdate(CallbackInfo ci) {
		UpdateCheck.start();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void phi$offerUpdate(CallbackInfo ci) {
		UpdateCheck.offer((TitleScreen) (Object) this);
	}
}
