# cooldown_ticks: 20
# max_effects: 1
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_regen
execute if score #now ench_scratch matches 20.. if entity @s[type=player] run data modify entity @s foodLevel set value 17
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_regen = #clock ench_clock
