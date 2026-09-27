package com.linweiyun.genshin.client.render.character;

import com.geckolib.animatable.manager.AnimatableManager;
import com.linweiyun.genshin.client.combat.state.PlayerAnimationController;
import org.jetbrains.annotations.Nullable;

/**
 * 配置页预览专用的动画代理：<b>只播一条固定的动画</b>，默认是常态（idle）。
 *
 * <p>和 {@link GenshinReplacedPlayer} 的唯一区别就是控制器 —— 那份跟着玩家的真实状态走
 * （见 {@link PlayerAnimationController#create}），开页面之前刚打完一招就会看到定格在
 * 最后一帧的攻击姿势。预览要的是「站在原地」的样子，用
 * {@link PlayerAnimationController#createIdleOnly} 单独给一条控制器。
 *
 * <p>想给某个角色来一条专属姿势（例如申鹤的 {@code extra48}「闲置-喝茶」）就用
 * {@link #GenshinPreviewPlayer(String)} 点名：那一条在当前角色的动画文件里不存在时，
 * 控制器会自己退回 idle，页面不用管。
 *
 * <p>独立实例还顺带解决两件事：预览的动画进度和世界里的那份互不影响；
 * 关掉页面不需要任何收尾（共享实例的话就得把状态还回去，还容易还错）。
 */
public class GenshinPreviewPlayer extends GenshinReplacedPlayer {

    /** 点名要播的那条动画；{@code null} = 角色的常态（idle）。 */
    @Nullable
    private final String previewAnimation;

    /** 只播常态（idle）。 */
    public GenshinPreviewPlayer() {
        this(null);
    }

    /**
     * 点名播某一条动画。
     *
     * @param previewAnimation 动画名（如 {@code "extra48"}）；{@code null} = 常态（idle）
     */
    public GenshinPreviewPlayer(@Nullable String previewAnimation) {
        this.previewAnimation = previewAnimation;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 控制器是首次取 AnimatableManager 时才注册的（晚于构造器），这里读到的名字一定是构造时定下的那份
        controllers.add(PlayerAnimationController.createPreview(this, previewAnimation));
    }
}
