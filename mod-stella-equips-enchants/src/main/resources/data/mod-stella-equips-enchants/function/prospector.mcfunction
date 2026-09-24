# cooldown_ticks: 40
# max_particles: 24
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_prospect
scoreboard players set #prospect_level ench_scratch 0
execute store result score #slot ench_scratch run data get entity @s SelectedItemSlot
execute if score #slot ench_scratch matches 0 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:0b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #slot ench_scratch matches 1 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:1b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #slot ench_scratch matches 2 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:2b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #slot ench_scratch matches 3 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:3b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #slot ench_scratch matches 4 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:4b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #slot ench_scratch matches 5 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:5b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #slot ench_scratch matches 6 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:6b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #slot ench_scratch matches 7 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:7b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #slot ench_scratch matches 8 store result score #prospect_level ench_scratch run data get entity @s Inventory[{Slot:8b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/prospector"
execute if score #now ench_scratch matches 40.. if score #prospect_level ench_scratch matches 1 run particle minecraft:end_rod ~0.4 ~0.5 ~0.4 0.4 0.4 0.4 0.02 8 force
execute if score #now ench_scratch matches 40.. if score #prospect_level ench_scratch matches 2 run particle minecraft:end_rod ~0.7 ~0.5 ~0.7 0.7 0.4 0.7 0.02 12 force
execute if score #now ench_scratch matches 40.. if score #prospect_level ench_scratch matches 3 run particle minecraft:end_rod ~1 ~0.5 ~1 1 0.4 1 0.02 16 force
execute if score #now ench_scratch matches 40.. if score #prospect_level ench_scratch matches 4 run particle minecraft:end_rod ~1.3 ~0.5 ~1.3 1.3 0.4 1.3 0.02 20 force
execute if score #now ench_scratch matches 40.. if score #prospect_level ench_scratch matches 5.. run particle minecraft:end_rod ~1.6 ~0.5 ~1.6 1.6 0.4 1.6 0.02 24 force
execute if score #now ench_scratch matches 40.. run playsound minecraft:block.amethyst_block.chime master @s ~ ~ ~ 0.8 1.4
execute if score #now ench_scratch matches 40.. run scoreboard players operation @s ench_cd_prospect = #clock ench_clock
