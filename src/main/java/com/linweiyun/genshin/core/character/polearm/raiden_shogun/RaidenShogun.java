package com.linweiyun.genshin.core.character.polearm.raiden_shogun;

import com.linweiyun.genshin.config.character.ShenheAttributeConfig;
import com.linweiyun.genshin.core.character.polearm.PolearmCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class RaidenShogun extends PolearmCharacter {
    public RaidenShogun() {
        super(135003, 5, Component.translatable("character.name.raiden_shogun"),
            ModElements.ELECTRO.getId().toString(), CharacterAscendAttribute.ATK,
            10 * 20,  10 * 20, 80f, "raiden_shogun",
            Map.of(
                    ModAttributes.MAX_HP.getId(), ShenheAttributeConfig::getAllHp,
                    ModAttributes.ATK.getId(), ShenheAttributeConfig::getAllAtk,
                    ModAttributes.DEF.getId(), ShenheAttributeConfig::getAllDef
            ));
        // 三个协作者都在无参构造器里建（客户端反序列化走 newInstance()，会跑到这里）。
        this.skill = new RaidenShogunSkill();
        this.talent = new RaidenShogunTalent();
        this.constellation = new RaidenShogunConstellation();
    }

    /**
     * 数据驱动的动作配置 —— 客户端与服务端都从这一份表构建 {@code ActionSet}
     * （时序 / 动画名 / 索敌形态），伤害仍在 {@link RaidenShogunSkill} 里结算。
     *
     * <p>不覆写这个方法的话会落到 {@code SkillBase} 的通用兜底表；接上自己的表之后
     * 角色才真正接进当前的动作系统。
     */
    @Override
    public CharacterActionData getActionData() {
        return RaidenShogunResources.ACTION_DATA;
    }

    @Override
    public Map<ResourceLocation, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return Map.of(
                ModAttributes.MAX_HP.getId(), ShenheAttributeConfig::getAllHp,
                ModAttributes.ATK.getId(), ShenheAttributeConfig::getAllAtk,
                ModAttributes.DEF.getId(), ShenheAttributeConfig::getAllDef
        );
    }
}
