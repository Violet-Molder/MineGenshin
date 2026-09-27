package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.config.character.ShenheAttributeConfig;
import com.linweiyun.genshin.core.character.claymore.ClaymoreCharacter;
import com.linweiyun.genshin.core.character.IStellarStateHolder;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.CharacterAscendAttribute;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 申鹤。
 *
 * <h2>⚠️ 当前是<b>临时</b>的大剑角色</h2>
 * 她本来应该是长枪（原神里就是），这里临时改成 {@link ClaymoreCharacter}
 * 是为了验收「大剑的持续型重击」这一套：
 * <ul>
 *   <li>她的技能因此也换成 {@code ShenheSkill extends ClaymoreSkill}
 *       —— 重击变成「按住进入持续状态、松手/到时结束」；</li>
 *   <li>{@link #getAllowedWeaponClass()} 会跟着变成大剑：<b>装备校验、武器面板筛选项、
 *       削韧/冲击查表</b>都按大剑走（这是临时改造的预期后果 —— 她暂时只能拿大剑，
 *       拿长枪会被拒）。</li>
 * </ul>
 *
 * <p>所以：<b>她的包名保持 {@code core.character.polearm.shenhe} 不动</b>。
 * 这层包名对应「按武器分类」的目录约定，跟着改一次要动五个文件和所有 import；
 * 等这个临时改动确定要留下来（她真的当大剑角色）时再一起搬，别在这一步做。
 */
public class Shenhe extends ClaymoreCharacter implements IStellarStateHolder {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);
    public static final String ID = "shenhe";
    public Shenhe() {
        // ⚠️ 这里是「三冷却」重载（11 个参数）：skillShort=200 / skillLong=300 /
        //    burst=200 / 最大能量=80。别数错成两冷却重载 —— short ≠ long 是
        //    「E 长按有效果」的前提（ActionStateMachine.hasSkillHoldVariant 就是比这两个）。
        super(135001, 5, Component.translatable("character.name.shenhe"),
                ModElements.CYRO.getId().toString(), CharacterAscendAttribute.ATK,
                10 * 20, 15 * 20, 10 * 20, 80f, "shenhe",
                Map.of(
                        ModAttributes.MAX_HP.getId(), ShenheAttributeConfig::getAllHp,
                        ModAttributes.ATK.getId(), ShenheAttributeConfig::getAllAtk,
                        ModAttributes.DEF.getId(), ShenheAttributeConfig::getAllDef
                ));
        // 三个协作者都在无参构造器里建：客户端反序列化走
        // clazz.getDeclaredConstructor().newInstance()（会跑到这里），双端都拿得到实例。
        this.skill = new ShenheSkill();
        this.talent = new ShenheTalent();
        this.constellation = new ShenheConstellation();
        // 配置页（按键 N）：页面实例本身是「无状态的构造器」，控件树每次打开重建
        this.configUI = new ShenheConfigUI();
        // 外观数据：她的掩码是「每条腿的袜子 + 鞋 + 猫耳」，读法收在她自己这一类里
        this.appearanceData = com.linweiyun.genshin.core.character.appearance.ShenheAppearanceData.INSTANCE;
        CharacterRenderRepository.register(ShenheResources.RENDER_DATA);
    }

    @Override
    public com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData getActionData() {
        return ShenheResources.ACTION_DATA;
    }

    /**
     * 她的 E / 重击是「技能自己推位移」（{@code DashSystem}：客户端按格走、服务端扫伤害），
     * 所以客户端也要本地跑一次天赋钩子 —— 否则那段突刺在客户端永远不会被调用。
     */
    @Override
    public boolean runsTalentOnClient() {
        return true;
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
