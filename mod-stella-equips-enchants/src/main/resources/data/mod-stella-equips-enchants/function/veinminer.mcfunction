# cooldown_ticks: 20
# max_blocks: 25
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
scoreboard players set #vein_cap ench_scratch 0
scoreboard players operation #vein_cap ench_scratch += #five ench_scratch
scoreboard players operation #vein_cap ench_scratch *= #vein_level ench_scratch
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 1.. if block ~-1 ~0 ~0 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~0 ~0 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 2.. if block ~0 ~-1 ~0 #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~-1 ~0 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 3.. if block ~0 ~0 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~0 ~-1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 4.. if block ~0 ~0 ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~0 ~1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 5.. if block ~0 ~1 ~0 #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~1 ~0 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 6.. if block ~1 ~0 ~0 #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~0 ~0 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 7.. if block ~-1 ~-1 ~0 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~-1 ~0 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 8.. if block ~-1 ~0 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~0 ~-1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 9.. if block ~-1 ~0 ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~0 ~1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 10.. if block ~-1 ~1 ~0 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~1 ~0 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 11.. if block ~0 ~-1 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~-1 ~-1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 12.. if block ~0 ~-1 ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~-1 ~1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 13.. if block ~0 ~1 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~1 ~-1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 14.. if block ~0 ~1 ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~0 ~1 ~1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 15.. if block ~1 ~-1 ~0 #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~-1 ~0 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 16.. if block ~1 ~0 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~0 ~-1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 17.. if block ~1 ~0 ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~0 ~1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 18.. if block ~1 ~1 ~0 #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~1 ~0 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 19.. if block ~-1 ~-1 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~-1 ~-1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 20.. if block ~-1 ~-1 ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~-1 ~1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 21.. if block ~-1 ~1 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~1 ~-1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 22.. if block ~-1 ~1 ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~-1 ~1 ~1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 23.. if block ~1 ~-1 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~-1 ~-1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 24.. if block ~1 ~-1 ~1 #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~-1 ~1 air destroy
execute if score #now ench_scratch matches 20.. if score #vein_cap ench_scratch matches 25.. if block ~1 ~1 ~-1 #mod-stella-equips-enchants:vein_blocks run setblock ~1 ~1 ~-1 air destroy
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_vein = #clock ench_clock
