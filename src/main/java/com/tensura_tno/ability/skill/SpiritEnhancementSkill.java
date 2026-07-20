package com.tensura_tno.ability.skill;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import com.tensura_tno.network.ContractLittleFoxPackets;
import com.tensura_tno.race.fox_spirit.SpiritSummonEntityHelper;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SpiritEnhancementSkill extends Skill {
    private static final int COOLDOWN_SECONDS = 120;
    private static final int MAX_MASTERY = 30;
    private static final double BONUS_RATIO = 0.35;
    private static final double MAGICULE_COST_RATIO = 0.5;
    private static final double MASTERED_MAGICULE_COST_RATIO = 0.3;
    private static final float HEALTH_COST_RATIO = 0.2F;
    private static final float MASTERED_HEALTH_COST_RATIO = 0.1F;
    private static final String TAG_ENHANCED = "tensura_tno_spirit_enhanced";
    private static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "spirit_enhancement_health");
    private static final ResourceLocation ATTACK_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "spirit_enhancement_attack");

    public SpiritEnhancementSkill() {
        super(SkillType.EXTRA);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "textures/skill/spirit_enhancement.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        if (entity == null) return 0.0;
        IExistence existence = TensuraStorages.getExistenceFrom(entity);
        double ratio = instance.isMastered(entity) ? MASTERED_MAGICULE_COST_RATIO : MAGICULE_COST_RATIO;
        return existence.getMagicule() * ratio;
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

        List<LivingEntity> targets = findEnhanceableTargets(player);
        if (targets.isEmpty()) {
            boolean hasActivePets = !findActivePets(player).isEmpty();
            player.displayClientMessage(Component.translatable(hasActivePets
                    ? "tensura_tno.spirit_enhancement.already_enhanced"
                    : "tensura_tno.spirit_enhancement.no_targets").withStyle(ChatFormatting.RED), true);
            return;
        }

        boolean mastered = instance.isMastered(player);
        IExistence playerExistence = TensuraStorages.getExistenceFrom(player);
        double magiculeCost = playerExistence.getMagicule()
                * (mastered ? MASTERED_MAGICULE_COST_RATIO : MAGICULE_COST_RATIO);
        float healthCost = player.getHealth()
                * (mastered ? MASTERED_HEALTH_COST_RATIO : HEALTH_COST_RATIO);

        if (!player.hasInfiniteMaterials() && player.getHealth() - healthCost < 1.0F) {
            player.displayClientMessage(Component.translatable("tensura_tno.spirit_enhancement.low_health")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (EnergyHelper.isOutOfEnergy(player, 0.0, magiculeCost)) return;
        if (!player.hasInfiniteMaterials()) {
            player.setHealth(Math.max(1.0F, player.getHealth() - healthCost));
        }

        double healthBonus = player.getMaxHealth() * BONUS_RATIO;
        double attackBonus = player.getAttributeValue(Attributes.ATTACK_DAMAGE) * BONUS_RATIO;
        int enhancedCount = 0;
        for (LivingEntity target : targets) {
            if (applyEnhancement(target, healthBonus, attackBonus)) {
                enhancedCount++;
                TensuraParticleHelper.addServerParticlesAroundSelf(target, ParticleTypes.ENCHANT, 1.0);
            }
        }

        if (enhancedCount <= 0) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                (SoundEvent) TensuraSoundEvents.CAST_DARK.get(), SoundSource.PLAYERS, 0.8F, 1.5F);
        player.displayClientMessage(Component.translatable("tensura_tno.spirit_enhancement.success", enhancedCount), true);
        instance.addMasteryPoint(player, 1.0);
        instance.setCoolDown(COOLDOWN_SECONDS, mode);
        instance.markDirty();
    }

    private static List<LivingEntity> findEnhanceableTargets(ServerPlayer player) {
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity target : findActivePets(player)) {
            if (!isEnhanced(target)) {
                targets.add(target);
            }
        }
        return targets;
    }

    private static List<LivingEntity> findActivePets(ServerPlayer player) {
        LinkedHashSet<LivingEntity> targets = new LinkedHashSet<>();
        FoxSpiritEntity fox = ContractLittleFoxPackets.findActiveFox(player);
        if (fox != null && fox.isAlive()) {
            targets.add(fox);
        }
        targets.addAll(SpiritSummonEntityHelper.findActiveSpiritSummons(player));
        return new ArrayList<>(targets);
    }

    private static boolean isEnhanced(LivingEntity target) {
        return target.getPersistentData().getBoolean(TAG_ENHANCED)
                || hasModifier(target, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID)
                || hasModifier(target, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_ID);
    }

    private static boolean applyEnhancement(LivingEntity target, double healthBonus, double attackBonus) {
        if (isEnhanced(target)) return false;
        boolean changed = false;

        AttributeInstance health = target.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && !health.hasModifier(HEALTH_MODIFIER_ID)) {
            health.addPermanentModifier(new AttributeModifier(
                    HEALTH_MODIFIER_ID, healthBonus, AttributeModifier.Operation.ADD_VALUE));
            target.setHealth(target.getMaxHealth());
            changed = true;
        }

        AttributeInstance attack = target.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && !attack.hasModifier(ATTACK_MODIFIER_ID)) {
            attack.addPermanentModifier(new AttributeModifier(
                    ATTACK_MODIFIER_ID, attackBonus, AttributeModifier.Operation.ADD_VALUE));
            changed = true;
        }

        if (changed) {
            target.getPersistentData().putBoolean(TAG_ENHANCED, true);
        }
        return changed;
    }

    private static boolean hasModifier(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null && instance.hasModifier(id);
    }
}
