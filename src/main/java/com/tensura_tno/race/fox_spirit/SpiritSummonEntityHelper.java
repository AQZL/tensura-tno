package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.registry.TensuraTNOSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

public final class SpiritSummonEntityHelper {
    public static final String TAG_SPIRIT_SUMMON = "tensura_tno_spirit_summon_entity";
    public static final String TAG_SPIRIT_SUMMON_OWNER = "tensura_tno_spirit_summon_owner";

    private SpiritSummonEntityHelper() {
    }

    public static boolean isSpiritSummonOf(Entity entity, ServerPlayer owner) {
        return owner != null && isSpiritSummonOf(entity, (Player) owner);
    }

    public static boolean isSpiritSummonOf(Entity entity, Player owner) {
        if (owner == null || !(entity instanceof LivingEntity living)) return false;
        UUID ownerId = getSpiritSummonOwnerUUID(living);
        return owner.getUUID().equals(ownerId) && isSpiritSummon(living);
    }

    public static boolean isSpiritSummon(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.getPersistentData().getBoolean(TAG_SPIRIT_SUMMON)) return true;
        try {
            IExistence existence = TensuraStorages.getExistenceFrom(living);
            return existence.getSummonedAbility() != null
                    && existence.getSummonedAbility().getSkill() == TensuraTNOSkills.SPIRIT_SUMMON.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static UUID getSpiritSummonOwnerUUID(LivingEntity entity) {
        if (entity == null) return null;
        try {
            IExistence existence = TensuraStorages.getExistenceFrom(entity);
            UUID summoner = existence.getSummoner();
            if (summoner != null) return summoner;
            UUID permanentOwner = existence.getPermanentOwner();
            if (permanentOwner != null) return permanentOwner;
            UUID temporaryOwner = existence.getTemporaryOwner();
            if (temporaryOwner != null) return temporaryOwner;
        } catch (Exception ignored) {
        }
        if (entity.getPersistentData().hasUUID(TAG_SPIRIT_SUMMON_OWNER)) {
            return entity.getPersistentData().getUUID(TAG_SPIRIT_SUMMON_OWNER);
        }
        return null;
    }

    public static void markSpiritSummon(Mob summon, ServerPlayer owner) {
        summon.getPersistentData().putBoolean(TAG_SPIRIT_SUMMON, true);
        summon.getPersistentData().putUUID(TAG_SPIRIT_SUMMON_OWNER, owner.getUUID());
        IExistence existence = TensuraStorages.getExistenceFrom(summon);
        existence.setPermanentOwner(owner.getUUID());
        existence.setSummoner(owner.getUUID());
        existence.setSummonedAbility(TensuraTNOSkills.SPIRIT_SUMMON.get(), 1);
        existence.markDirty();
    }

    public static void restoreSpiritSummonIdentity(LivingEntity summon, Player owner) {
        if (summon == null || owner == null || !isSpiritSummon(summon)) return;
        summon.getPersistentData().putBoolean(TAG_SPIRIT_SUMMON, true);
        summon.getPersistentData().putUUID(TAG_SPIRIT_SUMMON_OWNER, owner.getUUID());
        IExistence existence = TensuraStorages.getExistenceFrom(summon);
        existence.setPermanentOwner(owner.getUUID());
        existence.setTemporaryOwner(null);
        existence.setSummoner(owner.getUUID());
        existence.setSummonedAbility(TensuraTNOSkills.SPIRIT_SUMMON.get(), 1);
        existence.markDirty();
    }

    public static boolean isOwnedBy(Entity entity, ServerPlayer owner) {
        if (entity.getUUID().equals(owner.getUUID())) return true;
        if (entity instanceof LivingEntity living) {
            UUID spiritOwner = getSpiritSummonOwnerUUID(living);
            if (owner.getUUID().equals(spiritOwner)) return true;
            try {
                IExistence existence = TensuraStorages.getExistenceFrom(living);
                UUID summoner = existence.getSummoner();
                if (owner.getUUID().equals(summoner)) return true;
                UUID permanentOwner = existence.getPermanentOwner();
                if (owner.getUUID().equals(permanentOwner)) return true;
                UUID temporaryOwner = existence.getTemporaryOwner();
                if (owner.getUUID().equals(temporaryOwner)) return true;
            } catch (Exception ignored) {
            }
        }
        if (entity instanceof OwnableEntity ownable) {
            return owner.getUUID().equals(ownable.getOwnerUUID());
        }
        return false;
    }

    public static List<Mob> findActiveSpiritSummons(ServerPlayer owner) {
        List<Mob> result = new ArrayList<>();
        if (!(owner.level() instanceof ServerLevel level)) return result;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob)) continue;
            if (!mob.isAlive()) continue;
            if (isSpiritSummonOf(mob, owner)) {
                result.add(mob);
            }
        }
        return result;
    }
}
