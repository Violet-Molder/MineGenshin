package com.linweiyun.genshin.core.character.catalyst.columbina;

import com.linweiyun.genshin.config.character.ShenheAttributeConfig;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.catalyst.CatalystCharacter;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Columbina extends CatalystCharacter {

    public Columbina() {
        super(145001, 5, Component.translatable("character.name.columbina"),
                "minegenshin:hydro", CharacterAscendAttribute.ATK,
                17 * 20, 20 * 20, 80f, "columbina",
                Map.of(
                        ModAttributes.MAX_HP.getId(), ShenheAttributeConfig::getAllHp,
                        ModAttributes.ATK.getId(), ShenheAttributeConfig::getAllAtk,
                        ModAttributes.DEF.getId(), ShenheAttributeConfig::getAllDef
                ));
        // 三个协作者都在无参构造器里建（客户端反序列化走 newInstance()，会跑到这里）。
        this.skill = new ColumbinaSkill();
        this.talent = new ColumbinaTalent();
        this.constellation = new ColumbinaConstellation();
    }

    /**
     * 数据驱动的动作配置 —— 客户端与服务端都从这一份表构建 {@code ActionSet}
     * （时序 / 动画名 / 远程索敌形态），伤害仍在 {@link ColumbinaSkill} 里结算。
     *
     * <p>不覆写这个方法的话会落到 {@code SkillBase} 的通用兜底表（那一份是近战形态），
     * 法器角色的射程就用不上了。
     */
    @Override
    public CharacterActionData getActionData() {
        return ColumbinaResources.ACTION_DATA;
    }

    @Override
    public Map<Identifier, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return Map.of(
                ModAttributes.MAX_HP.getId(), ShenheAttributeConfig::getAllHp,
                ModAttributes.ATK.getId(), ShenheAttributeConfig::getAllAtk,
                ModAttributes.DEF.getId(), ShenheAttributeConfig::getAllDef
        );
    }
}