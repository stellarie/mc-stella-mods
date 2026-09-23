# cooldown_ticks: 40
# max_particles: 27
scoreboard players operation #now ench_scratch = #clock ench_clock
scoreboard players operation #now ench_scratch -= @s ench_cd_echo
execute if score #now ench_scratch matches 40.. if block ~-2 ~-2 ~-2 #minecraft:ores run particle minecraft:end_rod ~-2 ~-2 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~-2 ~-2 ~0 #minecraft:ores run particle minecraft:end_rod ~-2 ~-2 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~-2 ~-2 ~2 #minecraft:ores run particle minecraft:end_rod ~-2 ~-2 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~-2 ~0 ~-2 #minecraft:ores run particle minecraft:end_rod ~-2 ~0 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~-2 ~0 ~0 #minecraft:ores run particle minecraft:end_rod ~-2 ~0 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~-2 ~0 ~2 #minecraft:ores run particle minecraft:end_rod ~-2 ~0 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~-2 ~2 ~-2 #minecraft:ores run particle minecraft:end_rod ~-2 ~2 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~-2 ~2 ~0 #minecraft:ores run particle minecraft:end_rod ~-2 ~2 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~-2 ~2 ~2 #minecraft:ores run particle minecraft:end_rod ~-2 ~2 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~-2 ~-2 #minecraft:ores run particle minecraft:end_rod ~0 ~-2 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~-2 ~0 #minecraft:ores run particle minecraft:end_rod ~0 ~-2 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~-2 ~2 #minecraft:ores run particle minecraft:end_rod ~0 ~-2 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~0 ~-2 #minecraft:ores run particle minecraft:end_rod ~0 ~0 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~0 ~0 #minecraft:ores run particle minecraft:end_rod ~0 ~0 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~0 ~2 #minecraft:ores run particle minecraft:end_rod ~0 ~0 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~2 ~-2 #minecraft:ores run particle minecraft:end_rod ~0 ~2 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~2 ~0 #minecraft:ores run particle minecraft:end_rod ~0 ~2 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~0 ~2 ~2 #minecraft:ores run particle minecraft:end_rod ~0 ~2 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~-2 ~-2 #minecraft:ores run particle minecraft:end_rod ~2 ~-2 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~-2 ~0 #minecraft:ores run particle minecraft:end_rod ~2 ~-2 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~-2 ~2 #minecraft:ores run particle minecraft:end_rod ~2 ~-2 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~0 ~-2 #minecraft:ores run particle minecraft:end_rod ~2 ~0 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~0 ~0 #minecraft:ores run particle minecraft:end_rod ~2 ~0 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~0 ~2 #minecraft:ores run particle minecraft:end_rod ~2 ~0 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~2 ~-2 #minecraft:ores run particle minecraft:end_rod ~2 ~2 ~-2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~2 ~0 #minecraft:ores run particle minecraft:end_rod ~2 ~2 ~0 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. if block ~2 ~2 ~2 #minecraft:ores run particle minecraft:end_rod ~2 ~2 ~2 0.3 0.3 0.3 0.01 1 force
execute if score #now ench_scratch matches 40.. run playsound minecraft:block.sculk_sensor.clicking master @s ~ ~ ~ 0.6 1.6
execute if score #now ench_scratch matches 40.. run scoreboard players operation @s ench_cd_echo = #clock ench_clock
