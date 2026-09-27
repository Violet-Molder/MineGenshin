package com.linweiyun.genshin.content.effect.character;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public interface ICharacterEffect {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    /**
     * 把<b>参数化效果</b>自己的构造参数写进效果实例的 {@code data} 标签。
     *
     * <h2>为什么需要</h2>
     * 注册表里只存了效果的<b>类型</b>（一个无参原型），而像
     * {@link com.linweiyun.genshin.content.effect.character.impl.DamageBonusEffect}
     * 这种「加成多少、作用于哪些攻击类型」是构造参数 —— 光靠注册名读档回来会变成一个
     * 没参数的实例（加成 0 = 静默失效）。所以参数化效果自己把参数写出来、读档时自己重建。
     *
     * <p>单例效果（大多数）不用实现：默认什么都不写。
     */
    default void writeInstanceData(net.minecraft.nbt.CompoundTag data) {}

    /**
     * 读档时按 {@link #writeInstanceData} 写下的参数<b>重建</b>效果实例。
     *
     * <p>默认返回 {@code this}（注册表里的那个单例），只有参数化效果需要覆盖。
     */
    default ICharacterEffect createFromInstanceData(net.minecraft.nbt.CompoundTag data) {
        return this;
    }

    /**
     * 治疗加成（小数，{@code 0.04 = 4%}），默认 0。
     *
     * <p>文案写「治疗加成提升 X%」的效果覆写它；装备者侧还有武器自己的一份
     * （{@code WeaponItem#getHealingBonus}），两边在 {@code PGCharacter#getHealingBonus} 里相加，
     * 由角色实际治疗时消费。
     */
    default float getHealingBonus() {
        return 0f;
    }

    /**
     * 按<b>元素 / 反应类型</b>的额外暴击伤害（加在统一 CDG 之上），默认 0。
     *
     * <p>文案写「水/冰伤害的暴击伤害提升 X%」「星扩散反应伤害的暴击伤害提升 X%」就覆写它 ——
     * 统一 CDG 表达不了这种按元素区分的口径（例：沃雅妮莎 2/6 命「黑与白的双音」）。
     */
    default float getCritDamageBonus(com.linweiyun.genshin.core.element.GenshinElement element,
                                     boolean stellarReaction) {
        return 0f;
    }

    /**
     * 按分支的「擢升」加成（擢升区 = {@code 1 + 值}），默认 0。
     *
     * <p>和 {@code getStellarGlimmerBonus} 的区别是乘区：那个落在反应加成区、与元素精通加算；
     * 擢升是独立乘区。例：沃雅妮莎 6 命的「星扩散反应伤害擢升 30%」。
     */
    default float getElevationBonus(com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch branch) {
        return 0f;
    }
    /**
     * 效果首次添加时调用
     * @param holder 效果持有者（玩家）
     * @param character 效果作用的角色数据
     * @param instance 效果实例（包含持续时间、等级、额外数据等）
     */
    default void onEffectAdded(Player holder, PGCharacter character, CharacterEffectInstance instance) {}

    /**
     * 效果被移除时调用
     * @param holder 效果持有者（玩家）
     * @param character 效果作用的角色数据
     * @param instance 被移除的效果实例
     */
    default void onEffectRemoved(Player holder, PGCharacter character, CharacterEffectInstance instance) {}

    /**
     * 效果覆盖时调用 —— 当角色已有相同效果且再次添加时触发
     * 由各个具体效果类实现，定义重复添加时的行为（如叠加数量、刷新持续时间等）
     * @param holder 效果持有者（玩家）
     * @param character 效果作用的角色数据
     * @param existingInstance 已存在的效果实例（即将被覆盖的旧实例）
     * @param newInstance 新添加的效果实例（即将替换旧实例的新实例）
     */
    default void onEffectOverride(Player holder, PGCharacter character, CharacterEffectInstance existingInstance, CharacterEffectInstance newInstance) {}

    /**
     * 效果前台tick —— 角色处于前台激活状态时每tick调用
     * @return 返回true表示效果继续生效，返回false表示效果应被移除
     */
    default boolean onEffectFrontTick(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        return true;
    }

    /**
     * 效果后台tick —— 角色处于后台待机状态时每tick调用
     * @return 返回true表示效果继续生效，返回false表示效果应被移除
     */
    default boolean onEffectBackTick(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        return true;
    }

    /**
     * 效果通用tick —— 无论角色前后台都会调用
     * @return 返回true表示效果继续生效，返回false表示效果应被移除
     */
    default boolean onEffectTick(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        if (!holder.level().isClientSide()) {
            if (instance.getDuration() == CharacterEffectInstance.INFINITE) return true;
            instance.setDuration(instance.getDuration() - 1);
        }
        return instance.getDuration() > 0;
    }

    /**
     * 角色受到攻击时调用
     * @param holder 效果持有者（玩家）
     * @param character 效果作用的角色数据
     * @param target 攻击目标
     * @param instance 效果实例
     */
    default void onAttacked(Player holder, PGCharacter character, LivingEntity target, CharacterEffectInstance instance, ModDamageSource damageSource) {}

    /**
     * 是否为即时效果（一次性生效，不需要持续）
     * @return true表示即时效果，false表示持续效果
     */
    default boolean isInstantaneous() {
        return false;
    }

    /**
     * 获取对指定攻击类型的伤害加成（加成区）
     * @param attackType 攻击类型
     * @return 加成值（小数，如 0.12 = 12%），默认返回0
     */
    default float getDamageBonus(AttackType attackType) {
        return 0f;
    }

    /**
     * 获取对指定攻击类型和元素的伤害加成（加成区） —— 带元素校验的重载版本
     * @param attackType 攻击类型
     * @param element 攻击元素
     * @return 加成值（小数，如 0.12 = 12%），默认返回0
     */
    default float getDamageBonus(AttackType attackType, GenshinElement element) {
        return 0f;
    }

    /**
     * 对<b>辉映·星烁</b>反应伤害的加成（反应加成区，和元素精通加算）。
     *
     * <p>星烁 = 星扩散 + 星超导的统称，所以这个方法的返回值<b>按分支给</b>：
     * <pre>
     * // 文案写的是「星烁反应加成」→ 两个分支都给
     * return 0.20f;
     *
     * // 文案只写了星扩散（例如薇斯娜天赋）→ 只给星扩散
     * return branch == StellarGlimmerBranch.SWIRL ? 0.20f : 0f;
     * </pre>
     *
     * @param branch 这条星烁反应属于哪个分支
     * @return 加成值（小数，如 0.20 = 20%），默认 0
     */
    default float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
        return 0f;
    }

    /**
     * 这个效果能不能被加到<b>已经有某些效果</b>的角色身上。
     *
     * <p>返回 {@code false} = 这次添加被忽略（效果不上场，也不会走覆盖逻辑）。
     * 用于「同组效果互斥且分优先级」的场合 —— 目前只有辉映·星烁：
     * 有星超导时不接受星扩散（星超导优先级更高），反向则由星超导自己把星扩散摘掉。
     *
     * @param container 角色当前的效果容器
     * @return 默认 true（都能加）
     */
    default boolean canApplyWith(CharacterEffectContainer container) {
        return true;
    }
}