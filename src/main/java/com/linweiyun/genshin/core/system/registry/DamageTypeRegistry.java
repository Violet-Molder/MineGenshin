package com.linweiyun.genshin.core.system.registry;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * MC DamageType 注册 —— 定义所有自定义伤害类型的 ResourceKey
 *
 * 设计决策：DamageType 按攻击方式分类，不按元素分类
 * 原因：MC的DamageType控制的是死亡消息、伤害缩放（PvP减免）、疲劳值（饥饿恢复）
 *       这些都与攻击方式有关，与元素无关
 *       火元素普攻和冰元素普攻的死亡消息相同，但普攻和战技的死亡消息不同
 *
 * 三层分离架构：
 * - DamageType（MC层）：死亡消息、伤害缩放、疲劳值 → 按攻击方式
 * - AttackType（游戏层）：衰减标签、攻击分类、计时计数器 → 按攻击方式
 * - GenshinElement（元素层）：元素附着、元素反应、元素量 → 按元素
 *
 * 与 AttackType 的关系：
 * - AttackType 是游戏内部枚举，控制衰减系统和计时计数器
 * - DamageType 是MC注册表条目，控制死亡消息和伤害缩放
 * - 两者一一对应，通过本类的 resolveDamageType() 方法桥接
 * - DamageSpec 持有 AttackType，通过 ModDamageSource 嵌入 DamageSource 在MC管线中传递
 */
public class DamageTypeRegistry {

    // ========== MC DamageType 的 ResourceKey 常量 ==========

    // 普通攻击 —— 对应 AttackType.NORMAL_ATTACK
    public static final ResourceKey<DamageType> NORMAL_ATTACK =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("normal_attack"));

    // 重击 —— 对应 AttackType.CHARGED_ATTACK
    public static final ResourceKey<DamageType> CHARGED_ATTACK =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("charged_attack"));

    // 下落攻击 —— 对应 AttackType.PLUNGING_ATTACK
    public static final ResourceKey<DamageType> PLUNGING_ATTACK =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("plunging_attack"));

    // 元素战技 —— 对应 AttackType.ELEMENTAL_SKILL
    public static final ResourceKey<DamageType> ELEMENTAL_SKILL =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("elemental_skill"));

    // 元素爆发 —— 对应 AttackType.ELEMENTAL_BURST
    public static final ResourceKey<DamageType> ELEMENTAL_BURST =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("elemental_burst"));

    // 特殊/环境伤害 —— 对应 AttackType.SPECIAL
    public static final ResourceKey<DamageType> SPECIAL =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("special"));

    // 怪物伤害 —— 对应 AttackType.MONSTER
    public static final ResourceKey<DamageType> MONSTER =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("monster"));

    // 月感电 —— 对应 AttackType.LUNAR_CHARGED
    public static final ResourceKey<DamageType> LUNAR_CHARGED =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("lunar_charged"));

    // 感电 —— 对应 AttackType.ELECTRO_CHARGED
    public static final ResourceKey<DamageType> ELECTRO_CHARGED =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("electro_charged"));

    // 扩散 —— 对应 AttackType.SWIRL
    public static final ResourceKey<DamageType> SWIRL =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("swirl"));

    // 星扩散 —— 对应 AttackType.STELLAR_SWIRL
    public static final ResourceKey<DamageType> STELLAR_SWIRL =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("stellar_swirl"));

    // 星超导 —— 对应 AttackType.STELLAR_CONDUCE
    public static final ResourceKey<DamageType> STELLAR_CONDUCE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Minegenshin.id("stellar_conduce"));

    // ========== 解析方法 ==========

    /**
     * 根据 AttackType 解析对应的 MC DamageType ResourceKey
     * 桥接游戏层（AttackType）和MC层（DamageType）
     * @param attackType 攻击类型
     * @return 对应的 DamageType ResourceKey
     */
    public static ResourceKey<DamageType> resolveKey(AttackType attackType) {
        return switch (attackType) {                    // 根据攻击类型匹配
            case NORMAL_ATTACK -> NORMAL_ATTACK;        // 普通攻击
            case CHARGED_ATTACK -> CHARGED_ATTACK;      // 重击
            case PLUNGING_ATTACK -> PLUNGING_ATTACK;    // 下落攻击
            case ELEMENTAL_SKILL -> ELEMENTAL_SKILL;    // 元素战技
            case ELEMENTAL_BURST -> ELEMENTAL_BURST;    // 元素爆发
            case SPECIAL -> SPECIAL;                    // 特殊/环境伤害
            case MONSTER -> MONSTER;                    // 怪物伤害
            case LUNAR_CHARGED -> LUNAR_CHARGED;        // 月感电
            case ELECTRO_CHARGED -> ELECTRO_CHARGED;    // 感电
            case SWIRL -> SWIRL;                        // 扩散
            case STELLAR_SWIRL -> STELLAR_SWIRL;          // 星扩散
            case STELLAR_CONDUCE -> STELLAR_CONDUCE;    // 星超导
        };
    }

    /**
     * 根据 AttackType 获取 MC DamageType 的 Holder
     * @param attackType 攻击类型
     * @param registryAccess 注册表访问器
     * @return DamageType 的 Holder
     */
    public static Holder<DamageType> resolveHolder(AttackType attackType, RegistryAccess registryAccess) {
        return registryAccess                           // 从注册表访问器
                .lookupOrThrow(Registries.DAMAGE_TYPE) // 获取DamageType注册表
                .getOrThrow(resolveKey(attackType));  // 获取对应Holder
    }

    /**
     * 根据 AttackType 创建 DamageSource
     * 战斗系统使用此方法将游戏层的伤害数据转换为MC的DamageSource
     * @param attackType 攻击类型
     * @param causer 造成伤害的实体（攻击者）
     * @return MC的DamageSource实例
     */
    public static DamageSource createDamageSource(AttackType attackType, Entity causer) {
        return new DamageSource(                        // 创建DamageSource
                resolveHolder(attackType, causer.level().registryAccess()), // 获取DamageType Holder
                causer);                                // 设置造成伤害的实体
    }
}