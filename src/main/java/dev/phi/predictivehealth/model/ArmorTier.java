package dev.phi.predictivehealth.model;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Optional;

/**
 * The material of a piece of armour.
 *
 * <p>The servers that protect against health indicators rewrite the enchantments
 * of the equipment, so the real protection of the armour is hidden. The material
 * is not hidden though, and it tells what the protection most likely is, because
 * a piece of diamond armour is nearly always enchanted with protection four.
 */
public final class ArmorTier {
	private ArmorTier() {
	}

	/**
	 * The protection level that is assumed for a piece of armour whose
	 * enchantments the server hid.
	 *
	 * <p>Zero when the material is unknown, then nothing is assumed and the
	 * damage is modelled as if the piece had no protection at all.
	 */
	public static int assumedProtection(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}

		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);

		if (equippable == null) {
			return 0;
		}

		Optional<ResourceKey<EquipmentAsset>> asset = equippable.assetId();

		if (asset.isEmpty()) {
			return 0;
		}

		ResourceKey<EquipmentAsset> key = asset.get();

		if (EquipmentAssets.DIAMOND.equals(key) || EquipmentAssets.NETHERITE.equals(key)) {
			return 4;
		}

		if (EquipmentAssets.IRON.equals(key)) {
			return 2;
		}

		if (EquipmentAssets.LEATHER.equals(key) || EquipmentAssets.CHAINMAIL.equals(key)
			|| EquipmentAssets.GOLD.equals(key) || EquipmentAssets.COPPER.equals(key)) {
			return 1;
		}

		return 0;
	}
}
