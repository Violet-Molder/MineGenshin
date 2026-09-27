package com.linweiyun.genshin.client.render.character;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.optimize.GeoRenderIntercept;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * 角色模型渲染器。
 *
 * <h2>为什么要把父类的原点平移「改成 0」</h2>
 * 基类 {@link GeoObjectRenderer}（给「摆件」用的渲染器）在 {@code adjustRenderPose} 里
 * 有一句 {@code translate(0.5, 0.51, 0.5)} —— 那是给<b>以方块角为原点</b>导出的
 * 摆件模型准备的补偿。
 *
 * <p>而我们这套角色模型是<b>以原点为中心</b>导出的：所有 cube 的 origin 都对称于 x=0
 * （头 −3.5~3.5、躯干 −3~3、腿 ±1.9 单位），模型自带
 * {@code visible_bounds_offset = [0, 1.75, 0]}。人也确实站在方块中心 ——
 * 原版实体渲染交给我们的 pose 就只有「实体相对相机的位置」（见
 * {@code EntityRenderDispatcher.submit}），没有任何半格偏移。
 *
 * <p>所以那句 +0.5 会把模型整体推到斜后方半格（约 0.71 格），和判定箱、影子对不上。
 * GeckoLib 自己的实体渲染器（{@code GeoEntityRenderer} / {@code GeoReplacedEntityRenderer}）
 * 就<b>没有</b>这句平移，只有摆件渲染器有 —— 这也说明它是「摆件约定」，不是实体的。
 *
 * <p>覆盖成空实现后：模型正好落在实体位置上，第一人称那边也不需要再做
 * 「反向补偿半格」的换算。
 */
public class CharacterRenderer extends GeoObjectRenderer<GenshinReplacedPlayer, Player, GeoRenderState> {

    public CharacterRenderer(GeoModel<GenshinReplacedPlayer> model) {
        super(model);
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
        // 故意什么都不做：模型以原点为中心，不需要摆件渲染器那半格补偿。
        // （要调模型相对实体的位置就改这里，别去动 adjustRenderPose 的父类默认值）
    }

    /**
     * 几何提交 —— 本模组几何优化系统在角色侧的入口。
     *
     * <h2>为什么这里只剩两行</h2>
     * 真正的接管逻辑（GPU 蒙皮 / CPU 优化 / 回退判定）统一放在
     * {@link GeoRenderIntercept#trySubmit}：同一个入口也被 mixin 挂在
     * {@code GeoRenderer#submitRenderTasks} 这条接口 default 方法上，于是角色、本模组实体、
     * 以及其它模组的 GeckoLib 实体走的是<b>同一份代码</b>。这里保留覆写只是为了让角色
     * 不依赖「mixin 注入是否成功」——两者不会重复接管，因为角色覆写了这个方法，
     * 接口的 default 实现根本不会被调用。
     *
     * <p>{@code false} 只表示「这次什么都没提交」（目前只有 missing model 一种情况），
     * 此时按 GeckoLib 默认实现的逐句复刻走一遍。不能写 {@code super.submitRenderTasks(...)}：
     * {@code GeoRenderer} 的默认实现属于接口，而本类的直接父类是 {@code GeoObjectRenderer}
     * 这个类，Java 不允许它写 {@code GeoRenderer.super.submitRenderTasks(...)}。</p>
     */
    @Override
    public void submitRenderTasks(RenderPassInfo<GeoRenderState> renderPassInfo,
                                  OrderedSubmitNodeCollector renderTasks,
                                  @Nullable RenderType renderType) {
        if (!GeoRenderIntercept.trySubmit(renderPassInfo, renderTasks, renderType)) {
            GeoRenderIntercept.submitDefault(renderPassInfo, renderTasks, renderType);
        }
    }
}
