# cooldown_ticks: 20
# max_effects: 1
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_night
execute if score #now ench_scratch matches 20.. run effect give @s minecraft:night_vision 30 0 true
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_night = #clock ench_clock
