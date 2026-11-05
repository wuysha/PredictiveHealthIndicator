package dev.phi.predictivehealth.model;

import net.minecraft.world.item.ItemStack;

/**
 * A consumable (golden apple, potion, stew) the opponent is currently eating or
 * drinking. The healing is applied once the consumption actually completes.
 */
public final class PendingConsume {
	public final ItemStack stack;
	public final int expectedTicks;
	public int observedTicks;
	public boolean applied;

	public PendingConsume(ItemStack stack, int expectedTicks) {
		this.stack = stack;
		this.expectedTicks = Math.max(expectedTicks, 1);
	}
}
