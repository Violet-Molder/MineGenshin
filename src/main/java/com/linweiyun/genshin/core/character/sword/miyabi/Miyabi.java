package com.linweiyun.genshin.core.character.sword.miyabi;

import com.linweiyun.genshin.core.character.IBCharacter;
import com.linweiyun.genshin.core.character.sword.SwordCharacter;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.character.talent.ConstellationBase;
import com.linweiyun.genshin.core.character.util.capability.IStellarHousehold;
import com.linweiyun.genshin.core.character.util.capability.IStellarStateHolder;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 星见雅（Miyabi）—— 风元素五星单手剑，<b>联动角色</b>：模型 / 动画 / 贴图来自对方模组（IB）。
 *
 * <h2>资源</h2>
 * 本 MOD 里没有她的美术资源，只有一份说明「每样从哪读」的来源表：
 * {@code assets/minegenshin/character/miyabi/resources.json}。
 * 模型 / 动画 / 贴图指到对方，头像用对方的角色信物图标，立绘留给我们自己。
 *
 * <h2>对方不在时</h2>
 * 角色不注册（见 {@code ModCharacters}），因此这个类只会在对方也在的实例里出现。
 * 类本身不引用对方的任何类型，所以对方不在时本 MOD 照样能构建、能启动。
 *
 * <h2>分工</h2>
 * 对方只提供「门禁（角色在不在）+ 美术素材（模型 / 贴图 / 动画文件 / 信物图标）」；
 * 动画系统、动作时序、骨骼规则、反应户口全部是本 MOD 的。
 *
 * <h2>数值与招式</h2>
 * 成长表暂时照抄薇斯娜（同为五星单手剑）；动作表见 {@link MiyabiResources}，
 * 招式微调见 {@link MiyabiSkill}。
 */
public class Miyabi extends SwordCharacter implements IBCharacter, IStellarHousehold, IStellarStateHolder {

    /** 资源目录名 / 渲染登记表的键 / 动画登记表的键。 */
    public static final String ID = "miyabi";

    /** 角色 UID。 */
    public static final int UID = 115201;

    /** 她在对方模组里的角色 id —— 与本角色同名，写出来是为了让这条联动一眼可见。 */
    public static final String IB_ID = "miyabi";

    public Miyabi() {
        super(
                UID,
                5,
                Component.translatable("character.name.miyabi"),
                ModElements.CYRO.getId().toString(),
                CharacterAscendAttribute.CDG,
                20 * 20,
                15 * 20,
                60.0F,
                ID,
                statGrowthMap()
        );
        this.skill = new MiyabiSkill();
        this.talent = new MiyabiTalent();
        this.constellation = new ConstellationBase();
        CharacterRenderRepository.register(MiyabiResources.RENDER_DATA);
    }

    @Override
    public String ibCharacterId() {
        return IB_ID;
    }

    @Override
    public CharacterActionData getActionData() {
        return MiyabiResources.ACTION_DATA;
    }

    @Override
    public Map<ResourceLocation, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return statGrowthMap();
    }

    /**
     * 星见雅的<b>星超导户口</b>：本角色在队伍里时星超导成立，并按攻击力给全队基础伤害提升
     * （实现在 {@link MiyabiTalent#stellarHousehold}）。
     */
    @Override
    public IStellarHousehold.StellarHousehold stellarHousehold() {
        return getTalent() instanceof MiyabiTalent talent ? talent.stellarHousehold(this) : null;
    }

    /** 成长表暂时指向薇斯娜那份（同为五星单手剑），改她的配置会同时影响两边。 */
    private static Map<ResourceLocation, Supplier<List<? extends Integer>>> statGrowthMap() {
        return Map.of(
                ModAttributes.MAX_HP.getId(), Vesna::getAllHp,
                ModAttributes.ATK.getId(), Vesna::getAllAtk,
                ModAttributes.DEF.getId(), Vesna::getAllDef
        );
    }
}
