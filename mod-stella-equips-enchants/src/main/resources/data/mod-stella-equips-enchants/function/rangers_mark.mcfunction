# cooldown_ticks: 20
# max_damage: 4
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_mark
execute if score #now ench_scratch matches 20.. store result score #hp ench_scratch run data get entity @s Health
execute if score #now ench_scratch matches 20.. store result score #max ench_scratch run attribute @s minecraft:max_health get
scoreboard players operation #double ench_scratch = #hp ench_scratch
scoreboard players operation #double ench_scratch *= #two ench_scratch
execute if score #now ench_scratch matches 20.. if score #double ench_scratch >= #max ench_scratch run damage @s 4 mod-stella-equips-enchants:enchant_bonus
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_mark = #clock ench_clock
