package com.linweiyun.genshin.client.combat.action;

import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.animation.config.DefaultCharacterAnimations;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.Vodyanitsa;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.Linweiyun;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.LinweiyunAnimations;
import com.linweiyun.genshin.core.character.polearm.shenhe.ShenheAnimations;
import com.linweiyun.genshin.core.character.polearm.shenhe.ShenheResources;
import com.linweiyun.genshin.core.character.claymore.sandrone.TestAnimations;
import com.linweiyun.genshin.core.character.claymore.sandrone.SandroneCharacter;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.character.sword.vesna.VesnaAnimations;

/**
 * 角色动作系统的登记表 —— <b>加角色就在这里加一行</b>。
 *
 * <p>对应参考2 的 {@code Characters.registerAll()}：渲染数据是数据驱动的（{@code XxxResources}），
 * 动画/动作是代码登记的（{@code XxxAnimations}）。
 *
 * <p>本项目所有角色共用同一个 {@link ResourceDrivenActionHandler}：动作时序来自
 * 各自的 {@code XxxResources.ACTION_DATA} → {@code XxxSkill.buildActionSet()}，
 * 所以只要角色有 {@code CharacterActionData}，动作就能跑起来，不需要每个角色写一套动作类。
 * 某个角色确实需要特殊编排时，把第二个参数换成自己的 {@code CharacterActionHandler} 实现即可。
 *
 * <p>由 {@code MinegenshinClient.onClientSetup} 调用（客户端专属）。
 */
public final class CharacterAnimationRegistry {

    private CharacterAnimationRegistry() {
    }

    public static void registerAll() {
        CharacterActions.register(Vesna.ID, ResourceDrivenActionHandler.INSTANCE, VesnaAnimations.INSTANCE);

        // 申鹤：有专属常态动画名（sneak / sneaking / swim_stand）和普攻 + 收尾的动画，
        // 所以走自己的 ShenheAnimations —— 动作编排仍然给通用的 ResourceDrivenActionHandler。
        CharacterActions.register(ShenheResources.ID, ResourceDrivenActionHandler.INSTANCE,
                ShenheAnimations.INSTANCE);

        // 林薇云：全武器角色。必须在这里登记 —— 否则她的普攻 / 战技 / 爆发全部静默失效
        // （见 registerPlaceholder 的注释），常态会退回兜底配置（兜底没有飞行三态，
        // 飞着上升会一直播 jump）。她自己的 LinweiyunAnimations 接了飞行。
        CharacterActions.register(Linweiyun.ID, ResourceDrivenActionHandler.INSTANCE,
                LinweiyunAnimations.INSTANCE);

        // test：长柄测试角色，动画复用林薇云那份，只是把飞行接过成长柄那三条。
        CharacterActions.register(SandroneCharacter.ID, ResourceDrivenActionHandler.INSTANCE,
                TestAnimations.INSTANCE);

        registerPlaceholder("arlecchino");
        registerPlaceholder("columbina");
        registerPlaceholder("raiden_shogun");
        // 沃雅妮莎：ACTION_DATA / 天赋都是全的，只是还没有专属动画名（先用通用常态动画）
        registerPlaceholder(Vodyanitsa.ID);
    }

    /**
     * 只保证模型能渲染、动画用通用常态配置的角色 —— <b>动作编排仍然给通用的
     * {@link ResourceDrivenActionHandler}</b>（时序来自各自的 {@code XxxResources.ACTION_DATA}）。
     *
     * <p>⚠️ <b>这里没登记 = 这个角色的普攻 / 战技 / 爆发全部静默失效</b>：
     * {@code CharacterActions.getFor(player)} 查不到就返回 {@code EMPTY} 编排，
     * 按键<b>不播动画、不发请求、一条日志都没有</b>（沃雅妮莎漏登记时就是这个症状）。
     * 加角色时别忘了在这里补一行。
     */
    private static void registerPlaceholder(String characterId) {
        CharacterActions.register(characterId, ResourceDrivenActionHandler.INSTANCE,
                DefaultCharacterAnimations.INSTANCE);
    }
}
