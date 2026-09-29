package com.linweiyun.genshin.core.character.polearm.test;

import com.linweiyun.genshin.config.character.LinweiyunAttributeConfig;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.linweiyun.genshin.core.character.polearm.PolearmCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 测试用长柄角色 <b>test</b>（UUID {@code 135009}）。
 *
 * <p>这是一个「骨架角色」：所有数值、资源、技能逻辑<b>全部照抄林薇云</b>
 * （见 {@link TestResources} / {@link TestSkillLogic}），
 * 目的是给 Photon 特效接线提供一个可控的落脚点 —— 改特效时不用去动任何一个正式角色。
 *
 * <table border="1">
 *   <caption>与林薇云的差异</caption>
 *   <tr><th>项</th><th>林薇云</th><th>test</th></tr>
 *   <tr><td>武器分类</td><td>全武器类（六形态）</td><td><b>长柄</b>（{@link PolearmCharacter}）</td></tr>
 *   <tr><td>UUID</td><td>105001</td><td><b>135009</b></td></tr>
 *   <tr><td>资源 id</td><td>linweiyun</td><td>test（模型/贴图/动画仍指向林薇云的目录）</td></tr>
 *   <tr><td>其它</td><td colspan="2">星级 / 元素 / 突破属性 / 冷却 / 充能 / 属性成长表 全部相同</td></tr>
 * </table>
 *
 * <p><b>资源</b>：{@code assets/minegenshin/character/test/} 目前不存在，
 * 所以 {@link TestResources#RENDER_DATA} 显式复用林薇云的模型 / 贴图 / 动画路径 ——
 * 这也是项目里「缺项就回退到兜底角色」那条规则的显式写法。
 * 以后给 test 做了自己的素材，只要把 {@code TestResources} 里那三行换成
 * {@code "character/test/test.geo.json"} 之类的路径即可，其它代码一行都不用动。
 */
public class TestCharacter extends PolearmCharacter {
    /** 资源目录名 / {@code CharacterRenderRepository} 的 key / 动画登记表的 key。 */
    public static final String ID = "test";
    /** 角色 UUID。 */
    public static final int UID = 135009;

    public TestCharacter() {
        super(
                UID,
                5,
                Component.translatable("character.name.test"),
                ModElements.ANEMO.getId().toString(),
                CharacterAscendAttribute.ATK,
                400,
                400,
                80.0F,
                ID,
                TestCharacter.statGrowthMap()
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
