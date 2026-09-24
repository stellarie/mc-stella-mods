# cooldown_ticks: 20
# max_effects: 2
# Jump Boost 3 adds 0.3 to the jump power, which is about two blocks of height.
# The effect is refreshed every 20 ticks and lasts 40, so the remaining duration
# never falls below 20 ticks and gravity cannot lapse mid jump.
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_cloud
execute if score #now ench_scratch matches 20.. run scoreboard players set #cloud_pred ench_scratch 0
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:cloudstep_sneaking run scoreboard players set #cloud_pred ench_scratch 1
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:cloudstep_sneaking run effect give @s minecraft:jump_boost 40 2 true
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:cloudstep_sneaking run effect give @s minecraft:slow_falling 40 0 true
execute if score #now ench_scratch matches 20.. if predicate mod-stella-equips-enchants:cloudstep_sneaking run particle minecraft:cloud ~ ~0.2 ~ 0.3 0.1 0.3 0.01 4 force
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_cloud = #clock ench_clock
