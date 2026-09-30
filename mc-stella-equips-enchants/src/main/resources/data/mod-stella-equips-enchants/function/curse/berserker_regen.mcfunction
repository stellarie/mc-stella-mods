# cooldown_ticks: 20
# max_effects: 1
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_regen
execute if score #now ench_scratch matches 20.. run effect give @s minecraft:hunger 60 4 true
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_regen = #clock ench_clock