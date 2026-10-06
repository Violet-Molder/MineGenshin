package com.linweiyun.genshin.content.effect.character.impl;

import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 伤害加成效果 —— 在加成区为指定攻击类型（和/或元素）提供额外的伤害加成
 *
 * 使用示例：
 * 阿蕾奇诺装备46.6%火伤杯，同时获得一个12%对普通攻击和重击的DamageBonusEffect，
 * 则火普通攻击加成区 = 0.466 + 0.12，火元素战技加成区 = 0.466
 *
 * 带元素版本示例：
 * 申鹤领域内获得15%冰伤加成 → new DamageBonusEffect(0.15f, ModElements.CYRO.get())
 */
public class DamageBonusEffect implements ICharacterEffect {

    private final float bonus;
    private final Set<AttackType> applicableTypes;
    private final GenshinElement applicableElement;

    // ==================== 无参构造 —— 注册用 ====================

    public DamageBonusEffect() {
        this.bonus = 0f;
        this.applicableTypes = EnumSet.allOf(AttackType.class);
        this.applicableElement = null;
    }

    // ==================== 不带元素 —— 所有元素通用 ====================

    /**
     * 全攻击类型加成，不限元素
     */
    public DamageBonusEffect(float bonus) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.allOf(AttackType.class);
        this.applicableElement = null;
    }

    /**
     * 单攻击类型，不限元素
     */
    public DamageBonusEffect(float bonus, AttackType type) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.of(type);
        this.applicableElement = null;
    }

    /**
     * 双攻击类型，不限元素
     */
    public DamageBonusEffect(float bonus, AttackType type1, AttackType type2) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.of(type1, type2);
        this.applicableElement = null;
    }

    /**
     * 三攻击类型，不限元素
     */
    public DamageBonusEffect(float bonus, AttackType type1, AttackType type2, AttackType type3) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.of(type1, type2, type3);
        this.applicableElement = null;
    }

    /**
     * 四攻击类型，不限元素
     */
    public DamageBonusEffect(float bonus, AttackType type1, AttackType type2, AttackType type3, AttackType type4) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.of(type1, type2, type3, type4);
        this.applicableElement = null;
    }

    // ==================== 带元素 —— 限定元素 + 攻击类型 ====================

    /**
     * 全攻击类型，限定元素
     */
    public DamageBonusEffect(float bonus, GenshinElement element) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.allOf(AttackType.class);
        this.applicableElement = element;
    }

    /**
     * 单攻击类型，限定元素
     */
    public DamageBonusEffect(float bonus, GenshinElement element, AttackType type) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.of(type);
        this.applicableElement = element;
    }

    /**
     * 双攻击类型，限定元素
     */
    public DamageBonusEffect(float bonus, GenshinElement element, AttackType type1, AttackType type2) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.of(type1, type2);
        this.applicableElement = element;
    }

    /**
     * 三攻击类型，限定元素
     */
    public DamageBonusEffect(float bonus, GenshinElement element, AttackType type1, AttackType type2, AttackType type3) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.of(type1, type2, type3);
        this.applicableElement = element;
    }

    /**
     * 四攻击类型，限定元素
     */
    public DamageBonusEffect(float bonus, GenshinElement element, AttackType type1, AttackType type2, AttackType type3, AttackType type4) {
        this.bonus = bonus;
        this.applicableTypes = EnumSet.of(type1, type2, type3, type4);
        this.applicableElement = element;
    }

    // ==================== Getter ====================

    public float getBonus() {
        return bonus;
    }

    public Set<AttackType> getApplicableTypes() {
        return Collections.unmodifiableSet(applicableTypes);
    }

    public GenshinElement getApplicableElement() {
        return applicableElement;
    }

    // ==================== getDamageBonus ====================

    @Override
    public float getDamageBonus(AttackType attackType) {
        return applicableTypes.contains(attackType) ? bonus : 0f;
    }

    @Override
    public float getDamageBonus(AttackType attackType, GenshinElement element) {
        if (!applicableTypes.contains(attackType)) return 0f;
        if (applicableElement != null && applicableElement != element) return 0f;
        return bonus;
    }

    // ==================== 参数化效果的存档 ====================
    //
    // 注册表里只存了「无参原型」（minegenshin:damage_bonus），而加成多少、作用于哪些攻击类型
    // 是构造参数。不把参数写出来的话，读档回来会变成一个 bonus=0 的实例（buff 静默失效），
    // 所以这里自己写、自己读。

    @Override
    public void writeInstanceData(net.minecraft.nbt.CompoundTag data) {
        data.putFloat("dmg_bonus", bonus);

        int[] typeOrdinals = new int[applicableTypes.size()];
        int i = 0;
        for (AttackType type : applicableTypes) {
            typeOrdinals[i++] = type.ordinal();
        }
        data.putIntArray("dmg_types", typeOrdinals);

        if (applicableElement != null) {
            // 存<b>完整注册名</b>（元素 getId() 只给 path，反查注册表需要带命名空间的键）
            net.minecraft.resources.Identifier key =
                    com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENT_REGISTRY
                            .getKey(applicableElement);
            if (key != null) {
                data.putString("dmg_element", key.toString());
            }
        }
    }

    @Override
    public ICharacterEffect createFromInstanceData(net.minecraft.nbt.CompoundTag data) {
        float savedBonus = data.getFloat("dmg_bonus").orElse(0f);
        java.util.Optional<int[]> savedTypes = data.getIntArray("dmg_types");
        if (savedTypes.isEmpty()) {
            return this;        // 老数据没有参数：保持原型（加成为 0，但至少类型正确）
        }

        AttackType[] allTypes = AttackType.values();
        Set<AttackType> types = EnumSet.noneOf(AttackType.class);
        for (int ordinal : savedTypes.get()) {
            if (ordinal >= 0 && ordinal < allTypes.length) {
                types.add(allTypes[ordinal]);
            }
        }
        if (types.isEmpty()) {
            return this;
        }

        GenshinElement element = null;
        java.util.Optional<String> elementKey = data.getString("dmg_element");
        if (elementKey.isPresent()) {
            try {
                element = com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENT_REGISTRY
                        .get(net.minecraft.resources.Identifier.parse(elementKey.get()))
                        .map(net.minecraft.core.Holder.Reference::value)
                        .orElse(null);
            } catch (Exception ignored) {
                element = null;
            }
        }

        return new DamageBonusEffect(savedBonus, types, element);
    }

    /** 按「一组攻击类型 + 可选元素」直接构造（读档重建用）。 */
    public DamageBonusEffect(float bonus, Set<AttackType> types, GenshinElement element) {
        this.bonus = bonus;
        this.applicableTypes = types.isEmpty() ? EnumSet.noneOf(AttackType.class) : EnumSet.copyOf(types);
        this.applicableElement = element;
    }
}