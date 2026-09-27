package com.linweiyun.genshin.content.effect.character.impl;

import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * 辉映·星超导（Radiance: Stellar-Conduce）—— 辉映·星烁 的另一个分支。
 *
 * <h2>和星扩散的关系（重点）</h2>
 * <ul>
 *   <li>星扩散和星超导<b>同属辉映·星烁</b>：写「星烁反应加成」的 buff 两个都加，
 *       只写其中一个的（例如薇斯娜天赋）就只加那一个。见
 *       {@link com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch}。</li>
 *   <li>同一个角色身上<b>只能存在一个</b>，而且<b>星超导优先于星扩散</b>：
 *       星超导可以覆盖星扩散，反过来不行。</li>
 * </ul>
 * 两条规则分别落在：
 * <pre>
 * 覆盖方向   本类的 onEffectAdded —— 挂上星超导时把星扩散摘掉
 * 不许反向   RadianceStellarSwirlEffect.canApplyWith —— 有星超导时不接受星扩散
 * </pre>
 */
public class RadianceStellarConduceEffect implements ICharacterEffect {

    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    /** 和星扩散同一个持续时间：8 秒。 */
    public static final int DURATION_TICKS = 160;

    @Override
    public void onEffectAdded(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        // 星超导优先：上位分支一出现，下位分支立刻下场
        if (character == null) {
            return;
        }
        int removed = character.getData().getEffectContainer()
                .removeEffectsOfType(RadianceStellarSwirlEffect.class);
        if (removed > 0) {
            LOGGER.debug("[辉映-星超导] 覆盖星扩散 | char={}", character.getName());
            character.getData().syncEffectsToTag();
        }
    }

    @Override
    public void onEffectOverride(Player holder, PGCharacter character,
                                 CharacterEffectInstance existingInstance,
                                 CharacterEffectInstance newInstance) {
        existingInstance.setDuration(newInstance.getDuration());
        LOGGER.debug("[辉映-星超导] 刷新持续时间 to={} | char={}",
                newInstance.getDuration(), character.getName());
    }
}
