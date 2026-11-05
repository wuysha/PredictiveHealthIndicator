package dev.phi.predictivehealth.model;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reimplementation of the vanilla melee damage pipeline for a single swing.
 *
 * <p>Only information the client legitimately owns is used: the attacker's
 * attributes (weapon damage, custom attribute items), the swing cooldown, the
 * enchantments on the weapon and the defender's armour.
 *
 * <p>The numbers used here are the real vanilla ones. A player has a base
 * attack damage of {@code 1}, a wooden sword adds {@code 3} and every material
 * tier adds one more, so a diamond sword is {@code 7} and a netherite sword is
 * {@code 8}. Sharpness adds {@code 1 + 0.5 * (level - 1)} on top of that.
 */
public final class DamageModel {
	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** Vanilla refuses to apply damage inside this many ticks after the last one. */
	public static final int HURT_WINDOW_TICKS = 10;

	/** Attack damage of a player without any weapon. */
	public static final float PLAYER_BASE_ATTACK_DAMAGE = 1.0F;

	/** Strength adds three flat attack damage per level. */
	public static final float STRENGTH_PER_LEVEL = 3.0F;

	/** Weakness removes four flat attack damage per level. */
	public static final float WEAKNESS_PER_LEVEL = 4.0F;

	/** Fall damage only starts after this many blocks. */
	public static final float SAFE_FALL_DISTANCE = 3.0F;

	/** Damage of one burning tick. */
	public static final float FIRE_DAMAGE = 1.0F;

	/** Damage of one lava tick. */
	public static final float LAVA_DAMAGE = 4.0F;

	/** Protection points the feather falling enchantment adds to a fall. */
	public static final int FEATHER_FALLING_PER_LEVEL = 3;

	/**
	 * Protection points the blast protection adds against an explosion.
	 *
	 * <p>The vanilla data of the enchantment gives two points for the first level
	 * and two more for every level above it, so the level four is worth eight
	 * points.
	 */
	public static final int BLAST_PROTECTION_PER_LEVEL = 2;

	/** Protection points the vanilla damage pipeline never goes beyond. */
	public static final int MAX_PROTECTION_POINTS = 20;

	/** Base damage of an arrow, the speed of the arrow is multiplied with it. */
	public static final double ARROW_BASE_DAMAGE = 2.0D;

	/** Fastest an arrow can fly, three blocks per tick. */
	public static final double ARROW_MAX_SPEED = 3.0D;

	/** Power of a vanilla tnt, the explosion minecart uses the same one. */
	public static final float DEFAULT_EXPLOSION_POWER = 4.0F;

	private DamageModel() {
	}

	/**
	 * Bonus damage granted by the Sharpness enchantment.
	 * Vanilla uses {@code 1 + 0.5 * (level - 1)}.
	 */
	public static float enchantmentDamage(ItemStack weapon) {
		int level = EnchantmentReader.level(weapon, EnchantmentReader.sharpness());

		if (level <= 0) {
			return 0.0F;
		}

		return 1.0F + 0.5F * (level - 1);
	}

	/**
	 * Strength is a flat attribute modifier on the attack damage.
	 *
	 * <p>Vanilla applies effect modifiers on the server only, so the client
	 * side attribute value does not contain the bonus and has to be added here.
	 */
	public static float strengthBonus(Player attacker) {
		MobEffectInstance effect = attacker.getEffect(MobEffects.STRENGTH);

		if (effect == null) {
			return 0.0F;
		}

		return STRENGTH_PER_LEVEL * (effect.getAmplifier() + 1);
	}

	/**
	 * Attack damage of the attacker, weapon and Strength included.
	 *
	 * <p>The attack damage is not one of the attributes the server sends, so the
	 * value the client knows only holds the base of one. The damage of the weapon
	 * is taken from the item in the hand instead, exactly like the server builds
	 * it.
	 */
	public static float attackDamage(Player attacker) {
		return attackDamage(attacker, attacker.getMainHandItem());
	}

	/**
	 * Attack damage of the attacker with a known weapon.
	 *
	 * <p>The servers that protect against health indicators rewrite the equipment
	 * of every player, so the weapon of our own hand is taken from our own
	 * inventory, where the server sends the real stack.
	 */
	public static float attackDamage(Player attacker, ItemStack weapon) {
		float attribute = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		float fromWeapon = weaponAttackDamage(weapon)
			+ strengthBonus(attacker)
			- weaknessPenalty(attacker);

		return Math.max(attribute, fromWeapon);
	}

