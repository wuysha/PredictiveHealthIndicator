package dev.phi.predictivehealth.model;

import com.mojang.datafixers.util.Pair;
import dev.phi.predictivehealth.PhiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the running estimate of every player in the world.
 *
 * <p>Inputs are limited to packets the vanilla client already receives:
 * damage events, entity attributes, entity status and the synced equipment and
 * entity data. No scoreboard, no TAB, no team packets.
 */
public final class TargetTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("PredictiveHealthIndicator");

	/** Vanilla entity status byte that is broadcast when an entity dies. */
	private static final byte STATUS_DEATH = 3;
	/** Vanilla entity status byte that is broadcast when a totem saves a player. */
	private static final byte STATUS_TOTEM = 35;
	private static final int PENDING_TIMEOUT = 8;
	/** Ticks an item has to be used before the consumption counts. */
	private static final int MIN_USE_TICKS = 4;
	private static final double MAX_ATTACK_RANGE = 4.0D;
	private static final double SPLASH_RADIUS = 4.0D;
	/** Ticks the explosion packet may take to arrive after the damage packet. */
	private static final int EXPLOSION_TIMEOUT = 4;
	/** Horizontal knockback an explosion has to cause to be used as a hint. */
	private static final float MIN_EXPLOSION_KNOCKBACK = 0.5F;
	/** Ticks an arrow is remembered after it left the world. */
	private static final int ARROW_MEMORY = 100;
	/** Ticks a dropped piece of armour may take to be noticed. */
	private static final int DROP_WINDOW = 100;
	/** Blocks a player may stand away from a piece they just dropped. */
	private static final double OWNER_RADIUS = 1.0D;
	/** Ticks between the taking off of a piece and the moment it shows up. */
	private static final int DROP_LINK_WINDOW = 20;
	/** Ticks the learned pieces of a player stay believable. */
	private static final int LEARNED_TIMEOUT = 1200;
	/** Ticks the armour of a dead player may take to fall on the ground. */
	private static final int DEATH_DROP_WINDOW = 100;
	/** Blocks a piece may lie away from the place its owner died. */
	private static final double DEATH_DROP_RADIUS = 2.5D;

	public static long currentTick;

	/** Cooldown of the last swing, used when the snapshot of a hit is gone. */
	private static float lastCooldown = 1.0F;

	private static final Map<Integer, TargetState> TARGETS = new HashMap<>();
	private static final Map<Integer, CombatSnapshot> PENDING = new HashMap<>();
	private static final Map<Integer, PotionRecord> POTIONS = new HashMap<>();
	private static final Map<Integer, ArrowRecord> ARROWS = new HashMap<>();
	/** The armour of every player, as it arrived in the equipment packets. */
	private static final Map<Integer, ArmorProbe> EQUIPMENT = new HashMap<>();
	/** Tick the item entities were spawned at, to spot a fresh drop. */
	private static final Map<Integer, Long> SPAWNED_AT = new HashMap<>();
	/** Tick a player took a piece of armour off, per slot. */
	private static final Map<Integer, Map<EquipmentSlot, Long>> SLOT_DROPS = new HashMap<>();
	/** The pieces of armour whose real enchantments are known, per player name. */
	private static final Map<String, ArmorProfile> LEARNED = new HashMap<>();
	/** Tick the last piece of a player was learned at. */
	private static final Map<String, Long> LEARNED_AT = new HashMap<>();
	/** Entity of the piece a learned piece came from, per player and slot. */
	private static final Map<String, Map<EquipmentSlot, Integer>> LEARNED_ITEM = new HashMap<>();
	/** Tick a player died, to catch the armour that fell from them. */
	private static final Map<Integer, Long> DEATHS = new HashMap<>();
	/** Place a player died at. */
	private static final Map<Integer, Vec3> DEATH_POS = new HashMap<>();
	/** Item entities that were already looked at. */
	private static final Set<Integer> CLAIMED = new HashSet<>();
	/** Correction of the damage, learned from a death or a totem. */
	private static final Map<String, Double> DAMAGE_SCALES = new HashMap<>();
	/** Profile of a player nothing is known about. */
	private static final ArmorProfile EMPTY_PROFILE = new ArmorProfile();

	private TargetTracker() {
	}

	public static void reset() {
		TARGETS.clear();
		PENDING.clear();
		POTIONS.clear();
		ARROWS.clear();
		EQUIPMENT.clear();
		SPAWNED_AT.clear();
		SLOT_DROPS.clear();
		LEARNED.clear();
		LEARNED_AT.clear();
		LEARNED_ITEM.clear();
		DEATHS.clear();
		DEATH_POS.clear();
		CLAIMED.clear();
		DAMAGE_SCALES.clear();
		currentTick = 0L;
	}

	/**
	 * Called for every equipment packet of the server.
	 *
	 * <p>The packet holds the armour of a player exactly as it was sent, so it is
	 * the only place where the real enchantments can be looked at. The servers
	 * that protect against health indicators rewrite those packets, and the probe
	 * keeps the stacks so the mod can tell a real enchantment from a fake one.
	 */
	public static void onEquipment(ClientboundSetEquipmentPacket packet) {
		ArmorProbe probe = EQUIPMENT.computeIfAbsent(packet.getEntity(), id -> new ArmorProbe());

		for (Pair<EquipmentSlot, ItemStack> entry : packet.getSlots()) {
			ItemStack stack = entry.getSecond();

			if (stack == null) {
				continue;
			}

			ItemStack previous = probe.stack(entry.getFirst());
			probe.put(entry.getFirst(), stack);

			if (stack.isEmpty() && !previous.isEmpty()) {
				// The piece was taken off, so a piece of that armour shows up on
				// the ground right next to the player.
				SLOT_DROPS.computeIfAbsent(packet.getEntity(), id -> new EnumMap<>(EquipmentSlot.class))
					.put(entry.getFirst(), currentTick);
			}
		}

		LOGGER.info("equipment {} {}", packet.getEntity(), probe.describe());
	}

	/**
	 * The pieces of armour of a player whose real enchantments are known.
	 *
	 * <p>The profile is empty when nothing was learned about the player yet.
	 */
	public static ArmorProfile profile(String name) {
		return LEARNED.getOrDefault(name, EMPTY_PROFILE);
	}

	/**
	 * The server has just created an entity.
	 *
	 * <p>An item that comes with a chunk is not sent as a spawn packet, so the
	 * spawn of an item tells that it was just dropped instead of lying on the
	 * ground already.
	 */
	public static void onEntitySpawn(int entityId, boolean item) {
		// The server reuses the ids of the entities, so a new entity is never a
		// piece that was already looked at.
		CLAIMED.remove(entityId);

		if (item) {
			SPAWNED_AT.put(entityId, currentTick);
		}
	}

	/** The armour of a player as it arrived in the equipment packets. */
	public static ArmorProbe armor(int entityId) {
		return EQUIPMENT.getOrDefault(entityId, ArmorProbe.EMPTY);
	}

	public static Collection<TargetState> tracked() {
		return TARGETS.values();
	}

	/** Called when the local player swings at an entity. */
	public static void onAttack(Player attacker, Entity target) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.player != attacker || !(target instanceof Player victim)) {
			return;
		}

		if (victim.isSpectator() || attacker.distanceToSqr(victim) > MAX_ATTACK_RANGE * MAX_ATTACK_RANGE) {
			return;
		}

		float cooldown = attacker.getAttackStrengthScale(0.5F);
		ItemStack weapon = ownWeapon(attacker);
		CombatSnapshot snapshot = new CombatSnapshot(
			target.getId(),
			DamageModel.attackDamage(attacker, weapon),
			cooldown,
			DamageModel.enchantmentDamage(weapon),
			DamageModel.isCritical(attacker, cooldown),
			DamageModel.isShieldBlocking(victim, attacker),
			currentTick
		);

		PENDING.put(target.getId(), snapshot);
		lastCooldown = cooldown;
	}

	/**
	 * The weapon in our own hand with the real enchantments.
	 *
	 * <p>The servers that protect against health indicators rewrite the equipment
	 * of every player, so the sharpness of our own sword is hidden in the packet
	 * of the equipment. Our own inventory is not rewritten, the server sends the
	 * real stacks of it, so the sharpness is read from there.
	 */
	private static ItemStack ownWeapon(Player attacker) {
		Minecraft client = Minecraft.getInstance();
		ItemStack equipped = attacker.getMainHandItem();

		if (client.player != attacker) {
			return equipped;
		}

		ItemStack own = client.player.getInventory().getSelectedItem();

		if (own.isEmpty() || own.getItem() != equipped.getItem()) {
			return equipped;
		}

		return own;
	}

	/** Called for every damage event the client is told about. */
	public static void onDamage(ClientboundDamageEventPacket packet) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer me = client.player;

		if (me == null) {
			return;
		}

		int targetId = packet.entityId();
		int attackerId = packet.sourceCauseId();

		if (targetId == me.getId()) {
			return;
		}

		TargetState state = TARGETS.get(targetId);
		ResourceKey<DamageType> type = packet.sourceType().unwrapKey().orElse(null);

		if (isExplosionDamage(packet.sourceType(), type)) {
			// The explosion packet reaches us after the damage packet, so the
			// damage is calculated as soon as the explosion itself is known.
			if (state != null) {
				state.pendingExplosionTick = currentTick;
				state.explosionKnockback = 0.0F;
			}

			return;
		}

		if (isArrowDamage(type, packet.sourceDirectId())) {
			if (state != null) {
				applyArrowDamage(state, packet.sourceDirectId());
			}

			return;
		}

		if (attackerId == me.getId()) {
			// A melee hit names us as the attacker and as the source, a shot
			// names the projectile as the source. Only the first one can be
			// modelled with our weapon.
			if (packet.sourceDirectId() == me.getId()) {
				onOurHit(targetId);
			} else {
				if (state != null) {
					state.confidence = Math.max(0.0D, state.confidence - 0.05D);
				}
			}

			return;
		}

		if (state == null) {
			return;
		}

		if (type != null) {
			if (type == DamageTypes.FALL) {
				applyFallDamage(state);
				return;
			}

			if (type == DamageTypes.IN_FIRE || type == DamageTypes.ON_FIRE || type == DamageTypes.CAMPFIRE) {
				// Fire resistance makes the fire damage disappear completely.
				if (!state.fireResistant()) {
					applyKnownDamage(state, DamageModel.FIRE_DAMAGE);
				}

				return;
			}

			if (type == DamageTypes.LAVA) {
				if (!state.fireResistant()) {
					applyKnownDamage(state, DamageModel.LAVA_DAMAGE);
				}

				return;
			}
		}

		applyForeignDamage(state, attackerId);
	}

	/**
	 * True when the damage came from an explosion.
	 *
	 * <p>The vanilla tag covers TNT, creepers, beds, explosion minecarts and
	 * every other explosion. The names are checked as well, because the tag of
	 * the damage types is not part of the registry the client receives.
	 */
	private static boolean isExplosionDamage(Holder<DamageType> entry, ResourceKey<DamageType> type) {
		if (entry.is(DamageTypeTags.IS_EXPLOSION)) {
			return true;
		}

		if (type == null) {
			return false;
		}

		String path = type.identifier().getPath();

		return path.equals("explosion") || path.equals("player_explosion")
			|| path.equals("fireworks") || path.equals("bad_respawn_point");
	}

	/** True when the damage came from an arrow. */
	private static boolean isArrowDamage(ResourceKey<DamageType> type, int directId) {
		if (directId >= 0 && ARROWS.containsKey(directId)) {
			return true;
		}

		return type != null && type.identifier().getPath().equals("arrow");
	}

	/**
	 * The hit of an arrow. The speed of the arrow tells how much damage it
	 * does, a fully drawn one flies three blocks per tick and hits for six.
	 */
	private static void applyArrowDamage(TargetState state, int directId) {
		ArrowRecord arrow = ARROWS.get(directId);
		double speed = arrow == null ? DamageModel.ARROW_MAX_SPEED : arrow.speed;
		boolean critical = arrow != null && arrow.critical;

		float raw = DamageModel.arrowDamage(speed, critical);
		float effective = state.effectiveRawDamage(raw, currentTick);
		DamageBreakdown breakdown = DamageModel.mitigate(state, effective);

		state.applyExternalDamage(breakdown, currentTick);
		state.lastInteractionTick = currentTick;
	}

	/**
	 * The explosion of the world. The damage of an explosion depends on the
	 * distance to its centre, on the power of the explosion and on the part of
	 * the body that is not covered by a block.
	 *
	 * <p>The enchantments of the opponent are hidden by the servers that protect
	 * against health indicators, so the mitigation of the vanilla model cannot be
	 * calculated. The damage comes from the table of the distance instead, it
	 * holds the damage that was really measured on such a server.
	 */
	public static void onExplosion(ClientboundExplodePacket packet) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel world = client.level;

		if (world == null) {
			return;
		}

		Vec3 center = packet.center();
		float power = packet.radius();

		for (TargetState state : TARGETS.values()) {
			if (state.pendingExplosionTick == Long.MIN_VALUE) {
				continue;
			}

			state.pendingExplosionTick = Long.MIN_VALUE;
			Entity entity = world.getEntity(state.entityId);

			if (!(entity instanceof Player player)) {
				continue;
			}

			double distance = player.position().distanceTo(center);

			if (distance > power * 2.0D) {
				continue;
			}

			float exposure = DamageModel.explosionExposure(world, player, center);
			applyExplosionDamage(state, distance, power, exposure);
		}
	}

	/**
	 * The damage of an explosion and what it takes from the health of the
	 * opponent.
	 */
	private static void applyExplosionDamage(TargetState state, double distance, float power, float exposure) {
		float table = DamageModel.explosionDamageByDistance(distance);

		if (table <= 0.0F) {
			return;
		}

		// The table holds the damage of a minecart with dynamite, the other
		// explosions are scaled with their power.
		float damage = table * (power / DamageModel.DEFAULT_EXPLOSION_POWER);
		// A wall in front of the opponent takes a part of the damage away.
		damage *= DamageModel.explosionCoverRatio(distance, power, exposure);

		if (damage <= 0.0F) {
			return;
		}

		float effective = state.effectiveRawDamage(damage, currentTick);
		DamageBreakdown breakdown = DamageModel.explosionDamage(state, effective);

		state.applyExternalDamage(breakdown, currentTick);
		state.lastInteractionTick = currentTick;

		// The vanilla model of the same explosion, it tells how much of the
		// damage the armour and the enchantments took away.
		float raw = DamageModel.explosionRawDamage(distance, power, exposure);

		state.explosionRawDamage += raw;
		state.explosionArmourDamage += DamageModel.explosionAfterArmour(state, raw);
		state.explosionModelledDamage += breakdown.toHealth();
	}

	/** One of our own hits, modelled with the exact vanilla pipeline. */
	private static void onOurHit(int targetId) {
		CombatSnapshot snapshot = PENDING.remove(targetId);

		if (snapshot == null) {
			snapshot = fallbackSnapshot(targetId);
		}

		TargetState state = state(targetId);
		state.engaged = true;
		state.lastCooldown = snapshot.cooldownProgress();

		float raw = DamageModel.rawDamage(snapshot);
		float effective = state.effectiveRawDamage(raw, currentTick);
		DamageBreakdown breakdown = DamageModel.mitigate(state, effective);

		state.applyDamage(breakdown, currentTick);
		state.lastInteractionTick = currentTick;
		state.recentDamage = state.recentDamage * 0.7D + breakdown.toHealth() * 0.3D;
	}

	/**
	 * Fall damage. Vanilla deals {@code floor(fallDistance - 3)} and the armour
	 * reduces it like any other damage.
	 */
	private static void applyFallDamage(TargetState state) {
		double fall = observedFall(state);
		float raw = DamageModel.fallDamage(fall, state.safeFallDistance, state.fallDamageMultiplier);

		state.fallStartY = state.previousY;
		state.lastFallDistance = 0.0D;

		if (raw <= 0.0F) {
			// The server only sends the packet when there is damage, so the fall
			// was at least four blocks high even if we did not see it.
			raw = 1.0F;
		}

		applyKnownDamage(state, raw, state.fallProtection);
	}

	/** Burning and lava damage, the raw amounts are fixed in vanilla. */
	private static void applyKnownDamage(TargetState state, float raw) {
		applyKnownDamage(state, raw, 0);
	}

	private static void applyKnownDamage(TargetState state, float raw, int extraProtection) {
		float effective = state.effectiveRawDamage(raw, currentTick);
		DamageBreakdown breakdown = DamageModel.mitigate(state, effective, extraProtection);

		state.applyExternalDamage(breakdown, currentTick);
		state.lastInteractionTick = currentTick;
	}

	/**
	 * A hit of another player. Their weapon is synced to us, so the real damage
	 * of the swing can be estimated from it.
	 */
	private static void applyForeignDamage(TargetState state, int attackerId) {
		PhiConfig config = PhiConfig.get();

		if (!config.estimateUnknownDamage) {
			state.confidence = Math.max(0.0D, state.confidence - 0.05D);
			return;
		}

		TargetState attacker = TARGETS.get(attackerId);
		float raw;

		if (attacker != null) {
			raw = DamageModel.rawDamage(attacker.weaponDamage, attacker.weaponEnchantmentDamage);
		} else {
			raw = (float) (state.recentDamage / DamageModel.healthRatio(state));
		}

		float effective = state.effectiveRawDamage(raw, currentTick);
		DamageBreakdown breakdown = DamageModel.mitigate(state, effective);

		state.applyExternalDamage(breakdown, currentTick);
		state.lastInteractionTick = currentTick;
	}

	/** Called for entity status bytes, most importantly the death byte. */
	public static void onStatus(int entityId, byte status) {
		TargetState state = TARGETS.get(entityId);

		if (state == null) {
			return;
		}

		if (status == STATUS_DEATH) {
			if (state.alive) {
				// The health of a dead player is zero, so the damage of the model
				// can be checked against the real health.
				calibrate(state, 0.0D);
				measureBlastProtection(state, 0.0D);
				state.onDeath();
			}

			// The whole armour of a dead player falls on the ground, and the
			// pieces keep the enchantments the server hid.
			DEATHS.put(entityId, currentTick);

			Minecraft client = Minecraft.getInstance();

			if (client.level != null) {
				Entity entity = client.level.getEntity(entityId);

				if (entity != null) {
					DEATH_POS.put(entityId, entity.position());
				}
			}
		} else if (status == STATUS_TOTEM) {
			// The totem of undying saved the player, so the health is one point
			// and the absorption of the totem is up.
			calibrate(state, 1.0D);
			measureBlastProtection(state, 1.0D);
			state.onTotem();
			state.lastInteractionTick = currentTick;
		}
	}

	/**
	 * Learns the factor of the damage of an opponent from a moment where the
	 * real health is known and remembers it for the next fight.
	 */
	private static void calibrate(TargetState state, double realHealth) {
		if (!PhiConfig.get().autoCalibrate || !state.calibrate(realHealth)) {
			return;
		}

		DAMAGE_SCALES.put(state.name, state.damageScale);
	}

	/**
	 * Measures the blast protection of an opponent from a moment where the real
	 * health is known.
	 *
	 * <p>The blast protection is worth two protection points per level against
	 * an explosion, so the difference between the damage the model predicts and
	 * the damage the opponent really took tells the level of the enchantment.
	 *
	 * <p>The damage of the other sources is assumed to be modelled exactly, so
	 * what is left of the real loss belongs to the explosions. The absorption is
	 * a problem, because it soaks damage without touching the health, so a
	 * fight with a golden apple is not used.
	 */
	private static void measureBlastProtection(TargetState state, double realHealth) {
		if (state.blastSignal.level(EquipmentSlot.LEGS) > 0) {
			// The server named the level in the modifiers of its attributes, a
			// measurement would only add noise.
			return;
		}

		if (state.explosionArmourDamage < 1.0D || state.absorbedApplied > 0.5D) {
			// The opponent did not take enough explosion damage to learn
			// anything from it.
			return;
		}

		if (state.explosionModelledDamage < state.rawDamageApplied * 0.25D) {
			// The explosions are only a small part of the damage, so the rest of
			// the fight would drown the difference.
			return;
		}

		double realLoss = state.maxHealth + state.healingApplied - realHealth;

		if (realLoss <= 0.0D) {
			return;
		}

		double other = state.rawDamageApplied - state.explosionModelledDamage;
		double explosionLoss = realLoss - other;

		if (explosionLoss <= 0.0D) {
			// The explosions took more health than the model says, so the
			// difference says nothing about the enchantments.
			return;
		}

		// The protection points the explosions really had.
		double points = 25.0D * (1.0D - explosionLoss / state.explosionArmourDamage);
		// The points the armour has against every damage as long as no piece
		// carries the blast protection, the reference of the measurement.
		double difference = points - state.protectionBaseline;

		if (difference < -1.5D || difference > DamageModel.MAX_PROTECTION_POINTS) {
			// The difference is too small to be more than the noise of the model.
			return;
		}

		if (difference <= 0.0D) {
			// The explosions were not protected any better than the armour of the
			// opponent already protects against a hit, so the leggings carry no
			// blast protection.
			state.blastProtectionLevel = 0;
			state.blastProtectionMeasured = true;
			state.blastProtectionCapped = false;

			LOGGER.info("measured no blast protection on {}", state.name);
			return;
		}

		// The leggings carry the blast protection, so they have no protection
		// enchantment on them. The points they would have had without it are the
		// reference of the measurement.
		double blastPoints = difference + state.leggingsBaseline;
		int level = (int) Math.round(blastPoints / DamageModel.BLAST_PROTECTION_PER_LEVEL);

		state.blastProtectionLevel = Math.max(0, Math.min(4, level));
		state.blastProtectionMeasured = true;
		state.blastProtectionCapped = points >= DamageModel.MAX_PROTECTION_POINTS - 0.5D;

		LOGGER.info("measured the blast protection of {} as {}", state.name, state.blastProtectionLevel);
	}

	public static void tick() {
		currentTick++;

		Minecraft client = Minecraft.getInstance();

		if (client.level == null || client.player == null) {
			TARGETS.clear();
			PENDING.clear();
			POTIONS.clear();
			ARROWS.clear();
			SPAWNED_AT.clear();
			SLOT_DROPS.clear();
			DEATHS.clear();
			DEATH_POS.clear();
			CLAIMED.clear();
			return;
		}

		ClientLevel world = client.level;
		Iterator<Map.Entry<Integer, CombatSnapshot>> pending = PENDING.entrySet().iterator();

		while (pending.hasNext()) {
			Map.Entry<Integer, CombatSnapshot> entry = pending.next();

			if (currentTick - entry.getValue().tick() > PENDING_TIMEOUT) {
				pending.remove();
			}
		}

		Set<Integer> present = new HashSet<>();

		for (Entity entity : world.entitiesForRendering()) {
			// The list of the players of the client world is not filled by
			// vanilla, so every entity of the world has to be checked.
			if (entity instanceof Arrow arrow) {
				trackArrow(arrow);
				continue;
			}

			if (entity instanceof ItemEntity item) {
				trackDroppedArmour(item);
				continue;
			}

			if (!(entity instanceof Player player)) {
				continue;
			}

			present.add(player.getId());
			TargetState state = TARGETS.get(player.getId());

			if (state == null || (!state.alive && player.isAlive())) {
				// A new player or a respawn: the old estimate is worthless.
				state = newState(player);
			}

			String name = player.getName().getString();

			if (!name.equals(state.name)) {
				state.name = name;
				state.damageScale = DAMAGE_SCALES.getOrDefault(name, 1.0D);
			}

			state.currentTick = currentTick;

			if (state.alive && !player.isAlive()) {
				state.onDeath();
			}

			refreshAttributes(state, player);
			state.tickEffects();
			state.tickFood();
			state.tickAnimation();
			trackExhaustion(state, player);
			detectConsumption(state, player);
			updateWeapon(state, player);
			trackFall(state, player);
			state.ensurePositive();

			if (state.pendingExplosionTick != Long.MIN_VALUE
				&& currentTick - state.pendingExplosionTick > EXPLOSION_TIMEOUT) {
				// The explosion packet never arrived, so the distance and the
				// power of the explosion stay unknown.
				applyUnresolvedExplosion(state);
				state.pendingExplosionTick = Long.MIN_VALUE;
			}

			trackExplosionKnockback(state, player);
		}

		Iterator<Map.Entry<Integer, TargetState>> targets = TARGETS.entrySet().iterator();

		while (targets.hasNext()) {
			Map.Entry<Integer, TargetState> entry = targets.next();

			if (!present.contains(entry.getKey())) {
				targets.remove();
				EQUIPMENT.remove(entry.getKey());
				SLOT_DROPS.remove(entry.getKey());
			}
		}

		trackPotions(world);
		expireArrows();
		refreshLearned(world);
	}

	/**
	 * The knockback of an explosion is the only sign of it when the explosion
	 * itself is too far away to reach us. Vanilla throws the opponent away from
	 * the centre of the explosion, the strength of the throw tells how much of
	 * the explosion hit them.
	 */
	private static void trackExplosionKnockback(TargetState state, Player player) {
		Vec3 velocity = player.getDeltaMovement();
		double dx = velocity.x - state.previousVelocity.x;
		double dz = velocity.z - state.previousVelocity.z;
		state.previousVelocity = velocity;

		if (state.pendingExplosionTick == Long.MIN_VALUE
			|| currentTick - state.pendingExplosionTick > 2) {
			return;
		}

		double knockback = Math.sqrt(dx * dx + dz * dz);

		if (knockback > state.explosionKnockback) {
			state.explosionKnockback = (float) knockback;
		}
	}

	/**
	 * An explosion whose packet never arrived. The server only sends it to the
	 * players around the explosion, so an opponent that is hit by a blast far
	 * away from us leaves us with the knockback only.
	 */
	private static void applyUnresolvedExplosion(TargetState state) {
		float knockback = state.explosionKnockback;

		if (knockback < MIN_EXPLOSION_KNOCKBACK) {
			// Without a knockback there is nothing to go by, so only the
			// certainty of the estimate drops.
			state.confidence = Math.max(0.0D, state.confidence - 0.1D);
			return;
		}

		// The knockback of the explosion is the same factor the damage uses, so
		// it tells how far away the explosion was.
		double distance = (1.0D - knockback) * DamageModel.DEFAULT_EXPLOSION_POWER * 2.0D;

		applyExplosionDamage(state, distance, DamageModel.DEFAULT_EXPLOSION_POWER, 1.0F);
		state.confidence = Math.max(0.0D, state.confidence - 0.15D);
	}

	/**
	 * Remembers the arrows of the world. The speed of an arrow tells how much
	 * damage it does when it hits, and the client knows the speed because the
	 * server sends it with every position update.
	 */
	private static void trackArrow(Arrow arrow) {
		ArrowRecord record = ARROWS.computeIfAbsent(arrow.getId(), id -> new ArrowRecord());
		Vec3 velocity = arrow.getDeltaMovement();

		if (velocity.lengthSqr() > 0.0D) {
			record.speed = velocity.length();
		} else {
			double dx = arrow.getX() - record.previousX;
			double dy = arrow.getY() - record.previousY;
			double dz = arrow.getZ() - record.previousZ;
			double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);

			if (moved > 0.0D) {
				record.speed = moved;
			}
		}

		record.previousX = arrow.getX();
		record.previousY = arrow.getY();
		record.previousZ = arrow.getZ();
		record.critical = arrow.isCritArrow();
		record.lastSeen = currentTick;
	}

	/** Forgets the arrows that left the world a long time ago. */
	private static void expireArrows() {
		Iterator<Map.Entry<Integer, ArrowRecord>> iterator = ARROWS.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<Integer, ArrowRecord> entry = iterator.next();

			if (currentTick - entry.getValue().lastSeen > ARROW_MEMORY) {
				iterator.remove();
			}
		}
	}

	/**
	 * A piece of armour on the ground keeps the real enchantments, because the
	 * servers that protect against health indicators only rewrite the equipment
	 * packets and not the items of the world.
	 *
	 * <p>The enchantments of the piece are learned, but only when the owner is
	 * beyond any doubt: the player has to have lost that slot right before the
	 * piece showed up and has to be the only one that stands next to it. The
	 * armour of a dead player falls on the ground as well, and the place where
	 * the player died tells who the pieces belong to.
	 */
	private static void trackDroppedArmour(ItemEntity item) {
		int id = item.getId();

		if (CLAIMED.contains(id)) {
			return;
		}

		Long spawned = SPAWNED_AT.get(id);

		if (spawned == null) {
			// The piece did not come with a spawn packet, so it lay on the
			// ground already and says nothing about the player next to it.
			return;
		}

		ItemStack stack = item.getItem();
		EquipmentSlot slot = ArmorProbe.slotOf(stack);

		if (slot == null) {
			// Not a piece of armour, so there is nothing to learn from it.
			CLAIMED.add(id);
			return;
		}

		if (EnchantmentReader.isRewritten(stack)) {
			// The enchantments of the piece were rewritten as well, so they are
			// not the real ones.
			CLAIMED.add(id);
			return;
		}

		if (EnchantmentReader.names(stack).isEmpty()) {
			// The piece carries no enchantments, there is nothing to learn.
			CLAIMED.add(id);
			return;
		}

		int owner = ownerOfDroppedPiece(item, spawned, slot);

		if (owner < 0) {
			// Nobody claims the piece yet, it may still be a piece of a player
			// that dies right now.
			return;
		}

		learn(owner, slot, stack, id);
		CLAIMED.add(id);
	}

	/**
	 * The player a dropped piece belongs to.
	 *
	 * <p>Only a player that lost that slot a moment ago and that stands right
	 * next to the piece can be its owner. When more than one of them qualifies,
	 * the piece stays unclaimed. The armour of a player that died a moment ago
	 * falls on the ground around the place of the death.
	 */
	private static int ownerOfDroppedPiece(ItemEntity item, long spawned, EquipmentSlot slot) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel world = client.level;

		if (world == null) {
			return -1;
		}

		int owner = -1;

		for (Map.Entry<Integer, TargetState> entry : TARGETS.entrySet()) {
			int entityId = entry.getKey();
			Map<EquipmentSlot, Long> drops = SLOT_DROPS.get(entityId);

			if (drops == null) {
				continue;
			}

			Long dropTick = drops.get(slot);

			if (dropTick == null || Math.abs(spawned - dropTick) > DROP_LINK_WINDOW) {
				continue;
			}

			Entity entity = world.getEntity(entityId);

			if (!(entity instanceof Player player)) {
				continue;
			}

			if (player.distanceToSqr(item) > OWNER_RADIUS * OWNER_RADIUS) {
				continue;
			}

			if (owner >= 0) {
				// Two players could be the owner, so nobody is.
				return -1;
			}

			owner = entityId;
		}

		if (owner >= 0) {
			return owner;
		}

		for (Map.Entry<Integer, Long> entry : DEATHS.entrySet()) {
			int entityId = entry.getKey();
			long died = entry.getValue();

			if (spawned < died - 5L || spawned > died + DEATH_DROP_WINDOW) {
				continue;
			}

			Vec3 pos = DEATH_POS.get(entityId);

			if (pos == null || item.distanceToSqr(pos) > DEATH_DROP_RADIUS * DEATH_DROP_RADIUS) {
				continue;
			}

			if (owner >= 0) {
				return -1;
			}

			owner = entityId;
		}

		return owner;
	}

	/**
	 * Remembers a piece whose real enchantments are known.
	 *
	 * <p>The profile is kept per name, so it survives the respawn of the player.
	 */
	private static void learn(int entityId, EquipmentSlot slot, ItemStack stack, int itemId) {
		TargetState state = TARGETS.get(entityId);

		if (state == null || state.name == null) {
			return;
		}

		String name = state.name;
		ArmorProfile profile = LEARNED.computeIfAbsent(name, n -> new ArmorProfile());
		profile.put(slot, stack);
		LEARNED_AT.put(name, currentTick);
		LEARNED_ITEM.computeIfAbsent(name, n -> new EnumMap<>(EquipmentSlot.class)).put(slot, itemId);

		LOGGER.info("learned the {} of {} from the dropped piece", slot, name);
	}

	/**
	 * Keeps the learned pieces believable.
	 *
	 * <p>A piece only means something while the player wears the pair it came
	 * from. When the piece is still on the ground and the player wears another
	 * one, the learned piece is out of date. When the piece is gone, they picked
	 * it up, so the enchantments stay and the time it is good for starts again.
	 */
	private static void refreshLearned(ClientLevel world) {
		Iterator<Map.Entry<String, ArmorProfile>> profiles = LEARNED.entrySet().iterator();

		while (profiles.hasNext()) {
			Map.Entry<String, ArmorProfile> entry = profiles.next();
			String name = entry.getKey();
			ArmorProfile profile = entry.getValue();
			Long learnedAt = LEARNED_AT.get(name);

			if (learnedAt == null || currentTick - learnedAt > LEARNED_TIMEOUT) {
				profiles.remove();
				LEARNED_AT.remove(name);
				LEARNED_ITEM.remove(name);
				continue;
			}

			Map<EquipmentSlot, Integer> items = LEARNED_ITEM.get(name);

			if (items == null) {
				continue;
			}

			Player player = playerByName(name);

			if (player == null) {
				continue;
			}

			for (Map.Entry<EquipmentSlot, Integer> item : new ArrayList<>(items.entrySet())) {
				int itemId = item.getValue();

				if (world.getEntity(itemId) == null) {
					// The piece was picked up, so the player wears the one the
					// enchantments came from.
					items.remove(item.getKey());
					LEARNED_AT.put(name, currentTick);
					continue;
				}

				ItemStack worn = player.getItemBySlot(item.getKey());
				ItemStack learned = profile.piece(item.getKey());

				if (!worn.isEmpty() && worn.getItem() != learned.getItem()) {
					// The player wears another piece of that slot, so the learned
					// one is out of date.
					profile.forget(item.getKey());
					items.remove(item.getKey());
				}
			}
		}

		Iterator<Map.Entry<Integer, Map<EquipmentSlot, Long>>> drops = SLOT_DROPS.entrySet().iterator();

		while (drops.hasNext()) {
			Map.Entry<Integer, Map<EquipmentSlot, Long>> entry = drops.next();
			Iterator<Map.Entry<EquipmentSlot, Long>> slots = entry.getValue().entrySet().iterator();

			while (slots.hasNext()) {
				Map.Entry<EquipmentSlot, Long> slot = slots.next();

				if (currentTick - slot.getValue() > DROP_WINDOW) {
					slots.remove();
				}
			}

			if (entry.getValue().isEmpty()) {
				drops.remove();
			}
		}

		Iterator<Map.Entry<Integer, Long>> spawned = SPAWNED_AT.entrySet().iterator();

		while (spawned.hasNext()) {
			Map.Entry<Integer, Long> entry = spawned.next();

			if (currentTick - entry.getValue() > DROP_WINDOW) {
				spawned.remove();
				CLAIMED.remove(entry.getKey());
			}
		}

		Iterator<Map.Entry<Integer, Long>> deaths = DEATHS.entrySet().iterator();

		while (deaths.hasNext()) {
			Map.Entry<Integer, Long> entry = deaths.next();

			if (currentTick - entry.getValue() > DEATH_DROP_WINDOW) {
				deaths.remove();
				DEATH_POS.remove(entry.getKey());
			}
		}
	}

	/** The player with that name, null when they are not in the world. */
	private static Player playerByName(String name) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel world = client.level;

		if (world == null) {
			return null;
		}

		for (TargetState state : TARGETS.values()) {
			if (!state.name.equals(name)) {
				continue;
			}

			Entity entity = world.getEntity(state.entityId);

			if (entity instanceof Player player) {
				return player;
			}
		}

		return null;
	}

	private static TargetState newState(Player player) {
		TargetState state = new TargetState(player.getId());
		state.name = player.getName().getString();
		refreshAttributes(state, player);
		state.health = state.maxHealth;
		state.damageScale = DAMAGE_SCALES.getOrDefault(state.name, 1.0D);
		TARGETS.put(state.entityId, state);
		return state;
	}

	private static TargetState state(int entityId) {
		TargetState state = TARGETS.get(entityId);

		if (state != null) {
			return state;
		}

		state = new TargetState(entityId);
		Minecraft client = Minecraft.getInstance();

		if (client.level != null && client.level.getEntity(entityId) instanceof Player player) {
			state.name = player.getName().getString();
			refreshAttributes(state, player);
			state.health = state.maxHealth;
			state.damageScale = DAMAGE_SCALES.getOrDefault(state.name, 1.0D);
		}

		TARGETS.put(entityId, state);
		return state;
	}

	private static void refreshAttributes(TargetState state, Player player) {
		ArmorProbe armor = armor(player.getId());
		state.maxHealth = player.getAttributeValue(Attributes.MAX_HEALTH);
		// The armour attribute of another player is not sent by every server, so
		// the worn armour is used as a second source.
		state.armorAttribute = player.getAttributeValue(Attributes.ARMOR);
		state.armorEquipment = DamageModel.equipmentArmor(player);
		state.armor = Math.floor(Math.max(state.armorAttribute, state.armorEquipment));
		state.toughness = Math.max(
			player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
			DamageModel.equipmentToughness(player)
		);
		state.blastSignal = BlastSignal.of(player);
		ArmorProtection protection = ArmorProtection.of(armor, profile(state.name), state.blastSignal,
			state.blastProtectionMeasured ? Integer.valueOf(state.blastProtectionLevel) : null);
		state.protection = protection.general();
		state.blastProtection = protection.blast();
		state.explosionProtection = protection.explosion();
		state.protectionBaseline = protection.baseline();
		state.leggingsBaseline = protection.leggingsBaseline();
		// Boots with the feather falling enchantment raise the safe fall
		// distance, both attributes are sent to the client.
		state.safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		state.fallDamageMultiplier = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
		state.fallProtection = DamageModel.fallProtectionPoints(armor);

		// The maximum health can shrink when the player takes off a piece of
		// armour with extra health on it.
		if (state.health > state.maxHealth) {
			state.health = state.maxHealth;
		}
	}

	/**
	 * Exhaustion of the opponent. Sprinting and jumping burn it in vanilla, and
	 * every four points of exhaustion eat one point of saturation, which slows
	 * the natural regeneration down.
	 */
	private static void trackExhaustion(TargetState state, Player player) {
		if (player.isSprinting()) {
			state.addExhaustion(0.1D);
		}

		if (!player.onGround()) {
			state.addExhaustion(0.05D);
		}
	}

	/** Remembers the weapon of a player so their hits can be estimated. */
	private static void updateWeapon(TargetState state, Player player) {
		ItemStack weapon = player.getMainHandItem();
		// Strength is a server side effect modifier, so it is not part of the
		// attack damage attribute and has to be added by hand.
		state.weaponDamage = DamageModel.weaponAttackDamage(weapon) + state.strengthBonus() - state.weaknessPenalty();
		state.weaponEnchantmentDamage = DamageModel.enchantmentDamage(weapon);
	}

	/**
	 * Follows the height of a player to recognise a fall. The client only knows
	 * the synced positions, so the fall distance is an approximation.
	 */
	private static void trackFall(TargetState state, Player player) {
		double y = player.getY();

		if (Double.isNaN(state.previousY)) {
			state.previousY = y;
			state.fallStartY = y;
			return;
		}

		if (y < state.previousY - 0.05D) {
			if (state.fallStartY < state.previousY) {
				state.fallStartY = state.previousY;
			}
		} else if (y > state.previousY + 0.05D) {
			state.fallStartY = y;
			state.lastFallDistance = 0.0D;
		} else {
			double fall = state.fallStartY - y;

			if (fall > 0.5D && fall > state.lastFallDistance) {
				state.lastFallDistance = fall;
			}

			state.fallStartY = y;
		}

		state.previousY = y;
	}

	/** Fall the player is currently in, or the last one they survived. */
	private static double observedFall(TargetState state) {
		double pending = state.fallStartY - state.previousY;

		if (pending > state.lastFallDistance) {
			return pending;
		}

		return state.lastFallDistance;
	}

	private static CombatSnapshot fallbackSnapshot(int targetId) {
		LocalPlayer me = Minecraft.getInstance().player;

		if (me == null) {
			return new CombatSnapshot(targetId, DamageModel.PLAYER_BASE_ATTACK_DAMAGE, lastCooldown, 0.0F, false, false, currentTick);
		}

		ItemStack weapon = ownWeapon(me);
		float attackDamage = DamageModel.attackDamage(me, weapon);
		float enchantment = DamageModel.enchantmentDamage(weapon);

		// The cooldown of the last swing is the closest guess, a full charge
		// would predict far more damage than the hit really did.
		return new CombatSnapshot(targetId, attackDamage, lastCooldown, enchantment, false, false, currentTick);
	}

	/**
	 * Watches the "using item" flag of the opponent. It tells us that they are
	 * eating or drinking, and the item in the hand tells us what it was.
	 *
	 * <p>The flag stays up for the whole time the item is used, so the
	 * consumption is applied once, when the flag goes down again. Applying it
	 * while the flag is up would count the same golden apple again and again.
	 */
	private static void detectConsumption(TargetState state, Player player) {
		if (player.isUsingItem()) {
			if (state.pendingConsume == null) {
				ItemStack active = player.getActiveItem();

				if (active.isEmpty()) {
					// The client does not know the active item of another player,
					// but the item they use is the one in the hand they hold it in.
					active = player.getMainHandItem();
				}

				if (active.isEmpty() || !HealingModel.isConsumable(active)) {
					// Either the equipment packet did not arrive yet or the item
					// is not something that heals, a drawn bow for example.
					return;
				}

				state.pendingConsume = new PendingConsume(active.copy(), active.getUseDuration(player));
			}

			state.pendingConsume.observedTicks++;
			return;
		}

		if (state.pendingConsume != null) {
			if (state.pendingConsume.observedTicks >= MIN_USE_TICKS) {
				applyPendingConsumption(state);
			} else {
				// The use was cancelled before it was finished, so nothing happened.
				state.pendingConsume = null;
			}
		}
	}

	private static void applyPendingConsumption(TargetState state) {
		PendingConsume pending = state.pendingConsume;

		if (pending == null || pending.applied) {
			state.pendingConsume = null;
			return;
		}

		pending.applied = true;
		state.pendingConsume = null;
		HealingModel.applyConsumable(state, pending.stack);
		state.confidence = Math.max(0.0D, state.confidence - 0.05D);
		state.lastInteractionTick = currentTick;
	}

	/**
	 * Remembers every thrown potion around our opponents. When the potion
	 * entity disappears it has just burst, so its effects are applied to
	 * everybody that stood close enough to the impact.
	 */
	private static void trackPotions(ClientLevel world) {
		AABB area = null;

		for (TargetState state : TARGETS.values()) {
			Entity entity = world.getEntity(state.entityId);

			if (!(entity instanceof Player player)) {
				continue;
			}

			AABB box = player.getBoundingBox().inflate(SPLASH_RADIUS + 2.0D);
			area = area == null ? box : area.minmax(box);
		}

		if (area == null) {
			return;
		}

		Set<Integer> alive = new HashSet<>();

		for (ThrownSplashPotion potion : world.getEntities(
			EntityTypeTest.forClass(ThrownSplashPotion.class), area, entity -> true)) {
			alive.add(potion.getId());
			PotionRecord record = POTIONS.get(potion.getId());

			if (record == null) {
				record = new PotionRecord();
				record.id = potion.getId();
				POTIONS.put(potion.getId(), record);
			}

			record.x = potion.getX();
			record.y = potion.getY();
			record.z = potion.getZ();
			record.stack = potion.getItem().copy();
			record.present = true;
			record.lastSeen = currentTick;
		}

		Iterator<Map.Entry<Integer, PotionRecord>> iterator = POTIONS.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<Integer, PotionRecord> entry = iterator.next();
			PotionRecord record = entry.getValue();

			if (record.present && !alive.contains(record.id)) {
				record.present = false;
				applySplashPotion(record, world);
			}

			if (currentTick - record.lastSeen > 100L) {
				iterator.remove();
			}
		}
	}

	private static void applySplashPotion(PotionRecord record, ClientLevel world) {
		PotionContents contents = record.stack.get(DataComponents.POTION_CONTENTS);

		if (contents == null) {
			return;
		}

		Vec3 center = new Vec3(record.x, record.y, record.z);

		for (TargetState state : TARGETS.values()) {
			Entity entity = world.getEntity(state.entityId);

			if (!(entity instanceof Player player)) {
				continue;
			}

			double distance = player.position().distanceTo(center);

			if (distance > SPLASH_RADIUS) {
				continue;
			}

			float potency = (float) (1.0D - distance / SPLASH_RADIUS);

			for (MobEffectInstance instance : contents.getAllEffects()) {
				HealingModel.applyEffect(state, instance, potency);
			}

			state.confidence = Math.max(0.0D, state.confidence - 0.03D);
			state.lastInteractionTick = currentTick;
		}
	}

	private static final class PotionRecord {
		private int id;
		private double x;
		private double y;
		private double z;
		private ItemStack stack = ItemStack.EMPTY;
		private boolean present;
		private long lastSeen;
	}

	/** What the mod remembers about one arrow of the world. */
	private static final class ArrowRecord {
		private double previousX;
		private double previousY;
		private double previousZ;
		private double speed;
		private boolean critical;
		private long lastSeen;
	}
}
