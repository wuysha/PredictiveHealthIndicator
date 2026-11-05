package dev.phi.predictivehealth.mixin;

import dev.phi.predictivehealth.model.TargetTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the vanilla packets the prediction is built on: damage events, the
 * status bytes of the entities and the equipment of the players.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Shadow
	private ClientLevel level;

	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void phi$onEntityDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		TargetTracker.onDamage(packet);
	}

	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void phi$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Entity entity = packet.getEntity(this.level);

		if (entity != null) {
			TargetTracker.onStatus(entity.getId(), packet.getEventId());
		}
	}

	/**
	 * The explosion packet carries the centre and the power of the explosion.
	 * It arrives after the damage packet, because the server damages the
	 * entities before it tells the clients about the explosion.
	 */
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void phi$onExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		TargetTracker.onExplosion(packet);
	}

	/**
	 * The equipment packets carry the armour of the players. The servers that
	 * protect against health indicators rewrite them, so the mod keeps a copy of
	 * what really arrived to tell the real enchantments from the fake ones.
	 */
	@Inject(method = "handleSetEquipment", at = @At("TAIL"))
	private void phi$onEntityEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
		TargetTracker.onEquipment(packet);
	}

	/**
	 * The spawn packet of an entity tells that the server has just created it.
	 *
	 * <p>An item that comes with a chunk is not sent as a spawn packet, so this
	 * is the only way to tell a piece that was just dropped from one that lay on
	 * the ground already.
	 */
	@Inject(method = "handleAddEntity", at = @At("TAIL"))
	private void phi$onEntitySpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		TargetTracker.onEntitySpawn(packet.getId(), packet.getType() == EntityTypes.ITEM);
	}
}
