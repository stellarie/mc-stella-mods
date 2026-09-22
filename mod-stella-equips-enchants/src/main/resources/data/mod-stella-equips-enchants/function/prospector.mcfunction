# cooldown_ticks: 40
# max_particles: 12
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_prospect
execute if score #now ench_scratch matches 40.. run particle minecraft:end_rod ~0.5 ~0.5 ~0.5 0.4 0.4 0.4 0.02 12 force
execute if score #now ench_scratch matches 40.. run playsound minecraft:block.amethyst_block.chime master @s ~ ~ ~ 0.8 1.4
execute if score #now ench_scratch matches 40.. run scoreboard players operation @s ench_cd_prospect = #clock ench_clock
