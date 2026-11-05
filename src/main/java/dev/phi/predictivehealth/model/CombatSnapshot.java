package dev.phi.predictivehealth.model;

/**
 * State of a single melee swing, captured on the client at the exact moment the
 * attack packet is sent. Everything the damage formula needs is frozen here so
 * the estimate does not drift while the server processes the hit.
 */
public record CombatSnapshot(
		int targetId,
		float attackDamage,
		float cooldownProgress,
		float enchantmentDamage,
		boolean critical,
		boolean targetBlocking,
		long tick
) {
}
