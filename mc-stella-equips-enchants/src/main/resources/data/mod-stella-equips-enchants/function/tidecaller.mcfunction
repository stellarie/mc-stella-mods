# cooldown_ticks: 600
# max_effects: 1
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_grace
execute if score #now ench_scratch matches 600.. run effect give @s minecraft:dolphins_grace 20 0 true
execute if score #now ench_scratch matches 600.. run scoreboard players operation @s ench_cd_grace = #clock ench_clock
