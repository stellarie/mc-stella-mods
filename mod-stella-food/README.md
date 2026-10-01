# Stella Food

Stella Food adds a cooking station, combined dishes, food buffs, and potion-infused dishes.

## Eating

All edible items can be eaten at full hunger. Every food grants Regeneration I for two seconds.
Hover over a food to see its food buff, duration, and benefit. Infused dishes also list their potion effects.

Ordinary food grants a category buff for five minutes. Unknown foods, including food from other mods, receive Nourished.

| Food | Buff |
| --- | --- |
| Meat and poultry | Steady Hand |
| Fish | River Sense |
| Fruit | Quick Step |
| Vegetables | Sturdy |
| Bread and baked food | Endurance |
| Sweets | Sugar Rush |
| Suspicious food | Wild Taste |
| Other food | Nourished |

## Combined dishes

Each dish grants a distinct buff for thirty minutes.

| Dish | Buff | Ingredients |
| --- | --- | --- |
| Hunter's Stew | Surefooted | Cooked meat, mushroom, carrot |
| Miner's Lunch | Deep Breath | Baked potato, cooked meat, beetroot |
| Orchard Tart | Light Step | Apple, wheat, honey |
| Fisher's Feast | Current Rider | Cooked fish, kelp, potato |
| Nether Skewer | Ember Skin | Cooked pork, warped fungus, blaze powder |
| Golden Supper | Second Wind | Golden carrot, cooked meat, honey |

The cooking station combines ingredients and cooks food. Craft it with five coal, one flint and steel, and one crafting table.

- Right-click the station to open its 3x3 crafting screen.
- Use the left recipe list to see six dishes, Cook Food, and Infuse Dish. Select a dish to fill available ingredients from inventory.
- Put one food in the grid and take the output to cook it. A food without a vanilla cooked form gains up to two nutrition points.
- Put a dish and one potion in the grid. Take the output to infuse it.

You can also combine each dish's ingredients in a crafting table. The station returns empty bottles from honey and potion ingredients.

Combine a dish with one regular, splash, or lingering potion to infuse it. Its dish buff and timed potion effects last forty-five minutes. Instant effects apply when eaten. Harmful effects remain harmful.

## Damaging potions in food

Food never poisons, withers, starves or harms the eater. A damaging effect in an infused dish becomes a mild debuff for
5 minutes instead:

| Potion effect | Mild debuff |
|---|---|
| Poison, Hunger | Queasy: -10% movement speed |
| Wither | Sluggish: -10% attack speed |
| Harming | Heavy: -15% mining speed |

Other effects, including Slowness, Weakness and Mining Fatigue, apply as before. The dish tooltip shows the mild debuff.

## Container loot

Every searchable container, the same chests, barrels and pots the equips gear rolls reach, has two dish rolls:

- 10%: one random combined dish.
- 5%: one random dish tipped with a random potion, with a seasoning bonus, the same as one from the cooking station.

## Seasoned bonus

The station rolls one small stat bonus when it infuses a dish: a random stat (STR, AGI, VIT, DEX, INT, or LUK) and an amount from 5 to 10. The roll is stored on the dish in the `mod-stella-food:seasoning` data component, so the dish keeps it. The tooltip shows it, for example "Seasoned: +7 DEX".

- The roll happens once for each dish and potion pair in the grid. Changing the grid back to the same pair does not reroll it.
- Eating the dish applies the effect `mod-stella-food:seasoned_<stat>` for forty-five minutes, at level equal to the amount. The effects are `seasoned_str`, `seasoned_agi`, `seasoned_vit`, `seasoned_dex`, `seasoned_int`, and `seasoned_luk`. They have no attribute modifiers.
- `mod-stella-rpg` reads these effects by id and adds one stat point for each level. Without it the effects do nothing but show an icon.
- Plain dishes, ordinary food, and dishes infused before this bonus existed carry no bonus.

## Potion stacks

- Potions, splash potions, and lingering potions stack to 16.
- Only potions with the same contents stack. A Healing potion never merges with a Swiftness potion.
- Drinking one potion from a stack leaves the rest and gives one glass bottle.
- The cooking station uses one potion from a stack and returns one glass bottle.
- Glass bottles keep the vanilla stack size of 64.

## Build and verification

Run these commands from this directory:

```powershell
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Run the full GameTest suite five separate times. Save each run log before another run or clean build.
