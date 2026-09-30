# cooldown_ticks: 40
# max_damage: 4
scoreboard players add #charge ench_momentum 1
execute if score #charge ench_momentum matches 6.. run scoreboard players operation #now ench_scratch = #clock ench_clock
execute if score #charge ench_momentum matches 6.. run scoreboard players operation #now ench_scratch -= #burst ench_scratch
execute if score #charge ench_momentum matches 6.. if score #now ench_scratch matches 40.. run damage @s 4 mod-stella-equips-enchants:enchant_bonus
execute if score #charge ench_momentum matches 6.. if score #now ench_scratch matches 40.. run scoreboard players operation #burst ench_scratch = #clock ench_clock
execute if score #charge ench_momentum matches 6.. run scoreboard players set #charge ench_momentum 0
