package com.tensura_tno.ability.skill;

import com.mojang.datafixers.util.Pair;
import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.network.EdoTenseiPackets;
import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.magic.summon.SummoningMagic;
import io.github.manasmods.tensura.entity.magic.MagicCircle;
import io.github.manasmods.tensura.entity.template.subclass.ISubordinate;
import io.github.manasmods.tensura.entity.variant.MagicCircleVariant;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 禁忌召唤術 — Summoning magic with two tiers:
 *   Normal tier: 30,000 magicule, unlocked by killing common monsters.
 *   Mastered tier: 150,000 magicule, unlocked by killing boss monsters WHILE having the skill.
 *
 * Mastery: +25 per successful summon (100 max → mastered after 4 summons).
 */
public class EdoTenseiSkill extends SummoningMagic<Mob> {

    private static final int CAST_TIME = 60;
    private static final int COOLDOWN_TICKS = 400;
    /** 0 = permanent summon, never despawns on timer. */
    private static final int SUMMON_DURATION = 0;
    private static final String SELECTED_ENTITY_KEY = "SelectedEntity";
    private static final double NORMAL_COST = 30000.0;
    private static final double BOSS_COST = 150000.0;

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "textures/skill/edo_tensei.png");
    }

    @Override
    public MutableComponent getColoredName() {
        MutableComponent name = super.getName();
        return name == null ? null : name.withColor(0x8B0000);
    }

    @Override
    public int getMaxMastery() {
        return 100;
    }

    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        CompoundTag tag = instance.getTag();
        if (tag != null && tag.contains(SELECTED_ENTITY_KEY)) {
            String id = tag.getString(SELECTED_ENTITY_KEY);
            if (EdoTenseiPackets.BOSS_ENTITIES.contains(id)) {
                return BOSS_COST;
            }
        }
        return NORMAL_COST;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 5.0;
    }

    @Override
    public int getDefaultCastTime() {
        return CAST_TIME;
    }

    @Override
    public int getSuccessCooldown(ManasSkillInstance instance, LivingEntity entity) {
        return COOLDOWN_TICKS;
    }

    @Override
    public boolean isInstantCast(ManasSkillInstance instance, LivingEntity entity) {
        return false;
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return false;
    }

    @Override
    public boolean canRemoveSummon(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return false;
    }

    // ── onPressed: open GUI if nothing selected, otherwise start summoning ──

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        CompoundTag tag = instance.getOrCreateTag();
        if (!tag.contains(SELECTED_ENTITY_KEY)) {
            if (entity instanceof ServerPlayer sp) {
                boolean mastered = instance.isMastered(entity);
                NetworkManager.sendToPlayer(sp, new EdoTenseiPackets.OpenScreenPayload(
                        String.join(",", EdoTenseiPackets.getUnlockedEntityIds(sp, mastered)),
                        mastered));
            }
            return;
        }
        super.onPressed(instance, entity, keyNumber, mode);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (!instance.getOrCreateTag().contains(SELECTED_ENTITY_KEY)) {
            return false;
        }
        return super.onHeld(instance, entity, heldTicks, mode);
    }

    // ── ISummoning implementation ──

    @Override
    @SuppressWarnings("unchecked")
    public EntityType<? extends Mob> getSummonedType(ManasSkillInstance instance, LivingEntity entity, int mode) {
        CompoundTag tag = instance.getTag();
        if (tag != null && tag.contains(SELECTED_ENTITY_KEY)) {
            ResourceLocation rl = ResourceLocation.parse(tag.getString(SELECTED_ENTITY_KEY));
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                return (EntityType<? extends Mob>) BuiltInRegistries.ENTITY_TYPE.get(rl);
            }
        }
        return null;
    }

    @Override
    public void addAdditionalSummonData(ManasSkillInstance instance, LivingEntity entity, Mob summon, int mode) {
        if (entity instanceof Player player) {
            if (summon instanceof ISubordinate sub) {
                sub.tame(player);
            } else if (summon instanceof TamableAnimal ta) {
                ta.setOwnerUUID(player.getUUID());
                ta.setTame(true, true);
            }
        }
        summon.skipDropExperience();

        ResourceLocation summonId = BuiltInRegistries.ENTITY_TYPE.getKey(summon.getType());
        if (summonId != null && "tensura:shizu".equals(summonId.toString())) {
            try {
                java.lang.reflect.Method setTT = summon.getClass().getMethod("setTransformTick", int.class);
                setTT.invoke(summon, 100);
            } catch (Exception ignored) {}

            summon.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            summon.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            summon.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            try {
                java.lang.reflect.Method getSlotId = summon.getClass().getMethod("getSlotId", EquipmentSlot.class);
                java.lang.reflect.Field invField = null;
                for (Class<?> cls = summon.getClass(); cls != null; cls = cls.getSuperclass()) {
                    try { invField = cls.getDeclaredField("inventory"); break; }
                    catch (NoSuchFieldException ignored2) {}
                }
                if (invField != null) {
                    invField.setAccessible(true);
                    Object inv = invField.get(summon);
                    java.lang.reflect.Method setItem = inv.getClass().getMethod("setItem", int.class, ItemStack.class);
                    setItem.invoke(inv, (int) getSlotId.invoke(summon, EquipmentSlot.HEAD), ItemStack.EMPTY);
                    setItem.invoke(inv, (int) getSlotId.invoke(summon, EquipmentSlot.MAINHAND), ItemStack.EMPTY);
                }
                java.lang.reflect.Method updateEquip = summon.getClass().getMethod("updateContainerEquipment");
                updateEquip.invoke(summon);
            } catch (Exception ignored) {}
        }

        IExistence existence = TensuraStorages.getExistenceFrom(summon);
        existence.setSummonedSecond(SUMMON_DURATION);
        existence.setSummoner(entity.getUUID());
        existence.setSummonedAbility(this, mode);
        existence.markDirty();
    }

    @Override
    public void summonMagicCircle(ManasSkillInstance instance, LivingEntity entity, Vec3 pos, int heldTicks, int mode) {
        Pair<Double, Double> cost = Pair.of(
                this.getAuraCost(entity, instance, mode),
                this.getMagiculeCost(entity, instance, mode));
        MagicCircle.castMagicCircle(3.0F, 30, pos, MagicCircleVariant.DEMON, entity,
                instance.getOrCreateTag(), instance, mode, cost);
    }

    @Override
    public void onPostSummon(ManasSkillInstance instance, LivingEntity entity, Mob summon, int mode) {
        super.onPostSummon(instance, entity, summon, mode);
        instance.getOrCreateTag().remove(SELECTED_ENTITY_KEY);
        instance.addMasteryPoint(entity, 25.0);
        instance.markDirty();
    }

    @Override
    public void onSummonDeath(ManasSkillInstance instance, LivingEntity summon) {
        // Do NOT reset cooldown when the summoned entity dies.
        // Default ISummoning.onSummonDeath clears SummonUUID and sets cooldown to 0.
        // We only clear the UUID but keep the cooldown.
        net.minecraft.nbt.CompoundTag tag = instance.getTag();
        if (tag != null && tag.hasUUID("SummonUUID")) {
            if (java.util.Objects.equals(summon.getUUID(), tag.getUUID("SummonUUID"))) {
                tag.remove("SummonUUID");
            }
        }
    }

    @Override
    public ParticleOptions getSummoningParticle(ManasSkillInstance instance, int mode) {
        return TensuraParticleUtils.getWhiteEffect();
    }

    @Override
    public SoundEvent getSummoningSound(ManasSkillInstance instance, int mode) {
        return (SoundEvent) TensuraSoundEvents.CAST_DARK.get();
    }

    @Override
    public SoundEvent getFailSound(ManasSkillInstance instance, int mode) {
        return (SoundEvent) TensuraSoundEvents.LEECH_LIZARD_DEATH.get();
    }

    @Override
    public Component getModeName(ManasSkillInstance instance, int mode) {
        CompoundTag tag = instance.getTag();
        if (tag != null && tag.contains(SELECTED_ENTITY_KEY)) {
            String entityId = tag.getString(SELECTED_ENTITY_KEY);
            ResourceLocation rl = ResourceLocation.parse(entityId);
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                return BuiltInRegistries.ENTITY_TYPE.get(rl).getDescription();
            }
        }
        return Component.translatable("tensura_tno.edo_tensei.select");
    }
}
