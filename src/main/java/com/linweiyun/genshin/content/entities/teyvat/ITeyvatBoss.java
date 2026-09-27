package com.linweiyun.genshin.content.entities.teyvat;

import com.linweiyun.genshin.core.system.control.ControlRequest;
import com.linweiyun.genshin.core.system.control.ControlService;

/**
 * 首领 —— 只覆盖<b>一个方法</b>就够了：{@link #blocksControl}。
 *
 * <p>它把「破韧期间随便一下都能打断」那条规则挡在门外：首领<b>破韧也照样拦</b>。
 * 想给它一次控制，只有两条路：
 * <ul>
 *   <li>{@link ControlService#openGap}：开一个<b>一次性破绽窗口</b>，
 *       接下来那一下命中从正常入口通过（技能在抬手 / 蓄力 / 收招 / 破盾瞬间调一下）；</li>
 *   <li>{@link ControlService#force}：<b>直通口</b>，绕过入口直接执行内置的
 *       打断动作 / 击退（剧情、处决演出、调试用）。</li>
 * </ul>
 *
 * <p>削韧那一半不在这里：首领的 {@code super_armor} 默认
 * {@code PoiseTiers.BOSS_SUPER_ARMOR}（远高于普通生物），所以它也更难被打空韧性条。
 * 要做一个「能被打断的首领」，把 {@link #blocksControl} 覆盖回 {@code false} 即可。
 */
public interface ITeyvatBoss extends TeyvatLiving {

    @Override
    default boolean blocksControl(ControlRequest request) {
        return true;
    }
}
