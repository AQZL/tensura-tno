package com.tensura_tno.ability.skill;

import com.mojang.datafixers.util.Pair;
import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.network.SpiritSummonPackets;
import com.tensura_tno.race.fox_spirit.FoxSpiritSummonBonus;
import com.tensura_tno.race.fox_spirit.SpiritSummonEntityHelper;
import com.tensura_tno.registry.TensuraTNOSkills;
import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.entity.magic.MagicCircle;
import io.github.manasmods.tensura.entity.variant.MagicCircleVariant;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.data.TensuraCustomData;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 鐏典箣鍙敜 鈥斺€?鍐呭湪鎶€鑳姐€備袱绉嶆ā寮忥細
 * <ul>
 *   <li>Mode 0 (鏀剁撼): 鎸変綇鎶€鑳介敭瀵圭潃鍗婅浠ヤ笅涓旀渶澶P 鈮?100 鐨勭敓鐗╋紝
 *       榄旀硶闃佃搫鍔?鈫?鐢熺墿瀹氫綇骞剁紦缂撲笅娌?鈫?鏀剁撼瀹屾垚銆?/li>
 *   <li>Mode 1 (鍙敜): 鍏堟寜涓€娆℃墦寮€ GUI 閫夋嫨瀹炰綋锛岄€夊ソ鍚庢寜浣忔妧鑳介敭锛?
 *       榄旀硶闃佃搫鍔?鈫?鐢熺墿浠庡湴搴曞崌璧?鈫?鍙敜瀹屾垚銆?/li>
 * </ul>
 */
public class SpiritSummonSkill extends Skill {

