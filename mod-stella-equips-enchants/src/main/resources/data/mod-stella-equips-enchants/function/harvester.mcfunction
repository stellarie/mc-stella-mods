# cooldown_ticks: 20
# max_blocks: 9
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_harvest
execute if score #now ench_scratch matches 20.. if block ~-1 ~ ~-1 #mod-stella-equips-enchants:harvestable_crops run setblock ~-1 ~ ~-1 air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~ ~-1 #mod-stella-equips-enchants:harvestable_crops run setblock ~ ~ ~-1 air destroy
execute if score #now ench_scratch matches 20.. if block ~1 ~ ~-1 #mod-stella-equips-enchants:harvestable_crops run setblock ~1 ~ ~-1 air destroy
execute if score #now ench_scratch matches 20.. if block ~-1 ~ ~ #mod-stella-equips-enchants:harvestable_crops run setblock ~-1 ~ ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~ ~ #mod-stella-equips-enchants:harvestable_crops run setblock ~ ~ ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~1 ~ ~ #mod-stella-equips-enchants:harvestable_crops run setblock ~1 ~ ~ air destroy
execute if score #now ench_scratch matches 20.. if block ~-1 ~ ~1 #mod-stella-equips-enchants:harvestable_crops run setblock ~-1 ~ ~1 air destroy
execute if score #now ench_scratch matches 20.. if block ~ ~ ~1 #mod-stella-equips-enchants:harvestable_crops run setblock ~ ~ ~1 air destroy
execute if score #now ench_scratch matches 20.. if block ~1 ~ ~1 #mod-stella-equips-enchants:harvestable_crops run setblock ~1 ~ ~1 air destroy
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_harvest = #clock ench_clock
