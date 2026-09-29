package com.linweiyun.genshin.content.effect.character.impl;

import com.linweiyun.genshin.content.effect.character.CharacterEffectContainer;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * 辉映·星扩散（Radiance: Stellar Glimmer - Swirl）—— 辉映·星烁 的一个分支。
 *
 * <p>和 {@link RadianceStellarConduceEffect 星超导} 互斥，且<b>星超导优先</b>：
 * 星超导能覆盖星扩散，反过来不行 —— 所以这里在 {@link #canApplyWith} 里
 * 一旦发现身上已有星超导就拒绝上场。
 */
public class RadianceStellarSwirlEffect implements ICharacterEffect {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    public static final int DURATION_TICKS = 160; // 8s = 160 ticks

    /** 有星超导时不接受星扩散（星超导优先级更高）。 */
    @Override
    public boolean canApplyWith(CharacterEffectContainer container) {
        return !container.hasEffectOfType(RadianceStellarConduceEffect.class);
    }

    @Override
    public void onEffectOverride(Player holder, PGCharacter character,
                                 CharacterEffectInstance existingInstance,
                                 CharacterEffectInstance newInstance) {
        existingInstance.setDuration(newInstance.getDuration());
    }
}