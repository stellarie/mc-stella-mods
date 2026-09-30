# cooldown_ticks: 20
# max_damage: 3
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_plating
execute if score #now ench_scratch matches 20.. if entity @s[type=!player] store result score #hp ench_scratch run data get entity @s Health
execute if score #now ench_scratch matches 20.. if entity @s[type=!player] if score #hp ench_scratch matches 4.. run damage @s 3 mod-stella-equips-enchants:enchant_bonus
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_plating = #clock ench_clock
