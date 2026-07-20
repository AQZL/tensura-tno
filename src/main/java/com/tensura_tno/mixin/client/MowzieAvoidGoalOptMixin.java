package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOCompatConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Optimizes Mowzie's Mobs {@code AvoidEntityIfNotTamedGoal.canUse()}.
 *
 * <p>The original code calls the expensive {@code super.canUse()} (which runs
 * {@code getEntitiesOfClass} + iterates all {@code TensuraPartEntity} via
 * Tensura's Level mixin) <b>before</b> checking whether the animal is tamed.
 * For each BladeTiger, Mowzie's injects 5 of these goals, resulting in 5
 * full entity scans per server tick — even for tamed entities that will
 * always return {@code false}.</p>
 *
 * <p>This mixin applies two optimizations:</p>
 * <ol>
 *   <li><b>Early tame check</b> — return {@code false} immediately for tamed
 *       animals, completely skipping the entity scan (zero-cost).</li>
 *   <li><b>Scan throttle</b> — for untamed animals, only run the real
 *       {@code canUse()} every 4 ticks (~200 ms) instead of every tick,
 *       reducing entity scan volume by 75 %.</li>
 * </ol>
 */
@Pseudo
@Mixin(targets = "com.bobmowzie.mowziesmobs.server.ai.AvoidEntityIfNotTamedGoal", remap = false)
public abstract class MowzieAvoidGoalOptMixin extends AvoidEntityGoal<LivingEntity> {

    @SuppressWarnings("unused")
    private MowzieAvoidGoalOptMixin(PathfinderMob mob, Class<LivingEntity> cls, float dist, double walk, double sprint) {
        super(mob, cls, dist, walk, sprint);
    }

    @Unique
    private int tensuraTno$throttleCounter = ThreadLocalRandom.current().nextInt(4);

    @Inject(
        method = "canUse",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void tensuraTno$optimizeCanUse(CallbackInfoReturnable<Boolean> cir) {
        if (!TensuraTNOCompatConfig.isMowzieAvoidGoalOptEnabled()) return;

        if (this.mob instanceof TamableAnimal ta && ta.isTame()) {
            cir.setReturnValue(false);
            return;
        }

        if (++tensuraTno$throttleCounter % 4 != 0) {
            cir.setReturnValue(false);
        }
    }
}
