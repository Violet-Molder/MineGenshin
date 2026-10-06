package com.linweiyun.genshin.core.character.claymore.sandrone;

import com.linweiyun.genshin.config.character.LinweiyunAttributeConfig;
import com.linweiyun.genshin.core.character.claymore.ClaymoreCharacter;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SandroneCharacter extends ClaymoreCharacter {
    /** 资源目录名 / {@code CharacterRenderRepository} 的 key / 动画登记表的 key。 */
    public static final String ID = "sandrone";
    /** 角色 UUID。 */
    public static final int UID = 125001;

    public SandroneCharacter() {
        super(
                UID,
                5,
                Component.translatable("character.name.sandrone"),
                ModElements.ANEMO.getId().toString(),
                CharacterAscendAttribute.ATK,
                400,
                400,
                80.0F,
                ID,
                SandroneCharacter.statGrowthMap()
        );
        this.skill = new TestSkill();
        CharacterRenderRepository.register(TestResources.RENDER_DATA);
    }

    @Override
    public CharacterActionData getActionData() {
        return TestResources.POLEARM_ACTION_DATA;
    }

    @Override
    public Map<Identifier, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return statGrowthMap();
    }

    /** 属性成长表 —— 直接指向林薇云那份配置（数值完全一致，改 TOML 就同时生效）。 */
    private static Map<Identifier, Supplier<List<? extends Integer>>> statGrowthMap() {
        return Map.of(
                ModAttributes.MAX_HP.getId(), LinweiyunAttributeConfig::getAllHp,
                ModAttributes.ATK.getId(), LinweiyunAttributeConfig::getAllAtk,
                ModAttributes.DEF.getId(), LinweiyunAttributeConfig::getAllDef
        );
    }
}
