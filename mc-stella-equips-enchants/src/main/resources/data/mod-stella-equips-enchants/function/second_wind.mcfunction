# cooldown_ticks: 2400
# max_effects: 1
scoreboard players set #two ench_scratch 2
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_wind
execute if score #now ench_scratch matches 2400.. store result score #hp ench_scratch run data get entity @s Health
execute if score #now ench_scratch matches 2400.. store result score #max ench_scratch run attribute @s minecraft:max_health get
scoreboard players operation #half ench_scratch = #max ench_scratch
scoreboard players operation #half ench_scratch /= #two ench_scratch
execute if score #now ench_scratch matches 2400.. if score #hp ench_scratch <= #half ench_scratch run effect give @s minecraft:regeneration 6 1 true
execute if score #now ench_scratch matches 2400.. if score #hp ench_scratch <= #half ench_scratch run scoreboard players operation @s ench_cd_wind = #clock ench_clock