    private static final double ABSORB_MP_COST = 500.0;
    private static final double SUMMON_MP_COST = 500.0;
    private static final double SUMMON_MAGICULE_COST_RATIO = 0.5;
    private static final int DEFAULT_SUMMON_MAGICULE = 50;
    // Absorb cooldown in seconds.
    private static final int ABSORB_COOLDOWN_SECONDS = 30;
    // Summon cooldown in seconds.
    private static final int SUMMON_COOLDOWN_SECONDS = 5;
    private static final double ABSORB_RANGE = 5.0;
    /** 鏀剁撼榛戝悕鍗曪細Boss 绾у疄浣撶姝㈣鏀剁撼銆?*/
    private static final TagKey<EntityType<?>> SPIRIT_SUMMON_BLACKLIST =
            TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "spirit_summon_blacklist"));
    /** 榄旀硶闃佃搫鍔涢樁娈垫寔缁?ticks */
    private static final int CAST_TIME = 20;
    /** 涓嬫矇 / 鍗囪捣鍔ㄧ敾鎸佺画 ticks */
    private static final int ANIMATION_TICKS = 40;

    // 鈹€鈹€ instance NBT 閿?鈹€鈹€
    private static final String NBT_ABSORB_TARGET = "SpiritAbsorbTargetUUID";
    private static final String NBT_CIRCLE_X      = "circleX";
    private static final String NBT_CIRCLE_Y      = "circleY";
    private static final String NBT_CIRCLE_Z      = "circleZ";
    private static final String NBT_SUMMON_UUID   = "SummonUUID";
    private static final String NBT_CIRCLE_ABSORB = "MagicCircleAbsorbID";
    private static final String NBT_CIRCLE_SUMMON = "MagicCircleSummonID";
    private static final String NBT_SUMMON_MP_COST = "SummonMagiculeCost";

    // Player persistent key for the selected summon entity type.
    public static final String NBT_PENDING_SUMMON = "tensura_tno_pending_summon";

    public SpiritSummonSkill() {
        super(SkillType.INTRINSIC);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "textures/skill/spirit_summon.png");
    }

    @Override
    public MutableComponent getColoredName() {
        MutableComponent name = super.getName();
        return name == null ? null : name.withColor(0x7B68EE);
    }

    // 鈹€鈹€ 妯″紡绯荤粺 鈹€鈹€

    @Override
    public int getModes(ManasSkillInstance instance) { return 2; }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "spirit_summon.absorb";
            case 1 -> "spirit_summon.summon";
            default -> "default";
        };
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return switch (mode) { case 0 -> 1; case 1 -> 0; default -> 0; };
    }

    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        if (mode == 0) return ABSORB_MP_COST;
        if (mode != 1) return 0.0;

        CompoundTag tag = instance.getTag();
        if (tag != null && tag.contains(NBT_SUMMON_MP_COST)) {
            return tag.getDouble(NBT_SUMMON_MP_COST);
        }
        if (entity instanceof ServerPlayer player) {
            String pendingId = player.getPersistentData().getString(NBT_PENDING_SUMMON);
            if (!pendingId.isEmpty()) {
                return getSummonMagiculeCost(player.level(), pendingId);
            }
        }
        return 0.0;
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return false;
    }

    // 鈹€鈹€ onPressed: 鍒濆鍖栨柦娉?鈹€鈹€

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;
        switch (mode) {
            case 0 -> startAbsorb(instance, player);
            case 1 -> startSummon(instance, player);
        }
    }

    /**
     * Mode 0 鏀剁撼鍒濆鍖栵細
     * 涓ユ牸灏勭嚎妫€娴?鈫?鏍￠獙鐩爣鏉′欢 鈫?灏嗙洰鏍?UUID 鍜岄瓟娉曢樀浣嶇疆鍐欏叆 instance NBT銆?
     * 鍚庣画鐢?{@link #handleAbsorbHeld} 椹卞姩鍔ㄧ敾銆?
     */
    private void startAbsorb(ManasSkillInstance instance, ServerPlayer player) {
        if (instance.onCoolDown(0)) return;

        // 妫€鏌?MP锛堝垱閫犳ā寮忚烦杩囷級
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        if (!player.isCreative() && existence.getMagicule() < ABSORB_MP_COST) {
            player.displayClientMessage(
                    Component.translatable("tensura.skill.lack_magicule")
                            .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
            return;
        }

        // 涓ユ牸灏勭嚎鍛戒腑妫€娴嬶紙鏃犲厹搴曪紝瀵圭潃鏂瑰潡/绌烘皵闈欓粯蹇界暐锛?
        Mob target = getTargetMobStrictly(player);
        if (target == null) return; // 鏈懡涓换浣曞疄浣擄紝闈欓粯蹇界暐

        // Boss 榛戝悕鍗曟鏌モ€斺€旂姝㈡敹绾虫湯褰遍緳銆佸噵鐏电瓑 Boss 绾у疄浣擄紙闈欓粯鎷掔粷锛?
        if (target.getType().is(SPIRIT_SUMMON_BLACKLIST)) return;

        // 瑙勫垯 1锛歴piritSummonVanillaOnly = true 鏃朵粎鍏佽鍘熺増鐢熺墿
        if (SpiritSummonLimits.isVanillaOnlyEnforced(player.level())) {
            ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
            if (!SpiritSummonLimits.isVanilla(targetTypeId)) {
                player.displayClientMessage(
                        Component.translatable("tensura_tno.spirit_summon.target_not_vanilla")
                                .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
                return;
            }
        }

        // 瑙勫垯 2锛歴piritSummonMaxHealth 涓婇檺
        if (!SpiritSummonLimits.isMaxHealthAllowed(player.level(), target)) {
            int limit = SpiritSummonLimits.getMaxHealthLimit(player.level());
            player.displayClientMessage(
                    Component.translatable("tensura_tno.spirit_summon.target_max_hp_too_high", limit)
                            .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
            return;
        }

        // 鏍￠獙鏉′欢锛氭湁鏈夋晥鐩爣浣嗘潯浠朵笉婊¤冻鏃剁粰鍑烘彁绀?
        if (target.getHealth() > target.getMaxHealth() * 0.5F) {
            player.displayClientMessage(
                    Component.translatable("tensura_tno.spirit_summon.target_hp_too_high")
                            .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
            return;
        }
        if (target instanceof net.minecraft.world.entity.OwnableEntity ownable
                && ownable.getOwnerUUID() != null) {
            player.displayClientMessage(
                    Component.translatable("tensura_tno.spirit_summon.target_has_owner")
                            .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
            return;
        }

        // 鍐欏叆 instance NBT锛氱洰鏍?UUID + 榄旀硶闃典綅缃紙= 鐩爣褰撳墠浣嶇疆锛?
        CompoundTag tag = instance.getOrCreateTag();
        tag.putUUID(NBT_ABSORB_TARGET, target.getUUID());
        tag.putDouble(NBT_CIRCLE_X, target.getX());
        tag.putDouble(NBT_CIRCLE_Y, target.getY());
        tag.putDouble(NBT_CIRCLE_Z, target.getZ());
        instance.markDirty();
    }

    /**
     * Mode 1 鍙敜鍒濆鍖栵細
     * <ul>
     *   <li>濡傛湁寰呭彫鍞ら€夋嫨 鈫?纭畾鍦伴潰浣嶇疆锛屽噯澶囨寜浣忓姩鐢汇€?/li>
     *   <li>鏃犻€夋嫨 鈫?鎵撳紑 GUI 璁╃帺瀹堕€夋嫨瀹炰綋绫诲瀷銆?/li>
     * </ul>
     */
    private void startSummon(ManasSkillInstance instance, ServerPlayer player) {
        String pendingId = player.getPersistentData().getString(NBT_PENDING_SUMMON);
        if (!pendingId.isEmpty()) {
            // 宸叉湁寰呭彫鍞ゅ璞?鈫?鏍￠獙鍐峰嵈 & MP锛屽啀纭畾鏂芥硶浣嶇疆
            if (instance.onCoolDown(1)) return;
            if (!SpiritSummonPockets.hasAbsorbedEntity(player, pendingId)) {
                player.getPersistentData().remove(NBT_PENDING_SUMMON);
                cleanupSummon(instance, player);
                return;
            }
            double summonCost = getSummonMagiculeCost(player.level(), pendingId);
            IExistence existence = TensuraStorages.getExistenceFrom(player);
            if (!player.isCreative() && existence.getMagicule() < summonCost) {
                player.displayClientMessage(
                        Component.translatable("tensura.skill.lack_magicule")
                                .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
                player.getPersistentData().remove(NBT_PENDING_SUMMON);
                cleanupSummon(instance, player);
                return;
            }

            // 鍙敜鏁伴噺涓婇檺妫€鏌?
            int maxSummons = FoxSpiritSummonBonus.getMaxSummons(player);
            if (maxSummons <= 0) {
                player.getPersistentData().remove(NBT_PENDING_SUMMON);
                cleanupSummon(instance, player);
                // 闈炵嫄鐏电鏃忔棤娉曚娇鐢ㄧ伒涔嬪彫鍞?
                return;
            }
            int activeCount = countActiveSummons(player);
            if (activeCount >= maxSummons) {
                player.displayClientMessage(
                        Component.translatable("tensura_tno.spirit_summon.summon_limit_reached", maxSummons)
                                .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
                player.getPersistentData().remove(NBT_PENDING_SUMMON);
                cleanupSummon(instance, player);
                return;
            }

            // 灏勭嚎鎵惧湴闈綅缃紙鍑嗘槦鐪嬪悜鐨勬柟鍧楅潰锛?
            BlockHitResult hitResult = ObjectSelectionHelper.getPlayerPOVHitResult(
                    player.level(), player, ClipContext.Fluid.NONE, 10.0);
            BlockPos hitPos = hitResult.getBlockPos();
            if (!player.level().getBlockState(hitPos.below()).isSolid()
                    || !player.level().getBlockState(hitPos.below(2)).isSolid()) {
                cleanupSummon(instance, player);
                return;
            }
            Vec3 pos = hitResult.getLocation();

            CompoundTag tag = instance.getOrCreateTag();
            tag.putDouble(NBT_CIRCLE_X, pos.x);
            tag.putDouble(NBT_CIRCLE_Y, pos.y);
            tag.putDouble(NBT_CIRCLE_Z, pos.z);
            tag.putDouble(NBT_SUMMON_MP_COST, summonCost);
            tag.remove(NBT_SUMMON_UUID);
            instance.markDirty();
        } else {
            // 鏃犻€夋嫨 鈫?鎵撳紑 GUI
            var absorbedEntries = SpiritSummonPockets.getAbsorbedEntries(player);
            if (absorbedEntries.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("tensura_tno.spirit_summon.no_absorbed")
                                .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
                return;
            }
            // 鏍煎紡锛?id:bonusEP:hp:aura:magicule,..." 锛堝浐瀹氬€兼潵鑷?EntityExistenceData 鐨?min 鍊硷級
            var parts = new java.util.ArrayList<String>();
            var registry = player.level().registryAccess().registryOrThrow(TensuraCustomData.ENTITY_EXISTENCE);
            for (var entry : absorbedEntries) {
                String eid = entry.getString("id");
                double bep = entry.getDouble("bonus_ep");
                // 浠?EntityExistenceData 鑾峰彇鍥哄畾灞炴€у€?
                int hp = 20, aura = 50, magicule = DEFAULT_SUMMON_MAGICULE; // 榛樿鍊?
                ResourceLocation entryId = ResourceLocation.tryParse(eid);
                var data = entryId == null ? null : registry.get(entryId);
                if (data != null) {
                    hp = data.spiritualHP();
                    aura = data.minAura();
                    magicule = data.minMagicule();
                }
                parts.add(eid + ":" + bep + ":" + hp + ":" + aura + ":" + magicule);
            }
            String csv = String.join(",", parts);
            NetworkManager.sendToPlayer(player, new SpiritSummonPackets.OpenScreenPayload(csv));
        }
    }

    // 鈹€鈹€ onHeld: 姣?tick 椹卞姩鍔ㄧ敾 鈹€鈹€

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (entity.level().isClientSide()) return false;
        if (!(entity instanceof ServerPlayer player)) return false;
        return switch (mode) {
            case 0 -> handleAbsorbHeld(instance, player, heldTicks);
            case 1 -> handleSummonHeld(instance, player, heldTicks);
            default -> false;
        };
    }

    /**
     * Mode 0 鎸変綇閫昏緫锛?
     * Phase 1锛?..CAST_TIME-1 ticks锛夛細榄旀硶闃佃搫鍔涖€?
     * Phase 2锛圕AST_TIME.. ticks锛夛細鐩爣瀹氫綇骞剁紦缂撲笅娌夛紝ANIMATION_TICKS 鍚庡畬鎴愭敹绾炽€?
     */
    private boolean handleAbsorbHeld(ManasSkillInstance instance, ServerPlayer player, int heldTicks) {
        CompoundTag tag = instance.getOrCreateTag();
        if (!tag.hasUUID(NBT_ABSORB_TARGET)) return false;

        UUID targetUUID = tag.getUUID(NBT_ABSORB_TARGET);
        Entity targetEntity = ((ServerLevel) player.level()).getEntity(targetUUID);
        if (!(targetEntity instanceof Mob mob) || !mob.isAlive()) {
            cleanupAbsorb(instance);
            return false;
        }

        Pair<Double, Double> cost = Pair.of(0.0, ABSORB_MP_COST);
        int sinkTick = heldTicks - CAST_TIME; // 璐熸暟 = Phase1锛屸墺0 = Phase2

        // 棣杢ick鍐荤粨鐩爣锛屼娇鍏舵棤娉曠Щ鍔ㄤ笖鍏嶇柅浼ゅ锛堥槻姝㈢獟鎭浜★級
        if (heldTicks == 0) {
            mob.setNoAi(true);
            mob.noPhysics = true;
            mob.setInvulnerable(true);
        }

        // Phase 2锛氬厛绉诲姩鐩爣锛屽啀娓叉煋榄旀硶闃碉紙纭繚榄旀硶闃佃窡闅忕洰鏍囷級
        if (sinkTick >= 0 && sinkTick < ANIMATION_TICKS) {
            mob.setPos(mob.position().subtract(0, mob.getBbHeight() * 1.5F / 39.0, 0));
        }

        // 榄旀硶闃靛缁堣窡闅忕洰鏍囧綋鍓嶄綅缃紝淇濇寔鐩爣鍦ㄩ瓟娉曢樀涓績
        Vec3 mobPos = mob.position();
        MagicCircle.castMagicCircle(NBT_CIRCLE_ABSORB, 3.0F, 30, mobPos,
                MagicCircleVariant.DEMON, false, player, tag, instance, 0, cost);
        player.level().playSound(null, mobPos.x, mobPos.y, mobPos.z,
                (SoundEvent) TensuraSoundEvents.CAST_DARK.get(), SoundSource.PLAYERS, 0.5F, 1.2F);

        if (sinkTick < ANIMATION_TICKS) {
            return true;
        } else {
            // 鍔ㄧ敾瀹屾垚 鈫?鏀剁撼
            completeAbsorb(instance, player, mob);
            return false;
        }
    }

    private void completeAbsorb(ManasSkillInstance instance, ServerPlayer player, Mob mob) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        if (!player.isCreative()) {
            existence.setMagicule(existence.getMagicule() - ABSORB_MP_COST);
            existence.markDirty();
        }

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (entityId != null) {
            SpiritSummonPockets.addAbsorbedEntity(player, entityId.toString());
        }

        // 鍚屾鏀剁撼绉嶇被鏁板埌绉嶆棌瀹炰緥 tag锛屼緵瀹㈡埛绔繘鍖栫晫闈㈣鍙?
        io.github.manasmods.manascore.race.api.RaceAPI.getRaceFrom(player).getRace().ifPresent(raceInstance -> {
            int count = SpiritSummonPockets.getAbsorbedEntities(player).size();
            raceInstance.getOrCreateTag().putInt("absorbedTypesCount", count);
            raceInstance.markDirty();
        });

        mob.discard();

        instance.setCoolDown(ABSORB_COOLDOWN_SECONDS, 0);
        cleanupAbsorb(instance);
        instance.markDirty();
    }

    private void cleanupAbsorb(ManasSkillInstance instance) {
        CompoundTag tag = instance.getOrCreateTag();
        tag.remove(NBT_ABSORB_TARGET);
        tag.remove(NBT_CIRCLE_X);
        tag.remove(NBT_CIRCLE_Y);
        tag.remove(NBT_CIRCLE_Z);
        tag.remove(NBT_CIRCLE_ABSORB);
        instance.markDirty();
    }

    /**
     * Mode 1 鎸変綇閫昏緫锛?
     * Phase 1锛?..CAST_TIME-1 ticks锛夛細榄旀硶闃佃搫鍔涖€?
     * Phase 2锛圕AST_TIME.. ticks锛夛細瀹炰綋浠庡湴搴曞崌璧凤紝ANIMATION_TICKS 鍚庡畬鎴愬彫鍞ゃ€?
     */
    private boolean handleSummonHeld(ManasSkillInstance instance, ServerPlayer player, int heldTicks) {
        CompoundTag tag = instance.getOrCreateTag();
        // 鑻?startSummon 鏈缃綅缃紙鐜╁鍙槸鎵撳紑浜?GUI锛夛紝鍒欓潤榛樿繑鍥?
        if (!tag.contains(NBT_CIRCLE_X)) return false;

        Vec3 circlePos = new Vec3(
                tag.getDouble(NBT_CIRCLE_X),
                tag.getDouble(NBT_CIRCLE_Y),
                tag.getDouble(NBT_CIRCLE_Z));

        // 鍦伴潰妫€娴嬶細涓?SummoningMagic 涓€鑷达紝榄旀硶闃典笅鏂归渶瑕佷袱灞傚疄蹇冩柟鍧?
        BlockPos groundPos = ObjectSelectionHelper.getBlockPos(circlePos);
        if (!player.level().getBlockState(groundPos.below()).isSolid()
                || !player.level().getBlockState(groundPos.below(2)).isSolid()) {
            cleanupSummon(instance, player);
            return false;
        }

        double summonCost = tag.contains(NBT_SUMMON_MP_COST)
                ? tag.getDouble(NBT_SUMMON_MP_COST)
                : getSummonMagiculeCost(player.level(), player.getPersistentData().getString(NBT_PENDING_SUMMON));
        Pair<Double, Double> cost = Pair.of(0.0, summonCost);
        int riseTick = heldTicks - CAST_TIME; // 璐熸暟 = Phase1锛屸墺0 = Phase2

        // 濮嬬粓鏄剧ず榄旀硶闃碉紙寤堕暱瀵垮懡锛?
        MagicCircle.castMagicCircle(NBT_CIRCLE_SUMMON, 3.0F, 30, circlePos,
                MagicCircleVariant.DEMON, false, player, tag, instance, 1, cost);
        player.level().playSound(null, circlePos.x, circlePos.y, circlePos.z,
                (SoundEvent) TensuraSoundEvents.CAST_DARK.get(), SoundSource.PLAYERS, 0.5F, 1.5F);

        if (riseTick < 0) {
            // 鈹€鈹€ Phase 1: 榄旀硶闃佃搫鍔?鈹€鈹€
            return true;
        } else {
            // 鈹€鈹€ Phase 2: 瀹炰綋鍗囪捣鍔ㄧ敾 鈹€鈹€
            if (riseTick == 0) {
                // 棣?tick锛氬湪鍦伴潰浠ヤ笅鍒涘缓瀹炰綋
                createSummonEntity(instance, player, circlePos);
            }

            if (!tag.hasUUID(NBT_SUMMON_UUID)) {
                cleanupSummonAndClearPending(instance, player);
                return false;
            }
            UUID summonUUID = tag.getUUID(NBT_SUMMON_UUID);
            Entity summonEntity = ((ServerLevel) player.level()).getEntity(summonUUID);
            if (!(summonEntity instanceof Mob mob) || !mob.isAlive()) {
                cleanupSummonAndClearPending(instance, player);
                return false;
            }

            if (riseTick < ANIMATION_TICKS) {
                // 姣?tick 鍚戜笂绉诲姩
                mob.setPos(mob.position().add(0, mob.getBbHeight() * 1.5F / 39.0, 0));
                TensuraParticleHelper.addServerParticlesAroundSelf(mob, ParticleTypes.PORTAL, 2.0);
                return true;
            } else {
                // 鍔ㄧ敾瀹屾垚 鈫?鍙敜
                completeSummon(instance, player, mob);
                return false;
            }
        }
    }

    private void createSummonEntity(ManasSkillInstance instance, ServerPlayer player, Vec3 pos) {
        String pendingId = player.getPersistentData().getString(NBT_PENDING_SUMMON);
        if (pendingId.isEmpty()) { cleanupSummonAndClearPending(instance, player); return; }

        ResourceLocation rl = ResourceLocation.tryParse(pendingId);
        if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            cleanupSummonAndClearPending(instance, player);
            return;
        }

        EntityType<?> rawType = BuiltInRegistries.ENTITY_TYPE.get(rl);
        Entity created = rawType.create(player.level());
        if (!(created instanceof Mob mob)) { cleanupSummonAndClearPending(instance, player); return; }

        mob.setNoAi(true);
        mob.noPhysics = true;
        // 鍒濆浣嶇疆鍦ㄥ湴闈互涓嬶紙涓?ISummoning.createSummon 鐩稿悓锛?
        mob.setPos(pos.add(0, -1.5F * mob.getBbHeight(), 0));
        mob.finalizeSpawn((ServerLevelAccessor) player.level(),
                player.level().getCurrentDifficultyAt(player.blockPosition()),
                MobSpawnType.MOB_SUMMONED, (SpawnGroupData) null);
        player.level().addFreshEntity(mob);

        instance.getOrCreateTag().putUUID(NBT_SUMMON_UUID, mob.getUUID());
        instance.markDirty();
    }

    private void completeSummon(ManasSkillInstance instance, ServerPlayer player, Mob mob) {
        CompoundTag tag = instance.getOrCreateTag();
        double summonCost = tag.contains(NBT_SUMMON_MP_COST)
                ? tag.getDouble(NBT_SUMMON_MP_COST)
                : getSummonMagiculeCost(player.level(), player.getPersistentData().getString(NBT_PENDING_SUMMON));
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        if (!player.isCreative() && existence.getMagicule() < summonCost) {
            player.displayClientMessage(
                    Component.translatable("tensura.skill.lack_magicule")
                            .setStyle(Style.EMPTY.withColor(0xFF5555)), true);
            mob.discard();
            cleanupSummonAndClearPending(instance, player);
            return;
        }
        if (!player.isCreative()) {
            existence.setMagicule(existence.getMagicule() - summonCost);
            existence.markDirty();
        }

        // 瑙ｉ櫎鍐荤粨锛岀粦瀹氫粠灞炲叧绯?
        mob.noPhysics = false;
        mob.setNoAi(false);

        // 椹湇閫昏緫锛堜笌 EdoTenseiSkill.addAdditionalSummonData 涓€鑷达級
        if (mob instanceof io.github.manasmods.tensura.entity.template.subclass.ISubordinate sub) {
            sub.tame(player);
        } else if (mob instanceof net.minecraft.world.entity.TamableAnimal ta) {
            ta.setOwnerUUID(player.getUUID());
            ta.setTame(true, true);
        } else if (mob instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) {
            horse.tameWithName(player);
        }
        if (mob instanceof io.github.manasmods.tensura.entity.template.TensuraRideableEntity rideable) {
            rideable.setSaddled(true);
        }
        mob.skipDropExperience();

        // 闃叉琚嚮鏉€鏃惰鍏?stextras 鍑绘潃浠诲姟锛坰textras 妫€鏌?permanentOwner / stextras_had_owner 鏍囪锛?
        mob.addTag("stextras_had_owner");

        IExistence summonEx = TensuraStorages.getExistenceFrom(mob);
        SpiritSummonEntityHelper.markSpiritSummon(mob, player);
        summonEx.setSummonedSecond(FoxSpiritSummonBonus.getSummonDurationSeconds(player));

        // 鍥哄畾 EP锛氱敤 EntityExistenceData 鐨?min 鍊兼浛浠ｉ殢鏈哄€硷紝纭繚姣忔鍙敜灞炴€т竴鑷?
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (mobId != null) {
            var registry = player.level().registryAccess().registryOrThrow(
                    TensuraCustomData.ENTITY_EXISTENCE);
            var data = registry.get(mobId);
            if (data != null) {
                int fixedAura = data.minAura();
                int fixedMagicule = data.minMagicule();
                // 瑕嗙洊闅忔満鍊间负鍥哄畾鍊?
                AttributeInstance auraAttr = mob.getAttribute(io.github.manasmods.tensura.registry.attribute.TensuraAttributes.MAX_AURA);
                if (auraAttr != null) {
                    auraAttr.setBaseValue(fixedAura);
                    summonEx.setAura(fixedAura);
                }
                AttributeInstance mpAttr = mob.getAttribute(io.github.manasmods.tensura.registry.attribute.TensuraAttributes.MAX_MAGICULE);
                if (mpAttr != null) {
                    mpAttr.setBaseValue(fixedMagicule);
                    summonEx.setMagicule(fixedMagicule);
                }
            }

            // 娉ㄥ叆鍙ｈ涓瀹炰綋绫诲瀷鐨勭疮璁?EP 鍔犳垚
            double bonusEP = SpiritSummonPockets.getBonusEP(player, mobId.toString());
            if (bonusEP > 0.0) {
                // 璁板綍澧為暱鍓嶇殑鍩虹 EP锛岀敤浜庣瓑姣斿鍔?HP
                double baseEP = EnergyHelper.getBaseMaxEP(mob);
                double bonusHalf = bonusEP / 2.0;
                EnergyHelper.gainAura(mob, bonusHalf, EnergyHelper.GainType.MAX);
                EnergyHelper.gainMagicule(mob, bonusHalf, EnergyHelper.GainType.MAX);
                // EP 澧為暱 鈫?HP 绛夋瘮澧為暱锛坆onusEP 绛変簬 baseEP 鏃?HP 缈诲€嶏級
                if (baseEP > 0.0) {
                    AttributeInstance hpAttr = mob.getAttribute(
                            net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (hpAttr != null) {
                        double hpBoost = hpAttr.getBaseValue() * (bonusEP / baseEP);
                        hpAttr.setBaseValue(hpAttr.getBaseValue() + hpBoost);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }
            }
        }

        summonEx.markDirty();

        // 娓呴櫎寰呭彫鍞ら€夋嫨锛堜絾涓嶆竻闄ゅ彛琚嬩腑鐨勫疄浣撶被鍨嬶紝鍏佽閲嶅鍙敜锛?
        String pendingId = player.getPersistentData().getString(NBT_PENDING_SUMMON);
        if (!pendingId.isEmpty()) {
            player.getPersistentData().remove(NBT_PENDING_SUMMON);
        }

        player.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                (SoundEvent) TensuraSoundEvents.CAST_DARK.get(), SoundSource.PLAYERS, 1.0F, 1.5F);
        TensuraParticleHelper.addServerParticlesAroundSelf(mob, ParticleTypes.FLASH, 3.0);
        TensuraParticleHelper.addServerParticlesAroundSelf(mob, ParticleTypes.FLASH, 2.0);

        instance.setCoolDown(SUMMON_COOLDOWN_SECONDS, 1);
        cleanupSummon(instance, player);
        instance.markDirty();
    }

    private void cleanupSummon(ManasSkillInstance instance, ServerPlayer player) {
        CompoundTag tag = instance.getOrCreateTag();
        tag.remove(NBT_SUMMON_UUID);
        tag.remove(NBT_CIRCLE_X);
        tag.remove(NBT_CIRCLE_Y);
        tag.remove(NBT_CIRCLE_Z);
        tag.remove(NBT_CIRCLE_SUMMON);
        tag.remove(NBT_SUMMON_MP_COST);
        instance.markDirty();
    }

    private void cleanupSummonAndClearPending(ManasSkillInstance instance, ServerPlayer player) {
        player.getPersistentData().remove(NBT_PENDING_SUMMON);
        cleanupSummon(instance, player);
    }

    // 鈹€鈹€ onRelease: 鎻愬墠閲婃斁閿垯鍙栨秷 鈹€鈹€

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity,
                          int heldTicks, int keyNumber, int mode) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;
        switch (mode) {
            case 0 -> cancelAbsorb(instance, player);
            case 1 -> cancelSummon(instance, player);
        }
    }

    private void cancelAbsorb(ManasSkillInstance instance, ServerPlayer player) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(NBT_ABSORB_TARGET)) return;

        UUID targetUUID = tag.getUUID(NBT_ABSORB_TARGET);
        Entity targetEntity = ((ServerLevel) player.level()).getEntity(targetUUID);
        if (targetEntity instanceof Mob mob) {
            // 鎭㈠鍒板師濮嬩綅缃苟瑙ｉ櫎鍐荤粨鍜屾棤鏁?
            mob.teleportTo(
                    tag.getDouble(NBT_CIRCLE_X),
                    tag.getDouble(NBT_CIRCLE_Y),
                    tag.getDouble(NBT_CIRCLE_Z));
            mob.setNoAi(false);
            mob.noPhysics = false;
            mob.setInvulnerable(false);
        }
        cleanupAbsorb(instance);
    }

    private void cancelSummon(ManasSkillInstance instance, ServerPlayer player) {
        CompoundTag tag = instance.getTag();
        if (tag == null) { cleanupSummon(instance, player); return; }

        if (tag.hasUUID(NBT_SUMMON_UUID)) {
            UUID summonUUID = tag.getUUID(NBT_SUMMON_UUID);
            Entity summonEntity = ((ServerLevel) player.level()).getEntity(summonUUID);
            if (summonEntity instanceof Mob mob && mob.isNoAi()) {
                mob.discard();
            }
        }
        // 娉ㄦ剰锛歂BT_PENDING_SUMMON 涓嶆竻闄わ紝鐜╁鍙互鍐嶆鎸変綇閲嶈瘯
        cleanupSummon(instance, player);
    }

    // 鈹€鈹€ 涓ユ牸灏勭嚎妫€娴嬶紙鏃犲厹搴曢€昏緫锛夆攢鈹€

    private @Nullable Mob getTargetMobStrictly(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(ABSORB_RANGE));
        AABB searchBox = player.getBoundingBox()
                .expandTowards(lookVec.scale(ABSORB_RANGE)).inflate(1.0);

        double closestDist = Double.MAX_VALUE;
        Mob hitMob = null;
        for (Entity entity : player.level().getEntities(player, searchBox)) {
            if (!(entity instanceof Mob mob)) continue;
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = entityBox.clip(eyePos, endPos);
            if (hit.isPresent()) {
                double dist = eyePos.distanceTo(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    hitMob = mob;
                }
            }
        }
        // 澶氭ā鍧楃粨鏋勭敓鐗╋紙Evil Centipede / Tempest Serpent 绛夛級锛氬懡涓韩浣撻儴鍒嗘椂鏇挎崲涓哄ご閮ㄤ富瀹炰綋
        if (hitMob instanceof ILivingPartEntity part) {
            Entity head = part.getHead();
            if (head instanceof Mob headMob) {
                hitMob = headMob;
            }
        }
        return hitMob; // 灏勭嚎鏈懡涓疄浣撳垯杩斿洖 null锛堥潤榛橈紝涓嶆彁绀猴級
    }

    /**
     * 浠庢敹绾冲彛琚嬮€夋嫨寰呭彫鍞ゅ疄浣擄紙渚?{@link SpiritSummonPackets} 璋冪敤锛夈€?
     * 閫夋嫨鍚庣帺瀹堕暱鎸夋妧鑳介敭鍗冲彲瑙﹀彂榄旀硶闃靛彫鍞ゆ祦绋嬨€?
     */
    public static void summonFromPocket(ServerPlayer player, String entityId) {
        player.getPersistentData().putString(NBT_PENDING_SUMMON, entityId);
    }

    private static double getSummonMagiculeCost(Level level, String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) return DEFAULT_SUMMON_MAGICULE * SUMMON_MAGICULE_COST_RATIO;
        var registry = level.registryAccess().registryOrThrow(TensuraCustomData.ENTITY_EXISTENCE);
        var data = registry.get(id);
        int magicule = data != null ? data.minMagicule() : DEFAULT_SUMMON_MAGICULE;
        return magicule * SUMMON_MAGICULE_COST_RATIO;
    }

    /**
     * 缁熻鐜╁褰撳墠閫氳繃鐏典箣鍙敜瀛樻椿鐨勫彫鍞ょ墿鏁伴噺銆?
     * <p>
     * 閬嶅巻 ServerLevel 涓墍鏈夊疄浣擄紝绛涢€?IExistence 涓?summoner == playerUUID
     * 涓?summonedAbility == SPIRIT_SUMMON 鐨?Mob銆?
     */
    private int countActiveSummons(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        int count = 0;
        for (Entity entity : ((ServerLevel) player.level()).getAllEntities()) {
            if (!(entity instanceof Mob mob)) continue;
            try {
                IExistence ex = TensuraStorages.getExistenceFrom(mob);
                if (playerUUID.equals(ex.getSummoner())
                        && ex.getSummonedAbility() != null
                        && ex.getSummonedAbility().getSkill() == TensuraTNOSkills.SPIRIT_SUMMON.get()) {
                    count++;
                }
            } catch (Exception ignored) {
                // 闈?Tensura 瀹炰綋锛岃烦杩?
            }
        }
        return count;
    }

    /**
     * 绉婚櫎鐜╁鎵€鏈夌伒涔嬪彫鍞ょ殑娲昏穬鍙敜鐗╋紙鐢ㄤ簬閲嶇疆鍗锋竻鐞嗭級銆?
     */
    public static void removeAllActiveSummons(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        List<Mob> toRemove = new java.util.ArrayList<>();
        for (Entity entity : ((ServerLevel) player.level()).getAllEntities()) {
            if (!(entity instanceof Mob mob)) continue;
            try {
                IExistence ex = TensuraStorages.getExistenceFrom(mob);
                if (playerUUID.equals(ex.getSummoner())
                        && ex.getSummonedAbility() != null
                        && ex.getSummonedAbility().getSkill() == TensuraTNOSkills.SPIRIT_SUMMON.get()) {
                    toRemove.add(mob);
                }
            } catch (Exception ignored) {}
        }
        for (Mob mob : toRemove) {
            mob.discard();
        }
    }

    // 鈹€鈹€ 鏀剁撼鍙ｈ锛堢帺瀹?NBT 瀛樺偍宸ュ叿绫伙級鈹€鈹€

    public static class SpiritSummonPockets {
        private static final String TAG_ROOT     = "tensura_tno_spirit_summon";
        private static final String TAG_ABSORBED = "absorbed_entities";
        private static final String TAG_ENTITY_ID = "id";
        private static final String TAG_BONUS_EP  = "bonus_ep";

        // 鈹€鈹€ 璇诲彇锛氳繑鍥炲疄浣揑D鍒楄〃锛堝悜鍚庡吋瀹癸級 鈹€鈹€

        public static java.util.List<String> getAbsorbedEntities(ServerPlayer player) {
            CompoundTag root = ensureMigrated(player);
            var list = new java.util.ArrayList<String>();
            var tagList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var t : tagList) {
                if (t instanceof CompoundTag ct) {
                    list.add(ct.getString(TAG_ENTITY_ID));
                }
            }
            return list;
        }

        // 鈹€鈹€ 璇诲彇锛氳繑鍥炲寘鍚?bonusEP 鐨勫畬鏁存潯鐩垪琛?鈹€鈹€

        public static java.util.List<CompoundTag> getAbsorbedEntries(ServerPlayer player) {
            CompoundTag root = ensureMigrated(player);
            var list = new java.util.ArrayList<CompoundTag>();
            var tagList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var t : tagList) {
                if (t instanceof CompoundTag ct) {
                    list.add(ct);
                }
            }
            return list;
        }

        // 鈹€鈹€ 璇诲彇鎸囧畾瀹炰綋绫诲瀷鐨勭疮璁P鍔犳垚 鈹€鈹€

        public static double getBonusEP(ServerPlayer player, String entityId) {
            CompoundTag root = ensureMigrated(player);
            var tagList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var t : tagList) {
                if (t instanceof CompoundTag ct && ct.getString(TAG_ENTITY_ID).equals(entityId)) {
                    return ct.getDouble(TAG_BONUS_EP);
                }
            }
            return 0.0;
        }

        public static boolean hasAbsorbedEntity(ServerPlayer player, String entityId) {
            CompoundTag root = ensureMigrated(player);
            var tagList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var t : tagList) {
                if (t instanceof CompoundTag ct && ct.getString(TAG_ENTITY_ID).equals(entityId)) {
                    return true;
                }
            }
            return false;
        }

        // 鈹€鈹€ 鍐欏叆锛氭柊澧炴敹绾冲疄浣擄紙bonus_ep 鍒濆0锛?鈹€鈹€

        public static void addAbsorbedEntity(ServerPlayer player, String entityId) {
            CompoundTag root = ensureMigrated(player);
            var tagList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var t : tagList) {
                if (t instanceof CompoundTag ct && ct.getString(TAG_ENTITY_ID).equals(entityId)) {
                    // 宸插瓨鍦紝涓嶉噸澶嶆坊鍔?
                    return;
                }
            }
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_ENTITY_ID, entityId);
            entry.putDouble(TAG_BONUS_EP, 0.0);
            tagList.add(entry);
            root.put(TAG_ABSORBED, tagList);
            player.getPersistentData().put(TAG_ROOT, root);
        }

        // 鈹€鈹€ 鍐欏叆锛氱疮鍔燛P鍒版寚瀹氬疄浣撶被鍨嬬殑 bonus_ep 鈹€鈹€

        public static void addBonusEP(ServerPlayer player, String entityId, double amount) {
            CompoundTag root = ensureMigrated(player);
            var tagList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var t : tagList) {
                if (t instanceof CompoundTag ct && ct.getString(TAG_ENTITY_ID).equals(entityId)) {
                    ct.putDouble(TAG_BONUS_EP, ct.getDouble(TAG_BONUS_EP) + amount);
                    root.put(TAG_ABSORBED, tagList);
                    player.getPersistentData().put(TAG_ROOT, root);
                    return;
                }
            }
            // 涓嶅湪鍒楄〃涓垯蹇界暐锛堢悊璁轰笂涓嶄細鍙戠敓锛?
        }

        // 鈹€鈹€ 鍐欏叆锛氱洿鎺ヨ缃寚瀹氬疄浣撶被鍨嬬殑 bonus_ep 鈹€鈹€

        public static void setBonusEP(ServerPlayer player, String entityId, double value) {
            CompoundTag root = ensureMigrated(player);
            var tagList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var t : tagList) {
                if (t instanceof CompoundTag ct && ct.getString(TAG_ENTITY_ID).equals(entityId)) {
                    ct.putDouble(TAG_BONUS_EP, value);
                    root.put(TAG_ABSORBED, tagList);
                    player.getPersistentData().put(TAG_ROOT, root);
                    return;
                }
            }
        }

        // 鈹€鈹€ 鍒犻櫎锛氱Щ闄ゆ寚瀹氬疄浣撶被鍨?鈹€鈹€

        public static void removeAbsorbedEntity(ServerPlayer player, String entityId) {
            CompoundTag root = ensureMigrated(player);
            var tagList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            var newList = new net.minecraft.nbt.ListTag();
            for (var t : tagList) {
                if (t instanceof CompoundTag ct && !ct.getString(TAG_ENTITY_ID).equals(entityId)) {
                    newList.add(ct);
                }
            }
            root.put(TAG_ABSORBED, newList);
            player.getPersistentData().put(TAG_ROOT, root);
        }

        // 鈹€鈹€ 娓呯┖锛氶噸缃椂娓呴櫎鎵€鏈夊彛琚嬫暟鎹?鈹€鈹€

        public static void clearAll(ServerPlayer player) {
            player.getPersistentData().remove(TAG_ROOT);
        }

        // 鈹€鈹€ 鏁版嵁杩佺Щ锛氭棫鏍煎紡 ListTag<String> 鈫?鏂版牸寮?ListTag<CompoundTag> 鈹€鈹€

        private static CompoundTag ensureMigrated(ServerPlayer player) {
            CompoundTag root = player.getPersistentData().getCompound(TAG_ROOT);
            if (!root.contains(TAG_ABSORBED)) return root;

            // 灏濊瘯浠?CompoundTag 绫诲瀷璇诲彇锛涘鏋滄棫鏍煎紡鏄?StringTag 鍒欒鍙栦负绌哄垪琛?
            var compoundList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_COMPOUND);
            if (!compoundList.isEmpty()) return root; // 宸茬粡鏄柊鏍煎紡

            // 灏濊瘯浠?StringTag 绫诲瀷璇诲彇锛堟棫鏍煎紡锛?
            var stringList = root.getList(TAG_ABSORBED, net.minecraft.nbt.Tag.TAG_STRING);
            if (stringList.isEmpty()) return root; // 绌哄垪琛ㄦ棤闇€杩佺Щ

            // 杩佺Щ锛歋tringTag 鈫?CompoundTag
            var newList = new net.minecraft.nbt.ListTag();
            for (var t : stringList) {
                CompoundTag entry = new CompoundTag();
                entry.putString(TAG_ENTITY_ID, t.getAsString());
                entry.putDouble(TAG_BONUS_EP, 0.0);
                newList.add(entry);
            }
            root.put(TAG_ABSORBED, newList);
            player.getPersistentData().put(TAG_ROOT, root);
            return root;
        }
    }
}

