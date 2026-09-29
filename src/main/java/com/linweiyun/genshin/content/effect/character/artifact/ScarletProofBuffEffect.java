package com.linweiyun.genshin.content.effect.character.artifact;

import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * 血红之证 · 四件套的<b>触发buff</b>：触发星扩散反应后 10 秒内
 * 暴击率 +16%、<b>星扩散反应伤害 +40%</b>。
 *
 * <h2>那 40% 走哪一区</h2>
 * 就是单人伤害计算里、精通区后面的<b>星烁加成</b>
 * （{@code 反应加成区 = 1 + 16×元素精通/(元素精通+2000) + 星烁加成}）——
 * 而且这一份<b>只给星扩散</b>（不给星超导），所以
 * {@link #getStellarGlimmerBonus(StellarGlimmerBranch)} 只在 {@code SWIRL} 分支返回 0.40。
 */
public class ScarletProofBuffEffect implements ICharacterEffect {

    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    /** 触发后持续 10 秒。 */
    public static final int DURATION_TICKS = 10 * 20;

    /** 暴击率 +16%（CR 存的是小数，0.16 = 16%）。 */
    public static final double CRIT_RATE_BONUS = 0.16;
    /** 星扩散反应伤害 +40%。 */
    public static final float STELLAR_SWIRL_BONUS = 0.40f;

    private static final String CR_SOURCE = "scarlet_proof4_cr";

    @Override
    public void onEffectAdded(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        character.getData().addAttributeFlatModifier(ModAttributes.CR.value(), CR_SOURCE, CRIT_RATE_BONUS);
        LOGGER.debug("[血红之证] 触发星扩散 → 暴击率+16% / 星扩散伤害+40%，持续 {} 刻 | char={}",
                DURATION_TICKS, character.getName());
    }

    @Override
    public void onEffectRemoved(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        character.getData().removeAttributeModifier(ModAttributes.CR.value(), CR_SOURCE);
    }

    /** 再触发一次就刷新时间（不叠加）。 */
    @Override
    public void onEffectOverride(Player holder, PGCharacter character,
                                 CharacterEffectInstance existingInstance,
                                 CharacterEffectInstance newInstance) {
        existingInstance.setDuration(newInstance.getDuration());
    }

    /** 星烁加成：<b>只给星扩散</b>（文案只写了星扩散伤害）。 */
    @Override
    public float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
        return branch == StellarGlimmerBranch.SWIRL ? STELLAR_SWIRL_BONUS : 0f;
    }
}
