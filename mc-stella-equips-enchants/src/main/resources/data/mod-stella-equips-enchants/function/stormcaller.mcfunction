# cooldown_ticks: 200
# max_entities: 1
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_lightning
execute if score #now ench_scratch matches 200.. run summon minecraft:lightning_bolt ~ ~ ~
execute if score #now ench_scratch matches 200.. run scoreboard players operation @s ench_cd_lightning = #clock ench_clock
