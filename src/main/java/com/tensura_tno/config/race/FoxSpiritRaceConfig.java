package com.tensura_tno.config.race;

import io.github.manasmods.tensura.config.race.RaceConfig;

/**
 * 狐灵种族线配置 — 幼灵狐 → 狐灵使 → 灵狐契主 → 玄灵狐主 → 天灵狐尊
 * <p>
 * 数值说明：
 * <ul>
 *   <li>maxHealth 为加成值，玩家基础 20 HP，故 10 HP = -10.0，20 HP = 0.0，50 HP = 30.0</li>
 *   <li>maxSpiritualHealth 为加成值</li>
 *   <li>attackSpeed 为加成值，MC 基础攻速 4.0，加成后总攻速 ≥ 4.0</li>
 * </ul>
 */
public class FoxSpiritRaceConfig {

    // ======================== 幼灵狐 (10 HP) ========================
    public static class BabySpiritFox extends RaceConfig.Default {
        public double minAura = 100.0;
        public double maxAura = 300.0;
        public double minMagicule = 200.0;
        public double maxMagicule = 500.0;
        public double size = -0.25;
        public double maxHealth = -10.0;       // 20 + (-10) = 10 HP
        public double maxSpiritualHealth = 80.0;
        public double attack = 0.0;
        public double attackSpeed = 0.0;       // total 4.0
        public double knockbackResistance = 0.0;
        public double movementSpeed = 0.0;
        public double swimSpeed = 0.0;

        public double getMinAura() { return this.minAura; }
        public double getMaxAura() { return this.maxAura; }
        public double getMinMagicule() { return this.minMagicule; }
        public double getMaxMagicule() { return this.maxMagicule; }
        public double getSize() { return this.size; }
        public double getMaxHealth() { return this.maxHealth; }
        public double getMaxSpiritualHealth() { return this.maxSpiritualHealth; }
        public double getAttack() { return this.attack; }
        public double getAttackSpeed() { return this.attackSpeed; }
        public double getKnockbackResistance() { return this.knockbackResistance; }
        public double getMovementSpeed() { return this.movementSpeed; }
        public double getSwimSpeed() { return this.swimSpeed; }
    }

    // ======================== 狐灵使 (20 HP) ========================
    public static class FoxSpiritEnvoy extends RaceConfig.Default {
        public double minAura = 500.0;
        public double maxAura = 1500.0;
        public double minMagicule = 500.0;
        public double maxMagicule = 1000.0;
        public double size = -0.15;
        public double maxHealth = 0.0;         // 20 + 0 = 20 HP
        public double maxSpiritualHealth = 200.0;
        public double attack = 0.5;
        public double attackSpeed = 0.0;       // total 4.0
        public double knockbackResistance = 0.0;
        public double movementSpeed = 0.0;
        public double swimSpeed = 0.0;

        public double getMinAura() { return this.minAura; }
        public double getMaxAura() { return this.maxAura; }
        public double getMinMagicule() { return this.minMagicule; }
        public double getMaxMagicule() { return this.maxMagicule; }
        public double getSize() { return this.size; }
        public double getMaxHealth() { return this.maxHealth; }
        public double getMaxSpiritualHealth() { return this.maxSpiritualHealth; }
        public double getAttack() { return this.attack; }
        public double getAttackSpeed() { return this.attackSpeed; }
        public double getKnockbackResistance() { return this.knockbackResistance; }
        public double getMovementSpeed() { return this.movementSpeed; }
        public double getSwimSpeed() { return this.swimSpeed; }
    }

    // ======================== 灵狐契主 (50 HP) ========================
    public static class SpiritFoxContractMaster extends RaceConfig.Default {
        public double minAura = 2000.0;
        public double maxAura = 4000.0;
        public double minMagicule = 1500.0;
        public double maxMagicule = 3000.0;
        public double size = -0.1;
        public double maxHealth = 30.0;        // 20 + 30 = 50 HP
        public double maxSpiritualHealth = 680.0;
        public double attack = 1.0;
        public double attackSpeed = 0.5;       // total 4.5
        public double knockbackResistance = 0.2;
        public double movementSpeed = 0.0;
        public double swimSpeed = 0.0;

        public double getMinAura() { return this.minAura; }
        public double getMaxAura() { return this.maxAura; }
        public double getMinMagicule() { return this.minMagicule; }
        public double getMaxMagicule() { return this.maxMagicule; }
        public double getSize() { return this.size; }
        public double getMaxHealth() { return this.maxHealth; }
        public double getMaxSpiritualHealth() { return this.maxSpiritualHealth; }
        public double getAttack() { return this.attack; }
        public double getAttackSpeed() { return this.attackSpeed; }
        public double getKnockbackResistance() { return this.knockbackResistance; }
        public double getMovementSpeed() { return this.movementSpeed; }
        public double getSwimSpeed() { return this.swimSpeed; }
    }

    // ======================== 玄灵狐主 (200 HP)  ========================
    public static class MysticFoxMaster extends RaceConfig.Default {
        public double minAura = 5000.0;
        public double maxAura = 10000.0;
        public double minMagicule = 4000.0;
        public double maxMagicule = 8000.0;
        public double size = -0.05;
        public double maxHealth = 180.0;       // 20 + 180 = 200 HP
        public double maxSpiritualHealth = 2800.0;
        public double attack = 3.0;
        public double attackSpeed = 1.0;       // total 5.0
        public double knockbackResistance = 0.4;
        public double movementSpeed = 0.0;
        public double swimSpeed = 0.0;

        public double getMinAura() { return this.minAura; }
        public double getMaxAura() { return this.maxAura; }
        public double getMinMagicule() { return this.minMagicule; }
        public double getMaxMagicule() { return this.maxMagicule; }
        public double getSize() { return this.size; }
        public double getMaxHealth() { return this.maxHealth; }
        public double getMaxSpiritualHealth() { return this.maxSpiritualHealth; }
        public double getAttack() { return this.attack; }
        public double getAttackSpeed() { return this.attackSpeed; }
        public double getKnockbackResistance() { return this.knockbackResistance; }
        public double getMovementSpeed() { return this.movementSpeed; }
        public double getSwimSpeed() { return this.swimSpeed; }
    }

    // ======================== 天灵狐尊 (450 HP)  ========================
    public static class HeavenlyFoxSovereign extends RaceConfig.Default {
        public double minAura = 10000.0;
        public double maxAura = 20000.0;
        public double minMagicule = 8000.0;
        public double maxMagicule = 15000.0;
        public double size = 0.0;
        public double maxHealth = 430.0;       // 20 + 430 = 450 HP
        public double maxSpiritualHealth = 5400.0;
        public double attack = 5.0;
        public double attackSpeed = 2.0;       // total 6.0
        public double knockbackResistance = 0.6;
        public double movementSpeed = 0.0;
        public double swimSpeed = 0.0;

        public double getMinAura() { return this.minAura; }
        public double getMaxAura() { return this.maxAura; }
        public double getMinMagicule() { return this.minMagicule; }
        public double getMaxMagicule() { return this.maxMagicule; }
        public double getSize() { return this.size; }
        public double getMaxHealth() { return this.maxHealth; }
        public double getMaxSpiritualHealth() { return this.maxSpiritualHealth; }
        public double getAttack() { return this.attack; }
        public double getAttackSpeed() { return this.attackSpeed; }
        public double getKnockbackResistance() { return this.knockbackResistance; }
        public double getMovementSpeed() { return this.movementSpeed; }
        public double getSwimSpeed() { return this.swimSpeed; }
    }
}
