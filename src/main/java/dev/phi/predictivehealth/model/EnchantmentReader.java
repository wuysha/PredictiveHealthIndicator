package dev.phi.predictivehealth.model;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads enchantment levels off an item stack.
 *
 * <p>Enchantments are looked up by their registry key, which is stable across
 * datapacks and does not require the registry to be present.
 */
public final class EnchantmentReader {
	private EnchantmentReader() {
	}

	public static ResourceKey<Enchantment> sharpness() {
		return Enchantments.SHARPNESS;
	}

	public static ResourceKey<Enchantment> protection() {
		return Enchantments.PROTECTION;
	}

	public static ResourceKey<Enchantment> featherFalling() {
		return Enchantments.FEATHER_FALLING;
	}

	public static ResourceKey<Enchantment> blastProtection() {
		return Enchantments.BLAST_PROTECTION;
	}

	/**
	 * True when the enchantments of the stack cannot be the real ones.
	 *
	 * <p>The servers that protect against health indicators rewrite the equipment
	 * of every player and put a fortune three on every enchanted item, even on a
	 * piece of armour that cannot be enchanted with fortune. An enchantment that
	 * does not fit the item is a sign that the stack was rewritten.
	 */
	public static boolean isRewritten(ItemStack stack) {
		ItemEnchantments component = stack.get(DataComponents.ENCHANTMENTS);

		if (component == null) {
			return false;
		}

		for (Object2IntMap.Entry<Holder<Enchantment>> entry : component.entrySet()) {
			if (!entry.getKey().value().isSupportedItem(stack)) {
				return true;
			}
		}

		return false;
	}

	public static int level(ItemStack stack, ResourceKey<Enchantment> enchantment) {
		if (stack.isEmpty() || enchantment == null) {
			return 0;
		}

		ItemEnchantments component = stack.get(DataComponents.ENCHANTMENTS);

		if (component == null) {
			return 0;
		}

		for (Object2IntMap.Entry<Holder<Enchantment>> entry : component.entrySet()) {
			if (matches(entry.getKey(), enchantment)) {
				return entry.getIntValue();
			}
		}

		return 0;
	}

	/** Short description of the enchantments of a stack, for the debug output. */
	public static String describe(ItemStack stack) {
		ItemEnchantments component = stack.get(DataComponents.ENCHANTMENTS);

		if (component == null) {
			return "-";
		}

		StringBuilder builder = new StringBuilder();

		for (Object2IntMap.Entry<Holder<Enchantment>> entry : component.entrySet()) {
			if (builder.length() > 0) {
				builder.append(',');
			}

			builder.append(path(entry.getKey())).append(entry.getIntValue());
		}

		return builder.length() == 0 ? "-" : builder.toString();
	}

	/**
	 * The names of the enchantments of a stack, the way the game shows them.
	 *
	 * <p>The list is empty when the stack carries no enchantments and also when
	 * the server rewrote them, because the names would be the ones of the server
	 * and not the real ones.
	 */
	public static List<Component> names(ItemStack stack) {
		if (stack.isEmpty() || isRewritten(stack)) {
			return List.of();
		}

		ItemEnchantments component = stack.get(DataComponents.ENCHANTMENTS);

		if (component == null) {
			return List.of();
		}

		List<Component> names = new ArrayList<>();

		for (Object2IntMap.Entry<Holder<Enchantment>> entry : component.entrySet()) {
			names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
		}

		return names;
	}

	/**
	 * The name of the blast protection with its level, the way the game shows it.
	 *
	 * <p>The name is built from the translation keys, because the registry entry
	 * of the enchantment is not needed to write it.
	 */
	public static Component blastProtectionName(int level) {
		if (level <= 0) {
			return Component.empty();
		}

		return Component.translatable("enchantment.minecraft.blast_protection")
			.append(Component.translatable("enchantment.level." + level));
	}

	private static String path(Holder<Enchantment> entry) {
		return entry.unwrapKey().map(key -> key.identifier().getPath()).orElse("?");
	}

	/**
	 * True when the holder stands for the given registry key.
	 *
	 * <p>The keys of the registry are interned, so the comparison of the
	 * identifiers is enough and it also works for a holder that is not bound to
	 * a registry.
	 */
	private static boolean matches(Holder<Enchantment> holder, ResourceKey<Enchantment> key) {
		if (holder.is(key)) {
			return true;
		}

		return holder.unwrapKey()
			.map(bound -> bound.identifier().equals(key.identifier())
				&& bound.registry().equals(key.registry()))
			.orElse(false);
	}
}
