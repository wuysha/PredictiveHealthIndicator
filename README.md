# Predictive Health Indicator

Client-side Fabric mod that shows the **remaining health of an opponent above their head**, predicted from the damage you personally deal to them.

No scoreboard, no TAB, no side channels — only the vanilla packets every server already sends.

- No keybinds, no damage numbers floating over the entity.
- Settings live in **Mod Menu** (YACL3 screen).
- Works with servers that hide the real health from clients: the mod never reads the opponent's synced health, absorption or enchantments. It derives the damage from observable events (damage packets, explosion packets, synced equipment, entity status, knockback) and corrects itself from the moments where the real health is known anyway — the opponent's death and the totem that saves them.

## Download

Grab the jars from the [latest release](https://github.com/<owner>/PredictiveHealthIndicator/releases/latest).

| Jar | Minecraft | Java | Fabric API |
|---|---|---|---|
| `predictive-health-indicator-1.0.0-mc26.3.jar` | 26.3 | 25 | 0.165.2+26.3 |
| `predictive-health-indicator-1.0.0-mc1.21.11.jar` | 1.21.11 | 21 | 0.141.6+1.21.11 |

Both versions also need [YetAnotherConfigLib](https://modrinth.com/mod/yacl) (v3) and, optionally, [Mod Menu](https://modrinth.com/mod/modmenu).

The two jars are **not** interchangeable — pick the one that matches your Minecraft version.

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/`.

## Settings

| Option | Default | Meaning |
|---|---|---|
| Show the indicator | on | Shows the estimated health above the head of every opponent. |
| See through walls | off | The indicator stays visible through walls. |
| Estimate the damage of other players | on | Damage from another player is estimated, so the indicator does not lose track of the health. |
| Calibrate the damage on death | on | The death of an opponent and the totem that saves them show the real health, the damage is corrected with it. |
| Show the numbers of the prediction | off | Debug overlay with the inputs of the damage pipeline. |
| Distance | 48 | Opponents that are further away are not shown. |
| Text size | 1.0 | One is the size of a vanilla name tag. |
| Horizontal / Vertical offset | 0.0 | Shifts the indicator, in blocks. |

## License

MIT
