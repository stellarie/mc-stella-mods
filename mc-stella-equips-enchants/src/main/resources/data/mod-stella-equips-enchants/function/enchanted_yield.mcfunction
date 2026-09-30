# cooldown_ticks: 20
# max_effects: 1
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_yield
scoreboard players set #yield_level ench_scratch 0
execute store result score #slot ench_scratch run data get entity @s SelectedItemSlot
execute if score #slot ench_scratch matches 0 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:0b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #slot ench_scratch matches 1 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:1b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #slot ench_scratch matches 2 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:2b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #slot ench_scratch matches 3 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:3b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #slot ench_scratch matches 4 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:4b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #slot ench_scratch matches 5 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:5b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #slot ench_scratch matches 6 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:6b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #slot ench_scratch matches 7 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:7b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #slot ench_scratch matches 8 store result score #yield_level ench_scratch run data get entity @s Inventory[{Slot:8b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/enchanted_yield"
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:enchanted_yield_chance if score #yield_level ench_scratch matches 1 run experience add @s 1 points
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:enchanted_yield_chance if score #yield_level ench_scratch matches 2 run experience add @s 2 points
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:enchanted_yield_chance if score #yield_level ench_scratch matches 3 run experience add @s 3 points
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:enchanted_yield_chance if score #yield_level ench_scratch matches 4 run experience add @s 4 points
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:enchanted_yield_chance if score #yield_level ench_scratch matches 5.. run experience add @s 5 points
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_yield = #clock ench_clock
