package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.core.character.polearm.shenhe.ShenheResources;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 两张脸<b>二选一</b>：常态用默认那张，闭眼姿势换备用那张。
 *
 * <h2>为什么是两张脸而不是两套眼睛</h2>
 * 母本（{@code 桑多涅1.2(Ysm2.6.3)/models/main.json}，694 骨）里脸部<b>并存两套网格</b>：
 *
 * <ul>
 *   <li>{@link #DEFAULT_FACE_BONE}（{@code MEyes}，parent {@code Head}）—— 现在游戏里实际显示的那张；</li>
 *   <li>{@link #STANDBY_FACE_ROOT}（{@code yushe}，parent {@code Face}，0 方块容器）——
 *       备用脸子树，挂着 9 个直接子骨：{@code kuqi1} / {@code kuqi2} / {@code jidon} / {@code wuyu}
 *       （哭脸、悸动、无语那几档）与 {@link #CLOSED_EYE_FACE_BONE}（{@code biyang1}，闭眼）、
 *       {@code biyang_Left} / {@code biyang_Right} / {@code biyang_RightJD} / {@code biyang_LeftJD}。</li>
 * </ul>
 *
 * 母本里谁出场是 <b>Molang 按 {@code face} 变量选</b>的（{@code biyang1} 对 {@code face == 5}）。
 * 而仓库这份 {@code shenhe.geo.json} 是「裁掉全部 Molang 查询与表情子树」的下游产物
 * （见 {@code CharacterRenderDispatcher} 里那段 ClassCastException 注释）——
 * 裁的时候把 {@code yushe} 整棵子树一起删了，所以在这之前引擎侧<b>根本没有闭眼脸可用</b>。
 * 先用 {@code tools/add-shenhe-face-bones.py} 从母本把 72 骨 / 420 方块补回 geo，
 * 再由这里接管显隐（几何格式没有可见性字段，改 geo 也没用，理由同
 * {@link CharacterAppearanceBones}）。
 *
 * <h2>常态：整棵备用脸一律不画</h2>
 * 藏的是 {@link #STANDBY_FACE_ROOT} 这一根，{@code skipChildrenRender} 连子树一起跳过 ——
 * 一次盖住全部 9 个直接子骨。只藏 {@code biyang*} 三档是不行的：
 * {@code kuqi*} / {@code jidon} / {@code wuyu} 也是 Molang 分档的，不藏就会一起糊在脸上。
 *
 * <h2>闭眼姿势：换成备用脸的闭眼档</h2>
 * 判据是动画状态名进 {@link ShenheResources#CLOSED_EYE_ANIMATIONS} 时反过来：
 * 藏 {@code MEyes}（连同它子树里真正的眼睛网格）、
 * 放行 {@code yushe} 但把它除 {@code biyang1} 以外的 8 个直接子骨逐个藏掉 ——
 * 于是画面上只剩备用脸那张闭眼的网格（含它自己的眉毛子骨）。
 */
public final class CharacterFaceBones {

    /** 常态在用的那张脸（parent {@code Head}，0 方块容器，眼睛网格在子树里）。 */
    public static final String DEFAULT_FACE_BONE = "MEyes";

    /** 备用脸的容器骨（parent {@code Face}，0 方块）—— 常态整棵藏掉。 */
    public static final String STANDBY_FACE_ROOT = "yushe";

    /** 备用脸里的闭眼档（母本 {@code face == 5}）。 */
    public static final String CLOSED_EYE_FACE_BONE = "biyang1";

    /**
     * 备用脸容器下<b>除 {@link #CLOSED_EYE_FACE_BONE} 以外</b>的直接子骨。
     *
     * <p>闭眼姿势里 {@code yushe} 要放行，所以这 8 根得逐个点名藏 ——
     * 名单对不上母本时不会报错，只是那一档会多露在脸上，加档时记得同步这里
     * （{@code rg -n '"parent": "yushe"' shenhe.geo.json} 可以数出全部直接子骨）。
     */
    public static final List<String> STANDBY_SIBLING_BONES = List.of(
            "kuqi1",             // 哭脸 A
            "kuqi2",             // 哭脸 B
            "jidon",             // 悸动
            "wuyu",              // 无语
            "biyang_Left",       // 备用脸的左眼单独档
            "biyang_Right",      // 备用脸的右眼单独档
            "biyang_RightJD",    // 上两档的抖动子骨
            "biyang_LeftJD");

    /** 常态：藏掉整棵备用脸，画面上就是默认那张。 */
    private static final RenderPassInfo.BoneUpdater<GeoRenderState> HIDE_STANDBY =
            (renderPassInfo, snapshots) -> snapshots.ifPresent(STANDBY_FACE_ROOT, snapshot -> {
                snapshot.skipRender(true);
                snapshot.skipChildrenRender(true);
            });

    /** 闭眼姿势：藏默认脸，只在备用脸里留 {@link #CLOSED_EYE_FACE_BONE}。 */
    private static final RenderPassInfo.BoneUpdater<GeoRenderState> SHOW_CLOSED_EYE =
            (renderPassInfo, snapshots) -> {
                snapshots.ifPresent(DEFAULT_FACE_BONE, snapshot -> {
                    snapshot.skipRender(true);
                    snapshot.skipChildrenRender(true);
                });
                for (String bone : STANDBY_SIBLING_BONES) {
                    snapshots.ifPresent(bone, snapshot -> {
                        snapshot.skipRender(true);
                        snapshot.skipChildrenRender(true);
                    });
                }
            };

    private CharacterFaceBones() {
    }

    /**
     * 这一帧按玩家当前动画状态选脸。
     *
     * <p>判据与坐骑 / 红茶同源（本地读状态机、远端读同步附件，见 {@link AnimationStateSync}）——
     * 联机时远端玩家摆闭眼姿势，这边读到的状态名同样是那一个。
     */
    public static RenderPassInfo.BoneUpdater<GeoRenderState> updaterFor(@Nullable Player player) {
        return updaterForState(player == null ? null : AnimationStateSync.stateOf(player));
    }

    /**
     * 同上，但判据直接给<b>动画状态名</b>。
     *
     * <p>页面预览那两条路不走玩家的动作状态机 —— 预览播的是页面自己点名的那条动画
     * （装备页 {@code extra_equip}，见 {@code CharacterEquipUI#PREVIEW_ANIMATION_EQUIP}），
     * 所以它得把「这一趟在播什么」直接传进来，不然预览里永远是默认那张脸。
     */
    public static RenderPassInfo.BoneUpdater<GeoRenderState> updaterForState(@Nullable String animationState) {
        return animationState != null && ShenheResources.CLOSED_EYE_ANIMATIONS.contains(animationState)
                ? SHOW_CLOSED_EYE
                : HIDE_STANDBY;
    }
}
