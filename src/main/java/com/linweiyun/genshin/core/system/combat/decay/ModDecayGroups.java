package com.linweiyun.genshin.core.system.combat.decay;

import com.linweiyun.elementlib.core.system.combat.decay.DecayGroup;
import com.linweiyun.elementlib.core.system.combat.decay.DecaySequence;

/**
 * 本模组自己的衰减组别（角色专属）。
 */
public final class ModDecayGroups {

    /** 申鹤战技：0.1 秒清除，只有第 1 次附着。 */
    public static final DecayGroup SHENHE_SKILL = new DecayGroup(
            2,
            DecaySequence.createRepeating(new float[]{1, 0, 0, 0, 0, 0, 0}, 1),
            DecaySequence.DEFAULT_DAMAGE,
            DecaySequence.DEFAULT_POISE
    );

    /** 重击：与普攻同一套规则，但元素序列是独立实例（独立附着计数）。 */
    public static final DecayGroup CHARGED_ATTACK = new DecayGroup(
            50,
            DecaySequence.createRepeating(new float[]{1.0f, 0.0f, 0.0f}, 8),
            DecaySequence.DEFAULT_DAMAGE,
            DecaySequence.DEFAULT_POISE
    );

    /** 雷神协同攻击：0.1 秒清除，每次命中都能重新附着弱雷。 */
    public static final DecayGroup RAIDEN_COORDINATED = new DecayGroup(
            2,
            DecaySequence.createFilled(1.0f, 1),
            DecaySequence.DEFAULT_DAMAGE,
            DecaySequence.DEFAULT_POISE
    );

    private ModDecayGroups() {
    }
}
