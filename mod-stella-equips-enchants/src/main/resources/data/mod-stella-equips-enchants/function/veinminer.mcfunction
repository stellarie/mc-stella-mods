# cooldown_ticks: 20
# max_blocks: 3
# The plane holds the mined block and its two neighbours on one axis. The axis is
# X when the miner looks along Z, and Z when the miner looks along X. Looking
# straight up or down keeps the horizontal X plane.
scoreboard players set #armed ench_scratch 0
scoreboard players set #plane ench_scratch 1
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_vein
scoreboard players set #vein_level ench_scratch 0
execute store result score #slot ench_scratch run data get entity @s SelectedItemSlot
execute if score #slot ench_scratch matches 0 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:0b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
execute if score #slot ench_scratch matches 1 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:1b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
execute if score #slot ench_scratch matches 2 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:2b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
execute if score #slot ench_scratch matches 3 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:3b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
execute if score #slot ench_scratch matches 4 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:4b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
execute if score #slot ench_scratch matches 5 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:5b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
execute if score #slot ench_scratch matches 6 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:6b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
execute if score #slot ench_scratch matches 7 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:7b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
execute if score #slot ench_scratch matches 8 store result score #vein_level ench_scratch run data get entity @s Inventory[{Slot:8b}].components."minecraft:enchantments"."mod-stella-equips-enchants:tool/veinminer"
# The guard is armed before the first block breaks. A pulse fired by this swing
# must find the cooldown already set, or the swing would break six blocks.
execute if score #now ench_scratch matches 20.. if score #vein_level ench_scratch matches 1.. run scoreboard players operation @s ench_cd_vein = #clock ench_clock
execute if score #now ench_scratch matches 20.. if score #vein_level ench_scratch matches 1.. run scoreboard players set #armed ench_scratch 1
execute if score #armed ench_scratch matches 1 store result score #yaw ench_scratch run data get entity @s Rotation[0]
execute if score #armed ench_scratch matches 1 store result score #pitch ench_scratch run data get entity @s Rotation[1]
# Yaw 0 looks south, 90 west, 180 and -180 north, 270 and -90 east. The game
# reports 181 to 359 as -179 to -1, so the negative ranges matter.
# Looking south or north spreads the plane on X, which is the default.
execute if score #armed ench_scratch matches 1 if score #pitch ench_scratch matches -44..44 if score #yaw ench_scratch matches 45..134 run scoreboard players set #plane ench_scratch 2
execute if score #armed ench_scratch matches 1 if score #pitch ench_scratch matches -44..44 if score #yaw ench_scratch matches -134..-45 run scoreboard players set #plane ench_scratch 2
execute if score #armed ench_scratch matches 1 if score #plane ench_scratch matches 1 if block ~-1 ~ ~ #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~ ~ air destroy
execute if score #armed ench_scratch matches 1 if score #plane ench_scratch matches 1 if block ~0 ~ ~ #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~ ~ air destroy
execute if score #armed ench_scratch matches 1 if score #plane ench_scratch matches 1 if block ~1 ~ ~ #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~ ~ air destroy
execute if score #armed ench_scratch matches 1 if score #plane ench_scratch matches 2 if block ~ ~ ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~ ~ ~-1 air destroy
execute if score #armed ench_scratch matches 1 if score #plane ench_scratch matches 2 if block ~ ~ ~ #mod-stella-equips-enchants:vein_blocks run setblock ~ ~ ~ air destroy
execute if score #armed ench_scratch matches 1 if score #plane ench_scratch matches 2 if block ~ ~ ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~ ~ ~1 air destroy
