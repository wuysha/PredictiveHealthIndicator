package dev.phi.predictivehealth.model;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * The blast protection of a player, read from the attribute modifiers of the
 * server.
 *
 * <p>The blast protection adds the resistance against the knockback of an
 * explosion to the piece that carries it. The server sends the modifiers of the
 * attributes with every update, and the id of that modifier holds the
 * enchantment and the slot the piece sits in. The servers that hide the
 * enchantments rewrite the equipment packets, but they leave the attributes
 * alone, so the enchantment shows up here anyway.
 *
 * <p>The amount of the modifier tells the level, the vanilla effect adds fifteen
 * hundredths for the first level and fifteen hundredths more for every level
 * above it.
 */
public final class BlastSignal {
	/** The path the vanilla effect of the blast protection uses. */
	private static final String PATH = "enchantment.blast_protection/";

	/** The amount the effect adds for every level. */
	private static final double AMOUNT_PER_LEVEL = 0.15D;

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** The signal of a player without any blast protection. */
	public static final BlastSignal EMPTY = new BlastSignal(new EnumMap<>(EquipmentSlot.class));

	private final Map<EquipmentSlot, Integer> levels;

	private BlastSignal(Map<EquipmentSlot, Integer> levels) {
		this.levels = levels;
	}

	/**
	 * The blast protection of a player, as the attribute modifiers of the server
	 * show it.
	 *
	 * <p>Only a blast protection that the server shows is known here, everything
	 * else stays unknown. A player whose attribute packet did not arrive yet
	 * looks like a player without any blast protection.
	 */
	public static BlastSignal of(Player player) {
		EnumMap<EquipmentSlot, Integer> levels = new EnumMap<>(EquipmentSlot.class);
		AttributeInstance instance = player.getAttributes()
			.getInstance(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);

		if (instance == null) {
			return EMPTY;
		}

		for (EquipmentSlot slot : ARMOR_SLOTS) {
			Identifier id = Identifier.withDefaultNamespace(PATH + slot.getSerializedName());
			AttributeModifier modifier = instance.getModifier(id);

			if (modifier != null) {
				levels.put(slot, level(modifier.amount()));
			}
		}

		return levels.isEmpty() ? EMPTY : new BlastSignal(levels);
	}

	/**
	 * The level of the blast protection on a slot, zero when the server shows
	 * none.
	 */
	public int level(EquipmentSlot slot) {
		return levels.getOrDefault(slot, 0);
	}

	/** True when the server shows the blast protection on no slot at all. */
	public boolean isEmpty() {
		return levels.isEmpty();
	}

	/**
	 * The level the amount of the modifier stands for.
	 *
	 * <p>A server with its own data for the enchantment gives another amount, so
	 * the level that is closest to it is used.
	 */
	private static int level(double amount) {
		int level = (int) Math.round(amount / AMOUNT_PER_LEVEL);
		return Math.max(1, Math.min(4, level));
	}
}
