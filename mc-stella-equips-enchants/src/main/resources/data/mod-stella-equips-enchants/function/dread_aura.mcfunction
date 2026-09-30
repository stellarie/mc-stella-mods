# cooldown_ticks: 20
# max_entities: 16
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_dread
execute if score #now ench_scratch matches 20.. run scoreboard players set #dread_hits ench_scratch 0
execute if score #now ench_scratch matches 20.. run tag @s add ench_dread_wearer
execute if score #now ench_scratch matches 20.. run particle minecraft:soul_fire_flame ~ ~1 ~ 0.6 0.6 0.6 0.01 6 force
execute as @e[distance=..8,limit=16,type=!player] at @s positioned ^ ^ ^-2 run execute if score #now ench_scratch matches 20.. if entity @e[tag=ench_dread_wearer,distance=..3] run scoreboard players add #dread_hits ench_scratch 1
execute as @e[distance=..8,limit=16,type=!player] at @s positioned ^ ^ ^-2 run execute if score #now ench_scratch matches 20.. if entity @e[tag=ench_dread_wearer,distance=..3] run effect give @s minecraft:slowness 25 1 true
execute if score #now ench_scratch matches 20.. run tag @s remove ench_dread_wearer
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_dread = #clock ench_clock