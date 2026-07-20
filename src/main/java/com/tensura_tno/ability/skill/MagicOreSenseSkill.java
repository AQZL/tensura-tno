package com.tensura_tno.ability.skill;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.network.MagicOreSensePackets;
import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class MagicOreSenseSkill extends Skill {
    private static final int MAX_MASTERY = 100;
    private static final int NORMAL_RADIUS = 12;
    private static final int MASTERED_RADIUS = 20;
    private static final int NORMAL_DURATION_TICKS = 8 * 20;
    private static final int MASTERED_DURATION_TICKS = 16 * 20;
    private static final int NORMAL_COOLDOWN_SECONDS = 12;
    private static final int MASTERED_COOLDOWN_SECONDS = 6;
    private static final double NORMAL_MAGICULE_COST = 2500.0;
    private static final double MASTERED_MAGICULE_COST = 1500.0;
    private static final double ACQUIRE_MAGICULE = 10000.0;

    public MagicOreSenseSkill() {
        super(SkillType.EXTRA);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "textures/skill/magic_ore_sense.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public boolean checkAcquiringRequirement(Player entity, double newEP) {
        if (EnergyHelper.getBaseMaxMagicule(entity) < ACQUIRE_MAGICULE) return false;
        Optional<ManasSkillInstance> universalPerception =
                SkillAPI.getSkillsFrom(entity).getSkill(ExtraSkills.UNIVERSAL_PERCEPTION.get());
        return universalPerception.isPresent() && universalPerception.get().isMastered(entity);
    }

    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return instance.isMastered(entity) ? MASTERED_MAGICULE_COST : NORMAL_MAGICULE_COST;
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return false;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;
        if (instance.onCoolDown(mode)) return;
        if (EnergyHelper.isOutOfEnergy(player, instance, mode)) return;

        boolean mastered = instance.isMastered(player);
        int radius = mastered ? MASTERED_RADIUS : NORMAL_RADIUS;
        int durationTicks = mastered ? MASTERED_DURATION_TICKS : NORMAL_DURATION_TICKS;
        int cooldownSeconds = mastered ? MASTERED_COOLDOWN_SECONDS : NORMAL_COOLDOWN_SECONDS;

        NetworkManager.sendToPlayer(player, new MagicOreSensePackets.StartScanPayload(radius, durationTicks));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                (SoundEvent) TensuraSoundEvents.BUFF_ACTIVATE.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
        instance.addMasteryPoint(player, 1.0);
        instance.setCoolDown(cooldownSeconds, mode);
        instance.markDirty();
    }
}
