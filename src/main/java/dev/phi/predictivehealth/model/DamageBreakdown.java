package dev.phi.predictivehealth.model;

/**
 * Result of running a raw melee damage value through the vanilla damage pipeline.
 */
public record DamageBreakdown(
		float raw,
		float afterArmor,
		float afterResistance,
		float afterProtection,
		float absorbed,
		float toHealth
) {
}
