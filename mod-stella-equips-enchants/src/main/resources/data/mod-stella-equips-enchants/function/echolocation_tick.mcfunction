# 40 ticks is the two seconds a spark stays lit. A spark stores the clock it was lit at,
# so its age is measured per spark against the world clock, and no two sparks share a score.
scoreboard players operation #now ench_scratch = #clock ench_clock
execute as @e[tag=echo_spark] unless score @s echo_life <= #now ench_scratch run scoreboard players operation @s echo_life = #now ench_scratch
execute as @e[tag=echo_spark] run scoreboard players operation #age ench_scratch = #now ench_scratch
execute as @e[tag=echo_spark] run scoreboard players operation #age ench_scratch -= @s echo_life
execute as @e[tag=echo_spark] if score #age ench_scratch matches ..40 at @s run particle minecraft:end_rod ~ ~ ~ 0.25 0.25 0.25 0 1 force
execute as @e[tag=echo_spark] unless score #age ench_scratch matches ..40 run kill @s