	/**
	 * Weakness is a flat attribute modifier on the attack damage, the server
	 * applies it to the attribute and the client has to add it by hand.
	 */
	public static float weaknessPenalty(Player attacker) {
		MobEffectInstance effect = attacker.getEffect(MobEffects.WEAKNESS);

		if (effect == null) {
			return 0.0F;
		}

		return WEAKNESS_PER_LEVEL * (effect.getAmplifier() + 1);
	}

	/**
	 * Attack damage of an item held in the main hand, on top of the player base.
	 *
	 * <p>Used to estimate the hits of other players, whose attribute values are
	 * not sent to us.
	 */
	public static float weaponAttackDamage(ItemStack stack) {
		if (stack.isEmpty()) {
			return PLAYER_BASE_ATTACK_DAMAGE;
		}

		double[] added = {0.0D};
		double[] multiplied = {0.0D};

		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.value() != Attributes.ATTACK_DAMAGE.value()) {
				return;
			}

			switch (modifier.operation()) {
				case ADD_VALUE -> added[0] += modifier.amount();
				case ADD_MULTIPLIED_BASE -> added[0] += modifier.amount() * PLAYER_BASE_ATTACK_DAMAGE;
				case ADD_MULTIPLIED_TOTAL -> multiplied[0] += modifier.amount();
			}
		});

		return (float) ((PLAYER_BASE_ATTACK_DAMAGE + added[0]) * (1.0D + multiplied[0]));
	}

	/**
	 * Mirrors {@code Player#canCriticalAttack}. Vanilla also requires the
	 * attack cooldown to be nearly full, otherwise the hit is not critical.
	 */
	public static boolean isCritical(Player attacker, float cooldownProgress) {
		return cooldownProgress > 0.9F
			&& attacker.fallDistance > 0.0F
			&& !attacker.onGround()
			&& !attacker.onClimbable()
			&& !attacker.isInWater()
			&& !attacker.hasEffect(MobEffects.BLINDNESS)
			&& !attacker.isPassenger()
			&& !attacker.isSprinting();
	}

	/**
	 * Raw damage before the defender reduces it.
	 *
	 * <p>Vanilla computes {@code base * (0.2 + cooldown^2 * 0.8)}, applies the
	 * critical multiplier to that base part only and adds the cooldown scaled
	 * enchantment bonus afterwards.
	 */
	public static float rawDamage(CombatSnapshot snapshot) {
		if (snapshot.targetBlocking()) {
			// A shield that is held up in the direction of the attacker takes the
			// whole hit, vanilla reduces the damage by a hundred percent.
			return 0.0F;
		}

		float cooldown = snapshot.cooldownProgress();
		float base = snapshot.attackDamage() * (0.2F + cooldown * cooldown * 0.8F);

		if (snapshot.critical()) {
			base *= 1.5F;
		}

		return base + cooldown * snapshot.enchantmentDamage();
	}

	/**
	 * Raw damage of a swing without any cooldown information.
	 *
	 * <p>Used when another player hits somebody and only their weapon is known.
	 */
	public static float rawDamage(float attackDamage, float enchantmentDamage) {
		return attackDamage + enchantmentDamage;
	}

	/**
	 * Damage of a fall, exactly like {@code LivingEntity#computeFallDamage}.
	 *
	 * <p>The safe fall distance is the attribute of the opponent, so boots with
	 * the feather falling enchantment are taken into account.
	 */
	public static float fallDamage(double fallDistance, double safeFallDistance, double fallDamageMultiplier) {
		double unsafe = fallDistance + 1.0E-6D - safeFallDistance;
		return (float) Math.max(0.0D, Math.floor(unsafe * fallDamageMultiplier));
	}

	/**
	 * Runs the raw damage through armour, resistance, protection and absorption
	 * exactly like {@code LivingEntity#applyDamage} does.
	 *
	 * <p>The protection points of the target are the ones that work against every
	 * damage. The points of the blast protection only count against an
	 * explosion, so they are not part of them.
	 */
	public static DamageBreakdown mitigate(TargetState target, float raw) {
		return mitigate(target, raw, 0);
	}

	/**
	 * Runs the raw damage through the vanilla pipeline.
	 *
	 * @param extraProtection protection points that only apply to this damage,
	 *                        the feather falling enchantment for a fall
	 */
	public static DamageBreakdown mitigate(TargetState target, float raw, int extraProtection) {
		float damage = Math.max(raw, 0.0F);

		float divisor = 2.0F + (float) target.toughness / 4.0F;
		float effectiveArmor = Mth.clamp((float) target.armor - damage / divisor, (float) target.armor * 0.2F, 20.0F);
		float afterArmor = damage * (1.0F - effectiveArmor / 25.0F);

		float afterResistance = afterArmor * target.resistanceFactor();
		int protection = target.protection + extraProtection;
		float afterProtection = afterResistance * (1.0F - Math.min(protection, 20) / 25.0F);

		float absorbed = Math.min((float) target.absorption, afterProtection);
		float toHealth = afterProtection - absorbed;

		return new DamageBreakdown(raw, afterArmor, afterResistance, afterProtection, absorbed, toHealth);
	}

	/**
	 * Ratio between raw damage and the health it removes for this target. Used to
	 * turn an estimated health loss back into a raw damage value.
	 */
	public static float healthRatio(TargetState target) {
		float divisor = 2.0F + (float) target.toughness / 4.0F;
		float effectiveArmor = Mth.clamp((float) target.armor - 6.0F / divisor, (float) target.armor * 0.2F, 20.0F);
		float armorFactor = 1.0F - effectiveArmor / 25.0F;
		float protectionFactor = 1.0F - Math.min(target.protection, 20) / 25.0F;
		return Math.max(0.05F, armorFactor * target.resistanceFactor() * protectionFactor);
	}

	/**
	 * Protection points the feather falling enchantment adds to a fall.
	 *
	 * <p>Every level of the enchantment is worth three points, they only count
	 * for the damage of a fall.
	 */
	public static int fallProtectionPoints(ArmorProbe armor) {
		return armor.level(EquipmentSlot.FEET, EnchantmentReader.featherFalling()) * FEATHER_FALLING_PER_LEVEL;
	}

	/**
	 * Raw damage of an arrow, exactly like {@code AbstractArrow}
	 * calculates it.
	 *
	 * @param speed    speed of the arrow in blocks per tick
	 * @param critical true when the arrow hit was a critical hit
	 */
	public static float arrowDamage(double speed, boolean critical) {
		double clamped = Math.min(Math.max(speed, 0.0D), ARROW_MAX_SPEED);
		double raw = Math.ceil(clamped * ARROW_BASE_DAMAGE);
		// A critical hit adds a random amount between zero and half of the
		// damage, so the middle of that range is used.
		return (float) (critical ? raw * 1.25D : raw);
	}

	/**
	 * Damage of an explosion of a minecart with dynamite, by the distance to the
	 * opponent.
	 *
	 * <p>The enchantments of the opponent are hidden by the servers that protect
	 * against health indicators, so the mitigation of the vanilla model cannot be
	 * calculated and the model would take far too much health away. The table
	 * below holds the damage that was really measured on such a server.
	 *
	 * <ul>
	 * <li>the explosion at the feet of the opponent takes six and a half hearts</li>
	 * <li>one block away it takes six hearts</li>
	 * <li>two blocks away it takes five hearts</li>
	 * <li>three blocks away it takes three hearts</li>
	 * </ul>
	 *
	 * @param distance distance of the opponent to the centre of the explosion
	 */
	public static float explosionDamageByDistance(double distance) {
		if (distance < 1.0D) {
			return 13.0F;
		}

		if (distance < 2.0D) {
			return 12.0F;
		}

		if (distance < 3.0D) {
			return 10.0F;
		}

		if (distance < 4.0D) {
			return 6.0F;
		}

		return 0.0F;
	}

	/**
	 * Ratio between the damage of an explosion in the open and the damage behind
	 * a cover.
	 *
	 * <p>Vanilla samples the body of the entity and lowers the damage with the
	 * part that is covered by a block. The table of the distance holds the damage
	 * in the open, so it is scaled with the same ratio.
	 */
	public static float explosionCoverRatio(double distance, float power, float exposure) {
		float f = power * 2.0F;
		double open = 1.0D - distance / f;

		if (open <= 0.0D) {
			return 0.0F;
		}

		float full = explosionDamageFromExposure(open, power);
		float covered = explosionDamageFromExposure(open * exposure, power);

		return full <= 0.0F ? 0.0F : covered / full;
	}

	/**
	 * Damage of an explosion without the mitigation of the armour.
	 *
	 * <p>The table of the distance already holds the damage that was really
	 * dealt, so only the absorption is left to apply.
	 */
	public static DamageBreakdown explosionDamage(TargetState target, float damage) {
		float absorbed = Math.min((float) target.absorption, damage);

		return new DamageBreakdown(damage, damage, damage, damage, absorbed, damage - absorbed);
	}

	/**
	 * Damage of an explosion when the distance and the blocked part of the body
	 * are already reduced to the factor of the explosion.
	 */
	public static float explosionDamageFromExposure(double e, float power) {
		float f = power * 2.0F;
		return (float) ((e * e + e) / 2.0 * 7.0 * f + 1.0);
	}

	/**
	 * The raw damage of an explosion, before the armour and the enchantments.
	 *
	 * <p>Vanilla lowers the damage with the distance to the centre of the
	 * explosion and with the part of the body that a block covers. The result is
	 * the amount the health bar would lose without any armour.
	 */
	public static float explosionRawDamage(double distance, float power, float exposure) {
		float f = power * 2.0F;
		double open = 1.0D - distance / f;

		if (open <= 0.0D) {
			return 0.0F;
		}

		return explosionDamageFromExposure(open * exposure, power);
	}

	/**
	 * The damage an explosion leaves after the armour of the target.
	 *
	 * <p>The armour works the same way as for any other damage, the effective
	 * armour shrinks with the size of the damage.
	 */
	public static float explosionAfterArmour(TargetState target, float raw) {
		float divisor = 2.0F + (float) target.toughness / 4.0F;
		float effectiveArmor = Mth.clamp((float) target.armor - raw / divisor,
			(float) target.armor * 0.2F, 20.0F);
		return raw * (1.0F - effectiveArmor / 25.0F);
	}

	/**
	 * Part of an entity that is hit by an explosion.
	 *
	 * <p>Vanilla samples the bounding box of the entity on a three by three by
	 * three grid and counts the samples that are not blocked by a block. The
	 * blocks of the client world are the ones the server sent, so the result is
	 * close to the value of the server.
	 */
	public static float explosionExposure(ClientLevel world, Entity entity, Vec3 center) {
		AABB box = entity.getBoundingBox();
		double d = 1.0D / ((box.maxX - box.minX) * 2.0D + 1.0D);
		double e = 1.0D / ((box.maxY - box.minY) * 2.0D + 1.0D);
		double f = 1.0D / ((box.maxZ - box.minZ) * 2.0D + 1.0D);
		double g = (1.0D - Math.floor(1.0D / d) * d) / 2.0D;
		double h = (1.0D - Math.floor(1.0D / f) * f) / 2.0D;
		int exposed = 0;
		int samples = 0;

		for (double k = 0.0D; k <= 1.0D; k += d) {
			for (double l = 0.0D; l <= 1.0D; l += e) {
				for (double m = 0.0D; m <= 1.0D; m += f) {
					double n = Mth.lerp(k, box.minX, box.maxX);
					double o = Mth.lerp(l, box.minY, box.maxY);
					double p = Mth.lerp(m, box.minZ, box.maxZ);
					Vec3 start = new Vec3(n + g, o, p + h);

					if (world.clip(new ClipContext(start, center,
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType()
						== HitResult.Type.MISS) {
						exposed++;
					}

					samples++;
				}
			}
		}

		return samples == 0 ? 0.0F : (float) exposed / samples;
	}

	/**
	 * Armour points of the worn armour.
	 *
	 * <p>The armour attribute of another player is only sent to the client when
	 * the server sends it, so the value is also calculated from the equipment the
	 * client knows. That is the same sum the server builds.
	 */
	public static double equipmentArmor(Player entity) {
		return equipmentAttribute(entity, Attributes.ARMOR);
	}

	/** Armour toughness of the worn armour. */
	public static double equipmentToughness(Player entity) {
		return equipmentAttribute(entity, Attributes.ARMOR_TOUGHNESS);
	}

	private static double equipmentAttribute(Player entity, Holder<Attribute> attribute) {
		double base = entity.getAttributeBaseValue(attribute);
		double[] added = {0.0D};
		double[] multiplied = {0.0D};

		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = entity.getItemBySlot(slot);

			if (stack.isEmpty()) {
				continue;
			}

			stack.forEachModifier(slot, (entry, modifier) -> {
				if (entry.value() != attribute.value()) {
					return;
				}

				switch (modifier.operation()) {
					case ADD_VALUE -> added[0] += modifier.amount();
					case ADD_MULTIPLIED_BASE -> added[0] += modifier.amount() * base;
					case ADD_MULTIPLIED_TOTAL -> multiplied[0] += modifier.amount();
				}
			});
		}

		return (base + added[0]) * (1.0D + multiplied[0]);
	}

	/**
	 * True when the defender is holding a shield up and the attacker is inside
	 * the blocking cone, which fully negates the hit.
	 */
	public static boolean isShieldBlocking(Player defender, Player attacker) {
		if (defender.getItemBlockingWith() == null) {
			return false;
		}

		Vec3 look = defender.getHeadLookAngle();
		Vec3 toAttacker = attacker.position().subtract(defender.position());
		toAttacker = new Vec3(toAttacker.x, 0.0D, toAttacker.z).normalize();
		double angle = Math.acos(Math.min(1.0D, Math.max(-1.0D, toAttacker.dot(look))));
		return angle <= Math.PI / 2.0D;
	}
}
