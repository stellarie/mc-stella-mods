# cooldown_ticks: 20
# max_effects: 2
# Jump Boost 3 adds 0.3 to the jump power, which is about two blocks of height.
# Both effects are refreshed every 20 ticks and last 5 seconds (100 ticks), so they never lapse while the
# boots are worn and fade within five seconds of the boots coming off.
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_cloud
execute if score #now ench_scratch matches 20.. run effect give @s minecraft:jump_boost 5 2 true
execute if score #now ench_scratch matches 20.. run effect give @s minecraft:slow_falling 5 0 true
execute if score #now ench_scratch matches 20.. run particle minecraft:cloud ~ ~0.2 ~ 0.3 0.1 0.3 0.01 4 force
execute if score #now ench_scratch matches 20.. run scoreboard players operation @s ench_cd_cloud = #clock ench_clock
