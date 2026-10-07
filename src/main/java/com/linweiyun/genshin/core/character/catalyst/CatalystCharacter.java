package com.linweiyun.genshin.core.character.catalyst;

import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.catalyst.Catalyst;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class CatalystCharacter extends PGCharacter {

    public CatalystCharacter() {
        super();
    }

    @Override
    public Class<? extends WeaponItem> getAllowedWeaponClass() {
        return Catalyst.class;
    }


    public CatalystCharacter(
            int characterUUID, int starRating, Component name,
            String elementalId, CharacterAscendAttribute ascendAttribute,
            int skillMaxCooldownTick, int burstMaxCooldownTick,
            float maxObtainingEnergy, String textureId,
            Map<ResourceLocation, Supplier<List<? extends Integer>>> statGrowthMap) {
        super(characterUUID, starRating, name,
                elementalId, ascendAttribute,
                skillMaxCooldownTick, burstMaxCooldownTick,
                maxObtainingEnergy, textureId, statGrowthMap);
    }

    public CatalystCharacter(
            int characterUUID, int starRating, Component name,
            String elementalId, CharacterAscendAttribute ascendAttribute,
            int skillShortMaxCooldownTick, int skillLongMaxCooldownTick, int burstMaxCooldownTick,
            float maxObtainingEnergy, String textureId,
            Map<ResourceLocation, Supplier<List<? extends Integer>>> statGrowthMap) {
        super(characterUUID, starRating, name,
                elementalId, ascendAttribute,
                skillShortMaxCooldownTick, skillLongMaxCooldownTick, burstMaxCooldownTick,
                maxObtainingEnergy, textureId, statGrowthMap);
    }
}