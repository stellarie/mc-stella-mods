# cooldown_ticks: 60
# max_entities: 16
# The warden wave. Runs as the wearer, because the enchantment names the attacker as the
# affected entity. A small version of the warden's sonic boom: radius 3, sonic damage,
# slowness 3 for 40 ticks as the stagger, and the wearer is excluded from its own wave.
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_wave
execute if score #now ench_scratch matches 60.. run particle minecraft:sonic_boom ~ ~1 ~ 0 0 0 0 1 force
execute if score #now ench_scratch matches 60.. as @e[distance=..3,type=!player] run damage @s 4 mod-stella-equips-enchants:warden_boom
execute if score #now ench_scratch matches 60.. as @e[distance=..3,type=!player] run effect give @s minecraft:slowness 40 3 true
execute if score #now ench_scratch matches 60.. run scoreboard players operation @s ench_cd_wave = #clock ench_clock
