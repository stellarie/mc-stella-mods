# cooldown_ticks: 60
# max_blocks: 2
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_hook
execute if score #now ench_scratch matches 60.. at @p if entity @s[distance=3..16] positioned ~ ~ ~ facing entity @s feet run tp @s ^ ^ ^2
execute if score #now ench_scratch matches 60.. run scoreboard players operation @s ench_cd_hook = #clock ench_clock
