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

    private ModDecayGroups() {
    }
}
