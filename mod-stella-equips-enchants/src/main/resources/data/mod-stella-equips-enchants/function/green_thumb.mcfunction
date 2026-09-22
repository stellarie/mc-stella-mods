# cooldown_ticks: 20
# max_blocks: 1
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_thumb
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:green_thumb_chance if block ~ ~ ~ minecraft:wheat[age=7] run setblock ~ ~ ~ minecraft:wheat[age=0] destroy
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:green_thumb_chance if block ~ ~ ~ minecraft:carrots[age=7] run setblock ~ ~ ~ minecraft:carrots[age=0] destroy
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:green_thumb_chance if block ~ ~ ~ minecraft:potatoes[age=7] run setblock ~ ~ ~ minecraft:potatoes[age=0] destroy
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:green_thumb_chance if block ~ ~ ~ minecraft:beetroots[age=3] run setblock ~ ~ ~ minecraft:beetroots[age=0] destroy
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_thumb = #clock ench_clock
