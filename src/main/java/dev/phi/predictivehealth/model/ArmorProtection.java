package dev.phi.predictivehealth.model;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The protection points of a player, split by the damage they work against.
 *
 * <p>Vanilla counts the points of the protection enchantment against every
 * damage and the points of the blast protection only against an explosion. The
 * two enchantments share one exclusive set, so a piece that carries the blast
 * protection cannot carry the protection enchantment and gives no points
 * against a hit at all.
 *
 * <p>A player with three pieces at protection four and leggings at blast
 * protection four therefore has twelve points against a hit and twenty points
 * against an explosion. A hit takes away more health than four pieces at
 * protection four would let through, so the damage the mod shows for that
 * player has to be bigger.
 */
public final class ArmorProtection {
	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** Points that work against every damage. */
	private final int general;
	/** Points that work only against an explosion. */
	private final int blast;
	/** Points that work against an explosion, the general ones and the blast ones. */
	private final int explosion;
	/**
	 * The points the armour would have if no piece carried the blast
	 * protection, the reference the measurements are made against.
	 */
	private final int baseline;
	/** The baseline points of the leggings, the piece the blast protection sits on. */
	private final int leggingsBaseline;

	private ArmorProtection(int general, int blast, int baseline, int leggingsBaseline) {
		this.general = general;
		this.blast = blast;
		this.explosion = general + blast;
		this.baseline = baseline;
		this.leggingsBaseline = leggingsBaseline;
	}

	/**
	 * The protection of a player.
	 *
	 * <p>Every piece is looked at on its own. A piece whose real enchantments
	 * are known, either because it was seen on the ground or because the server
	 * did not hide them, is read as it is. A piece whose enchantments the server
	 * hid counts with the protection that its material stands for, unless the
	 * blast protection of the leggings is known.
	 *
	 * @param probe         the equipment as it arrived from the server
	 * @param profile       the pieces of the player whose real enchantments are known
	 * @param signal        the blast protection the server shows in its attributes
	 * @param measuredLevel the level of the blast protection that was measured from
	 *                      the damage of an explosion, null when nothing was measured
	 */
	public static ArmorProtection of(ArmorProbe probe, ArmorProfile profile, BlastSignal signal,
		Integer measuredLevel) {
		int general = 0;
		int blast = 0;
		int baseline = 0;
		int leggingsBaseline = 0;

		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack worn = probe.stack(slot);

			if (worn.isEmpty()) {
				// The slot is empty, so the piece that was learned for it lies
				// on the ground and protects nobody.
				continue;
			}

			ItemStack known = profile.piece(slot);
			boolean learned = !known.isEmpty() && known.getItem() == worn.getItem();

			int protection;
			int blastLevel;
			int signalled = signal == null ? 0 : signal.level(slot);

			if (signalled > 0) {
				// The server named the enchantment and the slot in the modifiers
				// of its attributes, so this piece carries the blast protection.
				// The protection enchantment shares the exclusive set with it, so
				// the piece gives no points against a hit.
				protection = 0;
				blastLevel = signalled;
			} else if (learned) {
				protection = EnchantmentReader.level(known, EnchantmentReader.protection());
				blastLevel = EnchantmentReader.level(known, EnchantmentReader.blastProtection());
			} else if (!probe.rewritten(slot)) {
				protection = EnchantmentReader.level(worn, EnchantmentReader.protection());
				blastLevel = EnchantmentReader.level(worn, EnchantmentReader.blastProtection());
			} else {
				// The server hid the enchantments, so only the material of the
				// piece is known.
				protection = 0;
				blastLevel = 0;

				if (slot == EquipmentSlot.LEGS) {
					// The blast protection of the leggings is the one the mod can
					// measure, so the measured level is used here.
					blastLevel = measuredLevel == null ? 0 : measuredLevel;
				}
			}

			int assumed = ArmorTier.assumedProtection(worn);
			// A piece that carries the blast protection would have carried the
			// protection of its material without it, that is the reference the
			// measurement of the level is made against.
			int baselinePoints = blastLevel > 0 ? assumed : (protection > 0 ? protection : assumed);

			baseline += baselinePoints;

			if (blastLevel > 0) {
				// The blast protection and the protection enchantment share one
				// exclusive set, so the piece gives no points against a hit.
				blast += blastLevel * DamageModel.BLAST_PROTECTION_PER_LEVEL;
			} else {
				general += protection > 0 ? protection : assumed;
			}

			if (slot == EquipmentSlot.LEGS) {
				leggingsBaseline = baselinePoints;
			}
		}

		return new ArmorProtection(general, blast, baseline, leggingsBaseline);
	}

	/** Points that work against every damage. */
	public int general() {
		return general;
	}

	/** Points that work only against an explosion. */
	public int blast() {
		return blast;
	}

	/** Points that work against an explosion. */
	public int explosion() {
		return explosion;
	}

	/** The points the armour would have without any blast protection. */
	public int baseline() {
		return baseline;
	}

	/** The baseline points of the leggings. */
	public int leggingsBaseline() {
		return leggingsBaseline;
	}
}
