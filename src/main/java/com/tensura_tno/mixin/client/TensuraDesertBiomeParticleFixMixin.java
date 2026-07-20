package com.tensura_tno.mixin.client;

import io.github.manasmods.tensura.world.biome.overworld.BarrenLandBiome;
import io.github.manasmods.tensura.world.biome.overworld.DesertOfDeathBiome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 将死亡沙漠（DesertOfDeath）和荒地（BarrenLand）群系的环境粒子概率
 * 从 0.1F 降低到 0.005F。
 *
 * 根本原因：主模组在这两个群系的 BiomeSpecialEffects 中设置了
 *   new AmbientParticleSettings(FALLING_DUST(SAND), 0.1F)
 * 而原版最高粒子概率（灵魂沙谷）仅为 0.00625F。
 * 0.1F 是原版的 16 倍，会在每 tick 持续产生大量飘落沙尘粒子，
 * 导致 GPU 半透明粒子批量渲染压力骤升，帧率严重下降。
 *
 * 此 Mixin 放在公共（mixins）区段，在服务端创建群系时即生效，
 * 确保多人游戏中服务端向客户端同步的群系数据已包含修正后的概率。
 */
@Mixin({DesertOfDeathBiome.class, BarrenLandBiome.class})
public class TensuraDesertBiomeParticleFixMixin {

    /**
     * 拦截 AmbientParticleSettings 构造调用，将概率参数从 0.1F 改为 0.005F。
     * index = 1 对应构造函数第二个参数（float probability）。
     */
    @ModifyArg(
        method = "create",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/AmbientParticleSettings;<init>(Lnet/minecraft/core/particles/ParticleOptions;F)V",
            remap = false
        ),
        index = 1,
        remap = false
    )
    private static float tensuraTno$fixBiomeParticleProbability(float probability) {
        // 降至约等于原版灵魂沙谷（0.00625F）的水平，保留沙尘氛围但不拖垮帧率
        return 0.002F;
    }
}
