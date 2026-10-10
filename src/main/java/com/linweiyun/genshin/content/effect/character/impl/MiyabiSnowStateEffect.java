package com.linweiyun.genshin.content.effect.character.impl;

import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.elementlib.core.element.GenshinElement;

/**
 * 星见雅的星雪状态：持续期间视为处于辉映·星超导，并提升冰元素伤害与星超导反应伤害。
 *
 * <p>两个加成分属不同乘区，所以分在两个方法里：冰元素伤害走增伤区
 * （{@link #getDamageBonus}），星超导反应伤害走反应加成区（{@link #getStellarGlimmerBonus}，
 * 与元素精通加算）。
 */
public class MiyabiSnowStateEffect implements ICharacterEffect {

    /** 冰元素伤害 +15%（增伤区）。 */
    public static final float CRYO_DAMAGE_BONUS = 0.15f;

    /** 星超导反应伤害 +25%（反应加成区）。 */
    public static final float CONDUCE_DAMAGE_BONUS = 0.25f;

    @Override
    public float getDamageBonus(AttackType attackType, GenshinElement element) {
        return element == ModElements.CYRO.get() ? CRYO_DAMAGE_BONUS : 0f;
    }

    @Override
    public float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
        return branch == StellarGlimmerBranch.CONDUCE ? CONDUCE_DAMAGE_BONUS : 0f;
    }
}
