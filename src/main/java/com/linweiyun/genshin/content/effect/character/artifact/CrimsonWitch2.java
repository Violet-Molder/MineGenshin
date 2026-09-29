package com.linweiyun.genshin.content.effect.character.artifact;

import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class CrimsonWitch2 extends ArtifactSetEffect {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);
    @Override
    public void onEffectAdded(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        PGCharacterData data = character.getData();
        var CyroBonus = data.getAttribute(ModAttributes.PYRO_BONUS.get());
        CyroBonus.addFlatModifier("crimson_witch2", 0.18);
    }

    @Override
    public void onEffectRemoved(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        PGCharacterData data = character.getData();
        var CyroBonus = data.getAttribute(ModAttributes.PYRO_BONUS.get());
        if (CyroBonus == null) return;
        CyroBonus.removeFlatModifier("crimson_witch2");
    }


}