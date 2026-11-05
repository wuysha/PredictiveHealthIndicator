package dev.phi.predictivehealth.model;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * The pieces of armour of an opponent whose real enchantments are known.
 *
 * <p>The servers that protect against health indicators rewrite the equipment
 * packets of every player, but they do not touch the pieces that lie on the
 * ground. A player that takes a piece off drops it, and a player that dies drops
 * the whole armour, so the drops are the only place where the real enchantments
 * can be read.
 */
public final class ArmorProfile {
	private final Map<EquipmentSlot, ItemStack> pieces = new EnumMap<>(EquipmentSlot.class);

	/** Remembers a piece whose real enchantments are known. */
	public void put(EquipmentSlot slot, ItemStack stack) {
		pieces.put(slot, stack.copy());
	}

	/** Forgets a piece, for example when the player put on another one. */
	public void forget(EquipmentSlot slot) {
		pieces.remove(slot);
	}

	/** The piece of a slot, empty when nothing is known about it. */
	public ItemStack piece(EquipmentSlot slot) {
		ItemStack stack = pieces.get(slot);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	/** True when nothing is known about the armour of the player. */
	public boolean isEmpty() {
		return pieces.isEmpty();
	}
}
