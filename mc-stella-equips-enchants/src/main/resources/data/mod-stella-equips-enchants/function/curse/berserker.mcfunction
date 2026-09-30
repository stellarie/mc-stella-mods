# cooldown_ticks: 20
# max_effects: 8
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_berserk
execute if score #now ench_scratch matches 20.. store result score #berserk_hp ench_scratch run data get entity @s Health
execute if score #now ench_scratch matches 20.. run scoreboard players set #berserk_tier ench_scratch 0
execute if score #now ench_scratch matches 20.. if score #berserk_hp ench_scratch matches ..15 run scoreboard players set #berserk_tier ench_scratch 1
execute if score #now ench_scratch matches 20.. if score #berserk_hp ench_scratch matches ..10 run scoreboard players set #berserk_tier ench_scratch 2
execute if score #now ench_scratch matches 20.. if score #berserk_hp ench_scratch matches ..5 run scoreboard players set #berserk_tier ench_scratch 3
execute if score #now ench_scratch matches 20.. unless score #berserk_tier ench_scratch = @s ench_berserk_tier run attribute @s minecraft:attack_damage modifier remove mod-stella-equips-enchants:berserker
execute if score #now ench_scratch matches 20.. unless score #berserk_tier ench_scratch = @s ench_berserk_tier run scoreboard players operation @s ench_berserk_tier = #berserk_tier ench_scratch
execute if score #now ench_scratch matches 20.. if score #berserk_tier ench_scratch matches 1 run attribute @s minecraft:attack_damage modifier add mod-stella-equips-enchants:berserker 0.4 add_multiplied_total
execute if score #now ench_scratch matches 20.. if score #berserk_tier ench_scratch matches 2 run attribute @s minecraft:attack_damage modifier add mod-stella-equips-enchants:berserker 0.8 add_multiplied_total
execute if score #now ench_scratch matches 20.. if score #berserk_tier ench_scratch matches 3 run attribute @s minecraft:attack_damage modifier add mod-stella-equips-enchants:berserker 1.2 add_multiplied_total
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_berserk = #clock ench_clock