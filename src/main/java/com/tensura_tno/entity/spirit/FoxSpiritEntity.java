package com.tensura_tno.entity.spirit;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.tensura_tno.compat.BetterSubsFoxCompat;
import com.tensura_tno.race.fox_spirit.FoxSpiritSummonBonus;

import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * 契约小狐灵 — 由"契约小狐"内在技能召唤的协助战斗的狐狸单位。
 *
 * <p><b>关键特性</b>：
 * <ul>
 *   <li>视觉上沿用原版狐狸模型（通过 {@code com.tensura_tno.client.renderer.FoxSpiritRenderer}
 *       使用 vanilla {@code FoxModel} 渲染）。</li>
 *   <li>独立 UUID（每次召唤都生成新的实体，UUID 由 Minecraft 自动分配）。</li>
 *   <li>属性注入：通过
 *       {@link io.github.manasmods.tensura.storage.ep.IExistence#setSummoner(UUID)}
 *       绑定到玩家，使 Tensura 的从属系统将其识别为玩家的下属，自动进入"队友判定"。</li>
 *   <li>不会自然消失 ({@link #removeWhenFarAway} 始终返回 false)。</li>
 *   <li>跟随玩家、协助攻击玩家攻击的目标 / 攻击玩家的目标。</li>
 * </ul>
 *
 * <p><b>属性同步</b>：每秒（{@link #ATTACK_SYNC_INTERVAL} 个 tick）从玩家身上拉取一次
 * {@link Attributes#ATTACK_DAMAGE} 的当前值同步到自己（保持"攻击力和玩家相同"）。
 * EP/MP/SHP 的最大值由召唤逻辑一次性写入（玩家的 50%），运行期间不再自动同步。
 */
public class FoxSpiritEntity extends Fox implements OwnableEntity {

    /** 召唤者 UUID 的 SynchedEntityData 槽，便于客户端识别归属。 */
    private static final EntityDataAccessor<Optional<UUID>> DATA_SUMMONER_UUID =
            SynchedEntityData.defineId(FoxSpiritEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 命令模式：0 = 跟随（走）, 1 = 游荡, 2 = 待命（停）。语义对齐 {@code ISubordinate} 的命令循环。 */
    public static final int CMD_FOLLOW = 0;
    public static final int CMD_WANDER = 1;
    public static final int CMD_STAY = 2;

    private static final EntityDataAccessor<Integer> DATA_OWNER_COMMAND =
            SynchedEntityData.defineId(FoxSpiritEntity.class, EntityDataSerializers.INT);

    /** 攻击力同步间隔（tick）—— 1 秒。 */
    private static final int ATTACK_SYNC_INTERVAL = 20;

    /** NBT 键 —— 召唤者 UUID。 */
    public static final String TAG_SUMMONER_UUID = "TnoSummonerUUID";
    /** NBT 键 —— 当前命令模式。 */
    public static final String TAG_COMMAND = "TnoCommand";
    /** NBT 键 —— 游荡中心方块。 */
    public static final String TAG_WANDER_POS = "TnoWanderPos";

    private int attackSyncCounter;
    private @Nullable BlockPos wanderCenter;

    public FoxSpiritEntity(EntityType<? extends Fox> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // 路径规避：避免狐狸跳火/水/熔岩的怪异路径
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_OTHER, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
    }

    /**
     * 实体属性。继承原版 Fox 的属性，再叠加 Tensura 的全局属性会通过
     * {@code EntityAttributeModificationEvent} 自动注入到所有 LivingEntity 上，所以这里
     * 不需要手动添加。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Fox.createAttributes()
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SUMMONER_UUID, Optional.empty());
        builder.define(DATA_OWNER_COMMAND, CMD_FOLLOW);
    }

    @Override
    protected void registerGoals() {
        // ⚠ 必须先调用 super.registerGoals()，原因：
        // vanilla Fox 的 landTargetGoal / turtleEggTargetGoal / fishTargetGoal 三个字段
        // 是在 super.registerGoals() 里被 new 出来的。Fox 内部的（private）setTargetGoals()
        // 方法会被 vanilla {@code Fox.readAdditionalSaveData} 在加载存档 / 区块重载 / 跨维度时
        // 调用，把这三个字段加进 targetSelector —— 字段为 null 时会触发
        // {@link net.minecraft.world.entity.ai.goal.WrappedGoal#hashCode} NPE 崩服。
        // 由于这是 private 方法我们无法 override，必须保证字段被正确初始化。
        super.registerGoals();
        // 立刻清空 super 添加的所有狐狸默认 AI（睡觉、繁殖、躲避玩家、偷蛋等都不适合战斗召唤物），
        // 然后用我们的最小集合 + 自定义跟随/护主目标重新填充。
        this.goalSelector.removeAllGoals(g -> true);
        this.targetSelector.removeAllGoals(g -> true);

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3D, true));
        this.goalSelector.addGoal(2, new FollowSummonerGoal(this, 1.0D, 6.0F, 2.5F));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new SummonerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new SummonerHurtTargetGoal(this));
        // HurtByTargetGoal 即使 STAY 也允许，让狐灵能自卫
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        // 不再主动索敌：只攻击伤害主人的、被玩家指使的、或攻击自身的目标
    }

    // ─── 召唤者绑定 ───

    public Optional<UUID> getSummonerUUID() {
        return this.entityData.get(DATA_SUMMONER_UUID);
    }

    public void setSummonerUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_SUMMONER_UUID, Optional.ofNullable(uuid));
        IExistence existence = TensuraStorages.getExistenceFrom(this);
        existence.setSummoner(uuid);
        existence.markDirty();
    }

    // ─── OwnableEntity（bett ExpandedSkillStorage 等按此识别“有主人”的生物）───

    @Override
    public @Nullable UUID getOwnerUUID() {
        return this.getSummonerUUID().orElse(null);
    }

    public @Nullable LivingEntity getSummoner() {
        return this.getSummonerUUID().map(uuid -> {
            if (this.level() instanceof ServerLevel sl) {
                Entity e = sl.getEntity(uuid);
                return e instanceof LivingEntity le ? le : null;
            }
            Player p = this.level().getPlayerByUUID(uuid);
            return p;
        }).orElse(null);
    }

    private boolean isAlliedWithSummoner(LivingEntity target) {
        UUID summonerId = this.getSummonerUUID().orElse(null);
        if (summonerId == null) return false;
        if (target.getUUID().equals(summonerId)) return true;
        IExistence existence = TensuraStorages.getExistenceFrom(target);
        return summonerId.equals(existence.getSummoner());
    }

    // ─── 命令模式（走 / 停 / 游荡） ───

    public int getOwnerCommand() {
        return this.entityData.get(DATA_OWNER_COMMAND);
    }

    public void setOwnerCommand(int command) {
        this.entityData.set(DATA_OWNER_COMMAND, command);
    }

    public boolean isFollowMode() { return getOwnerCommand() == CMD_FOLLOW; }

    public boolean isWanderMode() { return getOwnerCommand() == CMD_WANDER; }

    public boolean isStayMode()   { return getOwnerCommand() == CMD_STAY; }

    public @Nullable BlockPos getWanderCenter() { return this.wanderCenter; }

    public void setWanderCenter(@Nullable BlockPos pos) { this.wanderCenter = pos; }

    /**
     * 召唤者右键交互时切换命令：跟随 → 游荡 → 待命 → 跟随 …
     * <p>语义/反馈语对齐主模组 {@code ISubordinate.cycleCommands}。
     */
    public void cycleCommandFor(Player player) {
        if (this.level().isClientSide()) return;
        int next = (getOwnerCommand() + 1) % 3;
        setOwnerCommand(next);
        if (next == CMD_WANDER) {
            // 把当前玩家脚下的方块作为游荡中心
            this.wanderCenter = player.getOnPos().above();
            this.getNavigation().stop();
            this.setTarget(null);
        } else if (next == CMD_STAY) {
            this.getNavigation().stop();
            this.setTarget(null);
        } else {
            // CMD_FOLLOW 不需要额外清理
        }

        MutableComponent message = switch (next) {
            case CMD_WANDER -> Component.translatable(
                    "tensura.message.pet.wander", this.getDisplayName());
            case CMD_STAY -> Component.translatable(
                    "tensura.message.pet.stay", this.getDisplayName());
            default -> Component.translatable(
                    "tensura.message.pet.follow", this.getDisplayName());
        };
        player.displayClientMessage(message.setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)), true);
    }

    /**
     * 召唤者交互：
     * <ul>
     *   <li>潜行 + 空手：若安装了 tensura_better_subs，打开从属主界面（与思念传达第三模式一致）；</li>
     *   <li>非潜行 + 空手：切换跟随 / 游荡 / 待命；</li>
     *   <li>手持物品：交给原版 Fox（甜浆果等）；潜行时 bett 仍可通过全局交互事件消耗蜘蛛眼等。</li>
     * </ul>
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        UUID summoner = getSummonerUUID().orElse(null);
        if (summoner != null && player.getUUID().equals(summoner)) {
            ItemStack stack = player.getItemInHand(hand);
            if (!player.level().isClientSide()
                    && player instanceof ServerPlayer serverPlayer
                    && player.isShiftKeyDown()
                    && stack.isEmpty()
                    && player.distanceTo(this) < 8.0F
                    && BetterSubsFoxCompat.tryOpenSubordinateMainMenu(serverPlayer, this.getId())) {
                return InteractionResult.sidedSuccess(player.level().isClientSide());
            }
            if (stack.isEmpty()) {
                cycleCommandFor(player);
                return InteractionResult.sidedSuccess(player.level().isClientSide());
            }
        }
        return super.mobInteract(player, hand);
    }

    // ─── 生存策略 ───

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        // 自家人：召唤者 + 召唤者的其他从属
        UUID summonerId = this.getSummonerUUID().orElse(null);
        if (summonerId == null) return super.isAlliedTo(entity);
        if (entity.getUUID().equals(summonerId)) return true;
        if (entity instanceof LivingEntity le) {
            IExistence existence = TensuraStorages.getExistenceFrom(le);
            if (summonerId.equals(existence.getSummoner())) return true;
        }
        return super.isAlliedTo(entity);
    }

    /**
     * 召唤者及其从属对小狐灵造成的伤害一律忽略——不论近战、弹射物、技能、AOE、爆炸都拦截。
     * 检查路径：
     * <ol>
     *   <li>{@link net.minecraft.world.damagesource.DamageSource#getEntity()}
     *       —— 因果链顶端实体（玩家本人、射箭的玩家、释放技能的玩家）；
     *       Tensura 大多数技能都用 {@code createSource(instance, attacker, ...)}
     *       构造，{@code attacker = 玩家}，会出现在 getEntity()。</li>
     *   <li>{@link net.minecraft.world.damagesource.DamageSource#getDirectEntity()}
     *       —— 直接致害物（箭矢、火球、其他召唤生物本体）。</li>
     * </ol>
     * 对每个非空实体，再额外用 {@link SubordinateHelper#getSubordinateOwnerUUID}
     * 反查归属——这样玩家的"其他召唤物 / Edo Tensei 召唤 / 同主人下的另一只契约小狐"
     * 都不会误伤本狐灵。
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        UUID summonerId = this.getSummonerUUID().orElse(null);
        if (summonerId != null) {
            if (causedBySummonerOrSubordinate(source.getEntity(), summonerId)) return false;
            if (causedBySummonerOrSubordinate(source.getDirectEntity(), summonerId)) return false;
        }
        return super.hurt(source, amount);
    }

    /** 该实体是否就是召唤者本人，或归属于召唤者的某个从属/召唤物。 */
    private static boolean causedBySummonerOrSubordinate(@Nullable Entity entity, UUID summonerId) {
        if (entity == null) return false;
        if (summonerId.equals(entity.getUUID())) return true;
        if (entity instanceof LivingEntity le) {
            UUID owner = SubordinateHelper.getSubordinateOwnerUUID(le);
            return summonerId.equals(owner);
        }
        return false;
    }

    // ─── 召唤物亲和：免疫外部控制 ───

    /**
     * 免疫精神控制效果 — 狐灵不会被 MindControl 效果影响。
     * <p>这是"召唤物亲和"的核心：自身召唤物不会被其他单位控制。
     */
    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effectInstance) {
        // 阻挡 Tensura 的精神控制效果
        if (effectInstance.getEffect().equals(TensuraMobEffects.MIND_CONTROL)) {
            return false;
        }
        return super.canBeAffected(effectInstance);
    }

    /**
     * 防止被转向攻击自己的主人或主人的盟友。
     * <p>如果目标属于主人或主人的从属，则拒绝设置该目标。
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null) {
            UUID summonerId = this.getSummonerUUID().orElse(null);
            if (summonerId != null) {
                // 不允许以召唤者本身为目标
                if (target.getUUID().equals(summonerId)) return;
                // 不允许以召唤者的其他从属为目标
                if (target instanceof LivingEntity) {
                    UUID targetOwner = SubordinateHelper.getSubordinateOwnerUUID(target);
                    if (summonerId.equals(targetOwner)) return;
                }
                // 不允许以与召唤者同队的实体为目标
                if (target instanceof Player player && this.isAlliedTo(player)) return;
            }
        }
        super.setTarget(target);
    }

    /** 不允许被拴绳绑定。 */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** 不掉经验值。 */
    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
    }

    // ─── 行为 tick ───

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) return;

        if (++this.attackSyncCounter >= ATTACK_SYNC_INTERVAL) {
            this.attackSyncCounter = 0;
            this.syncAttackFromSummoner();
            this.syncMaxStatsFromSummoner();
        }

        // STAY 模式：没有自卫目标时，强制停下，不被随机漫步目标拉走
        if (isStayMode() && this.getTarget() == null && !this.getNavigation().isDone()) {
            this.getNavigation().stop();
        }
    }

    /**
     * 把召唤者的 ATTACK_DAMAGE 拉过来，并直接将狐灵种族加成内嵌到 base value。
     * <p>bonus 内嵌到 base 而不是外加 AttributeModifier，避免收回/再召唤时显示不一致。
     */
    private void syncAttackFromSummoner() {
        LivingEntity summoner = this.getSummoner();
        if (!(summoner instanceof Player player)) return;
        AttributeInstance ownAtk = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (ownAtk == null) return;

        float bonus = FoxSpiritSummonBonus.getBonusPercentage(player);
        double targetAtk = summoner.getAttributeValue(Attributes.ATTACK_DAMAGE) * (1.0 + bonus);

        if (Math.abs(ownAtk.getBaseValue() - targetAtk) > 0.001) {
            ownAtk.setBaseValue(targetAtk);
        }
    }

    /**
     * 定期同步最大生命、精神生命、EP、MP 的 base value。
     * <p>HP / SHP 包含狐灵种族加成（直接内嵌到 base，不使用外加 Modifier），
     * 保证召唤中 / 召唤后显示的最大值始终一致。
     * <p>双向同步：种族进化后数值会增大；如果当前狐灵已满血 / 满能量，同步后也保持满状态。
     */
    private void syncMaxStatsFromSummoner() {
        LivingEntity summoner = this.getSummoner();
        if (!(summoner instanceof Player player)) return;

        float bonus = FoxSpiritSummonBonus.getBonusPercentage(player);

        // ── HP 上限同步（包含加成 + EP 转化额外生命） ──
        AttributeInstance ownHp = this.getAttribute(Attributes.MAX_HEALTH);
        if (ownHp != null) {
            double baseHp = summoner.getMaxHealth() * 0.5;
            // 契约小狐自身积累的 EP（击杀获得的额外 EP）转化为额外生命值
            // 计算方式：foxCurrentMaxEP - summonerEP*0.5 = 击杀累积的额外EP
            // 每 1000 额外 EP = +1 最大生命值
            double summonerBaseEP = (EnergyHelper.getBaseMaxAura(summoner) + EnergyHelper.getBaseMaxMagicule(summoner)) * 0.5;
            double foxCurrentEP = EnergyHelper.getBaseMaxAura(this) + EnergyHelper.getBaseMaxMagicule(this);
            double extraEP = Math.max(0, foxCurrentEP - summonerBaseEP);
            double epHealthBonus = extraEP / 1000.0;
            double targetMaxHp = (baseHp + epHealthBonus) * (1.0 + bonus);
            double oldBase = ownHp.getBaseValue();
            if (Math.abs(oldBase - targetMaxHp) > 0.001) {
                ownHp.setBaseValue(targetMaxHp);
                // 如果之前是满血，同步后也满血
                if (this.getHealth() >= oldBase - 0.5F) {
                    this.setHealth(this.getMaxHealth());
                }
            }
        }

        // ── SHP 上限同步（包含加成） ──
        AttributeInstance ownShp = this.getAttribute(TensuraAttributes.MAX_SPIRITUAL_HEALTH);
        AttributeInstance playerShp = summoner.getAttribute(TensuraAttributes.MAX_SPIRITUAL_HEALTH);
        if (ownShp != null && playerShp != null) {
            double targetShp = playerShp.getValue() * 0.5 * (1.0 + bonus);
            double oldShpBase = ownShp.getBaseValue();
            if (Math.abs(oldShpBase - targetShp) > 0.001) {
                ownShp.setBaseValue(targetShp);
                IExistence foxEx = TensuraStorages.getExistenceFrom(this);
                if (foxEx.getSpiritualHealth() >= oldShpBase - 0.5) {
                    foxEx.setSpiritualHealth(targetShp);
                    foxEx.markDirty();
                }
            }
        }

        // ── EP 上限同步（EP/MP 不加加成，仅随玩家增长） ──
        double targetMaxAura = EnergyHelper.getBaseMaxAura(summoner) * 0.5;
        double oldMaxAura = EnergyHelper.getBaseMaxAura(this);
        if (targetMaxAura > oldMaxAura + 0.001) {
            EnergyHelper.setMaxAura(this, targetMaxAura);
            IExistence foxEx = TensuraStorages.getExistenceFrom(this);
            if (foxEx.getAura() >= oldMaxAura - 0.5) {
                foxEx.setAura(targetMaxAura);
                foxEx.markDirty();
            }
        }

        // ── MP 上限同步 ──
        double targetMaxMagic = EnergyHelper.getBaseMaxMagicule(summoner) * 0.5;
        double oldMaxMagic = EnergyHelper.getBaseMaxMagicule(this);
        if (targetMaxMagic > oldMaxMagic + 0.001) {
            EnergyHelper.setMaxMagicule(this, targetMaxMagic);
            IExistence foxEx = TensuraStorages.getExistenceFrom(this);
            if (foxEx.getMagicule() >= oldMaxMagic - 0.5) {
                foxEx.setMagicule(targetMaxMagic);
                foxEx.markDirty();
            }
        }
    }

    // ─── NBT ───

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.getSummonerUUID().ifPresent(uuid -> tag.putUUID(TAG_SUMMONER_UUID, uuid));
        tag.putInt(TAG_COMMAND, this.getOwnerCommand());
        if (this.wanderCenter != null) {
            tag.putLong(TAG_WANDER_POS, this.wanderCenter.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(TAG_SUMMONER_UUID)) {
            this.setSummonerUUID(tag.getUUID(TAG_SUMMONER_UUID));
        }
        if (tag.contains(TAG_COMMAND)) {
            int cmd = tag.getInt(TAG_COMMAND);
            this.setOwnerCommand(Math.max(CMD_FOLLOW, Math.min(CMD_STAY, cmd)));
        }
        if (tag.contains(TAG_WANDER_POS)) {
            this.wanderCenter = BlockPos.of(tag.getLong(TAG_WANDER_POS));
        }
    }

    /** 禁用繁殖（无后代）。返回 {@link Fox}，签名与 {@link Fox#getBreedOffspring} 一致。 */
    @Override
    public @Nullable Fox getBreedOffspring(ServerLevel level, AgeableMob mate) {
        return null;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal partner) {
        return false;
    }

    /** 召唤时强制成年。 */
    @Override
    public boolean isBaby() {
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    //  自定义 AI 目标（极简版）
    // ──────────────────────────────────────────────────────────────────

    /** 简单跟随召唤者的目标——距离过远就传送，距离适中就走过去。仅在 CMD_FOLLOW 模式下生效。 */
    private static class FollowSummonerGoal extends Goal {
        private final FoxSpiritEntity fox;
        private final double speed;
        private final float startDistance;
        private final float stopDistance;
        private LivingEntity summoner;
        private int teleportCooldown;

        FollowSummonerGoal(FoxSpiritEntity fox, double speed, float startDistance, float stopDistance) {
            this.fox = fox;
            this.speed = speed;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.fox.isFollowMode()) return false;
            LivingEntity s = this.fox.getSummoner();
            if (s == null || s.isSpectator()) return false;
            return this.fox.distanceTo(s) > this.startDistance;
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.fox.isFollowMode()) return false;
            if (this.summoner == null) return false;
            if (this.fox.getNavigation().isDone()) return false;
            return this.fox.distanceTo(this.summoner) > this.stopDistance;
        }

        @Override
        public void start() {
            this.summoner = this.fox.getSummoner();
            this.teleportCooldown = 0;
        }

        @Override
        public void stop() {
            this.summoner = null;
            this.fox.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.summoner == null) return;
            this.fox.getLookControl().setLookAt(this.summoner, 10.0F, this.fox.getMaxHeadXRot());
            if (--this.teleportCooldown <= 0) {
                this.teleportCooldown = 10;
                if (this.fox.distanceTo(this.summoner) > 16.0F) {
                    teleportToSummoner();
                } else {
                    this.fox.getNavigation().moveTo(this.summoner, this.speed);
                }
            }
        }

        private void teleportToSummoner() {
            Vec3 pos = this.summoner.position();
            this.fox.moveTo(pos.x, pos.y, pos.z, this.fox.getYRot(), this.fox.getXRot());
            this.fox.getNavigation().stop();
        }
    }

    /** 当召唤者被某怪物攻击时，把攻击者设为目标。STAY 模式下不主动出击。 */
    private static class SummonerHurtByTargetGoal extends Goal {
        private final FoxSpiritEntity fox;
        private LivingEntity attackerCandidate;
        private int timestamp;

        SummonerHurtByTargetGoal(FoxSpiritEntity fox) {
            this.fox = fox;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.fox.isStayMode()) return false;
            LivingEntity summoner = this.fox.getSummoner();
            if (summoner == null) return false;
            this.attackerCandidate = summoner.getLastHurtByMob();
            int newTs = summoner.getLastHurtByMobTimestamp();
            return this.attackerCandidate != null
                    && newTs != this.timestamp
                    && TargetingConditions.DEFAULT.test(this.fox, this.attackerCandidate)
                    && !this.fox.isAlliedTo(this.attackerCandidate);
        }

        @Override
        public void start() {
            this.fox.setTarget(this.attackerCandidate);
            LivingEntity s = this.fox.getSummoner();
            if (s != null) {
                this.timestamp = s.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    /** 当召唤者攻击某目标时，把那个目标抢过来打。STAY 模式下不主动出击。 */
    private static class SummonerHurtTargetGoal extends Goal {
        private final FoxSpiritEntity fox;
        private LivingEntity target;
        private int timestamp;

        SummonerHurtTargetGoal(FoxSpiritEntity fox) {
            this.fox = fox;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.fox.isStayMode()) return false;
            LivingEntity summoner = this.fox.getSummoner();
            if (summoner == null) return false;
            this.target = summoner.getLastHurtMob();
            int newTs = summoner.getLastHurtMobTimestamp();
            return this.target != null
                    && newTs != this.timestamp
                    && TargetingConditions.DEFAULT.test(this.fox, this.target)
                    && !this.fox.isAlliedTo(this.target);
        }

        @Override
        public void start() {
            this.fox.setTarget(this.target);
            LivingEntity s = this.fox.getSummoner();
            if (s != null) {
                this.timestamp = s.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    /** 静态工具：从召唤者拉取 50% 属性写入新生狐灵（用于"死亡后重置"路径）。 */
    public static void applyHalfStatsFrom(FoxSpiritEntity fox, LivingEntity summoner) {
        // EP / MP 上限 = 玩家上限 × 0.5
        double playerMaxAura = EnergyHelper.getBaseMaxAura(summoner);
        double playerMaxMagic = EnergyHelper.getBaseMaxMagicule(summoner);
        EnergyHelper.setMaxAura(fox, playerMaxAura * 0.5);
        EnergyHelper.setMaxMagicule(fox, playerMaxMagic * 0.5);

        // SHP 上限 = 玩家上限 × 0.5
        AttributeInstance shp = fox.getAttribute(TensuraAttributes.MAX_SPIRITUAL_HEALTH);
        if (shp != null) {
            shp.setBaseValue(summoner.getAttributeValue(TensuraAttributes.MAX_SPIRITUAL_HEALTH) * 0.5);
        }

        // HP 上限 = 玩家上限 × 0.5
        AttributeInstance hp = fox.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(summoner.getMaxHealth() * 0.5);
        }

        // 攻击 = 玩家攻击（100%）
        AttributeInstance atk = fox.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(summoner.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }

        // 首次召唤：使用最大值初始化（满血/满能量出场），不受玩家当前血量影响
        IExistence foxExistence = TensuraStorages.getExistenceFrom(fox);
        foxExistence.setAura(EnergyHelper.getBaseMaxAura(summoner) * 0.5);
        foxExistence.setMagicule(EnergyHelper.getBaseMaxMagicule(summoner) * 0.5);
        double maxShpVal = summoner.getAttributeValue(TensuraAttributes.MAX_SPIRITUAL_HEALTH);
        foxExistence.setSpiritualHealth(maxShpVal * 0.5);
        foxExistence.markDirty();

        fox.setHealth(fox.getMaxHealth());
    }
}
