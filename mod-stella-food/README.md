# Stella Food

Stella Food adds a cooking station, combined dishes, food buffs, and potion-infused dishes.

## Eating

All edible items can be eaten at full hunger. Every food grants Regeneration I for two seconds.

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

- Hold a food item and right-click the station to cook it. A food without a vanilla cooked form gains up to two nutrition points.
- Crouch, hold one dish ingredient, and right-click the station. The station takes the other ingredients from your inventory.
- Hold a dish in your main hand and a potion in your offhand. Right-click the station to infuse the dish.

You can also combine each dish's ingredients in a crafting table. The station returns empty bottles from honey and potion ingredients.

Combine a dish with one regular, splash, or lingering potion to infuse it. Its dish buff and timed potion effects last forty-five minutes. Instant effects apply when eaten. Harmful effects remain harmful.

## Build and verification

Run these commands from this directory:

```powershell
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Run the full GameTest suite five separate times. Save each run log before another run or clean build.
