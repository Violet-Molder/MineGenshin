package com.linweiyun.genshin.core.character.claymore;

import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.claymore.Claymore;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 大剑角色的基类。
 *
 * <h2>和别的武器类只差两件事</h2>
 * <ol>
 *   <li>限定武器：{@link #getAllowedWeaponClass()} 只认大剑（换武器校验、武器面板、削韧表都读它）；</li>
 *   <li>重击是<b>持续型</b> —— 这一条不写在这里，写在技能基类
 *       {@link ClaymoreSkill}（按键时序属于技能，不属于角色主类）。
 *       所以大剑角色只要 {@code extends ClaymoreCharacter} 且技能
 *       {@code extends ClaymoreSkill}，就自动拿到「按住进入持续重击、松手/到时结束」。</li>
 * </ol>
 *
 * <p>构造器与 {@code PolearmCharacter} / {@code SwordCharacter} 保持同一套：
 * 两冷却（11 参）与三冷却（12 参，E 有长按变体时用）。
 */
public abstract class ClaymoreCharacter extends PGCharacter {

    public ClaymoreCharacter() {
        super();
    }

    @Override
    public Class<? extends WeaponItem> getAllowedWeaponClass() {
        return Claymore.class;
    }

    public ClaymoreCharacter(
            int characterUUID, int starRating, Component name,
            String elementalId, CharacterAscendAttribute ascendAttribute,
            int skillMaxCooldownTick, int burstMaxCooldownTick,
            float maxObtainingEnergy, String textureId,
            Map<Identifier, Supplier<List<? extends Integer>>> statGrowthMap) {
        super(characterUUID, starRating, name,
                elementalId, ascendAttribute,
                skillMaxCooldownTick, burstMaxCooldownTick,
                maxObtainingEnergy, textureId, statGrowthMap);
    }

    public ClaymoreCharacter(
            int characterUUID, int starRating, Component name,
            String elementalId, CharacterAscendAttribute ascendAttribute,
            int skillShortMaxCooldownTick, int skillLongMaxCooldownTick, int burstMaxCooldownTick,
            float maxObtainingEnergy, String textureId,
            Map<Identifier, Supplier<List<? extends Integer>>> statGrowthMap) {
        super(characterUUID, starRating, name,
                elementalId, ascendAttribute,
                skillShortMaxCooldownTick, skillLongMaxCooldownTick, burstMaxCooldownTick,
                maxObtainingEnergy, textureId, statGrowthMap);
    }
}
