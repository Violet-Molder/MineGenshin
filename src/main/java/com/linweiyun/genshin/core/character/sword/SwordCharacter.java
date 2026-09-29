package com.linweiyun.genshin.core.character.sword;

import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.sword.Sword;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SwordCharacter extends PGCharacter {

    @Override
    public Class<? extends WeaponItem> getAllowedWeaponClass() {
        return Sword.class;
    }

    public SwordCharacter(
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

    public SwordCharacter(
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