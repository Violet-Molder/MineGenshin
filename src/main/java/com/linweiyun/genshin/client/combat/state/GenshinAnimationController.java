package com.linweiyun.genshin.client.combat.state;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;

/** 支持「下一条动画从第 N 刻起播」的控制器：用于下落攻击落地后续播。 */
public class GenshinAnimationController<T extends GeoAnimatable> extends AnimationController<T> {

    private double pendingStartTick = -1.0;

    public GenshinAnimationController(T animatable, String name, int transitionTicks,
                                      AnimationStateHandler<T> handler) {
        super(animatable, name, transitionTicks, handler);
    }

    public void playFromTick(double startTick) {
        this.pendingStartTick = Math.max(0.0, startTick);
    }

    @Override
    protected double adjustTick(double tick) {
        if (pendingStartTick >= 0.0) {
            double start = pendingStartTick;
            pendingStartTick = -1.0;
            this.shouldResetTick = false;
            this.tickOffset = tick - start;
            return this.animationSpeedModifier.apply(this.animatable) * Math.max(tick - this.tickOffset, 0.0);
        }
        return super.adjustTick(tick);
    }
}