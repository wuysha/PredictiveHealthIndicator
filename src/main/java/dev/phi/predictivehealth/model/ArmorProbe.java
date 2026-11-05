package dev.phi.predictivehealth.model;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The equipment of a player as it really arrived from the server.
 *
 * <p>The servers that protect against health indicators rewrite the equipment
 * packets, so the enchantments of a piece of armour can be replaced by ones
 * that do not fit the item at all. The probe keeps the stacks of the packets
 * and tells which of them are such a rewrite, so the mod never believes a
 * value the server made up.
 */
public final class ArmorProbe {
	/** Probe of a player whose equipment the server did not send yet. */
	public static final ArmorProbe EMPTY = new ArmorProbe();

	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};

	private final Map<EquipmentSlot, ItemStack> stacks = new EnumMap<>(EquipmentSlot.class);

	public void put(EquipmentSlot slot, ItemStack stack) {
		stacks.put(slot, stack.copy());
	}

	public ItemStack stack(EquipmentSlot slot) {
		ItemStack stack = stacks.get(slot);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	/** True when the server rewrote the enchantments of this piece. */
	public boolean rewritten(EquipmentSlot slot) {
		return EnchantmentReader.isRewritten(stack(slot));
	}

	/** True when at least one piece of the equipment was rewritten. */
	public boolean rewritten() {
		for (EquipmentSlot slot : SLOTS) {
			if (rewritten(slot)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Level of an enchantment of a piece of equipment.
	 *
	 * <p>A rewritten piece holds the enchantments of the server instead of the
	 * real ones, so nothing is read from it.
	 */
	public int level(EquipmentSlot slot, ResourceKey<Enchantment> enchantment) {
		if (rewritten(slot)) {
			return 0;
		}

		return EnchantmentReader.level(stack(slot), enchantment);
	}

	/**
	 * The slot a piece of equipment belongs to, null when it fits nowhere.
	 */
	public static EquipmentSlot slotOf(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);

		if (equippable == null) {
			return null;
		}

		EquipmentSlot slot = equippable.slot();

		return switch (slot) {
			case HEAD, CHEST, LEGS, FEET -> slot;
			default -> null;
		};
	}

	/** Description of the equipment as it arrived, for the debug output. */
	public String describe() {
		StringBuilder builder = new StringBuilder();

		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = stack(slot);

			if (stack.isEmpty()) {
				continue;
			}

			if (builder.length() > 0) {
				builder.append(' ');
			}

			builder.append(name(slot)).append(':').append(EnchantmentReader.describe(stack));

			if (rewritten(slot)) {
				builder.append('*');
			}
		}

		return builder.length() == 0 ? "-" : builder.toString();
	}

	private static String name(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> "hel";
			case CHEST -> "che";
			case LEGS -> "leg";
			case FEET -> "fee";
			case MAINHAND -> "hand";
			case OFFHAND -> "off";
			default -> "oth";
		};
	}
}
