# PRD: Dual Starters (Cobblemon add-on)

Designer: Finn (does not code; a developer builds from this document)

## One-line summary
New players choose **two** starter Pokémon at once, on one screen, instead of one.

## Base mod and platform
- **Base mod:** Cobblemon (adds Pokémon to Minecraft), version **1.8.0**
- **Minecraft:** Java Edition **1.21.1**
- **Mod loader:** **NeoForge**
- **Environments:** Must work in both **multiplayer and single player**.

## Core idea / player fantasy
In normal Cobblemon you start your journey with one partner. With this add-on you start with a **duo**: two partners from day one. Players can mix and match however they like, including picking the same Pokémon twice.

## Features

### 1. Two-starter selection screen
- **What it does:** Replaces the normal one-starter pick with a screen where the player chooses **two** starters at once.
- **How it's opened:** When the player presses the starter keybind for the first time, same trigger as the normal Cobblemon starter menu. Cobblemon's menu appears while the player is already in the world, and this one behaves the same way.
- **Layout:** Similar to the normal Cobblemon starter screen. Two slots side by side, with selection options on each side of them.
- **Choices:** Both picks come from the usual Cobblemon starter list. The player may pick the **same Pokémon twice**.
- **Confirm step:** After choosing both, the player sees a **"Confirm your team"** step and can change their mind before it is final.
- **Closing without confirming:** If the player closes the screen before confirming, nothing is given. They can simply press the keybind again to reopen it.
- **Result:** On confirming, the player receives both Pokémon as regular starters.

### 2. The starters themselves
- Both starters are **regular Pokémon**, with no special stats, bond, or bonus.
- If the player picks the **same species twice**, the two are **separate Pokémon**, each with its own different stats and nature (rolled independently, as normal).
- They are obtained **only** through this screen.

### 3. Who it applies to
- **New players only.** A "new player" is a player who has **never chosen a starter**. Players who already have one are left alone.
- **Always 2 starters.** No on/off setting or config to switch back to 1.

## Progression and balance
- Fits at the very start of the game, as the first thing a new player does.
- No special rarity, recipes, or stat changes. The only balance impact is that new players begin with two Pokémon instead of one.

## Art and theme
- Match the look of the normal Cobblemon starter screen.
- Two side-by-side starter slots with options on each side.
- No custom names, colors, or sounds were requested.

## Version 1 scope
**Must have:** Two starters through the one-screen picker with the "Confirm your team" step. That is all.

**Later ideas:**
- Battle effects for certain duos (for example, special bonuses when specific pairs are used together)

## Decisions confirmed by Finn
- Mod loader is NeoForge.
- Closing the screen without confirming is fine; the player presses the keybind again.
- "New player" means has never chosen a starter.
- Duplicate picks are separate Pokémon with different stats and nature.
- Works on both multiplayer and single player.

## Open questions for the developer
1. **Cobblemon version check:** Confirm Cobblemon 1.8.0 on Minecraft 1.21.1 works with NeoForge as expected.
2. **Duo battle effects (later):** These need a full design pass before building. Which duos, and what effects? Finn has not decided yet.
