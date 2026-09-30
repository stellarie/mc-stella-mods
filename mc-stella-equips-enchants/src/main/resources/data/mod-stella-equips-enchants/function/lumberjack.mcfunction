# cooldown_ticks: 20
# max_blocks: 8
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_lumber
execute if score #now ench_scratch matches 20.. if block ~ ~1 ~ #minecraft:logs run setblock ~ ~1 ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~2 ~ #minecraft:logs run setblock ~ ~2 ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~3 ~ #minecraft:logs run setblock ~ ~3 ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~4 ~ #minecraft:logs run setblock ~ ~4 ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~5 ~ #minecraft:logs run setblock ~ ~5 ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~6 ~ #minecraft:logs run setblock ~ ~6 ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~7 ~ #minecraft:logs run setblock ~ ~7 ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~8 ~ #minecraft:logs run setblock ~ ~8 ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~1 ~ #minecraft:logs run scoreboard players operation @s ench_cd_lumber = #clock ench_clock
