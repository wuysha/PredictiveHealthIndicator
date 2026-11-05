package dev.phi.predictivehealth.mixin;

import dev.phi.predictivehealth.model.TargetTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes the state of a swing at the exact moment the attack is sent, so the
 * damage estimate uses the same weapon, cooldown and effects the server sees.
 */
@Mixin(Player.class)
public class PlayerMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void phi$onAttack(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;

		if (self.level().isClientSide()) {
			TargetTracker.onAttack(self, target);
		}
	}
}
