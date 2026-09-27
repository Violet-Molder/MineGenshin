package com.linweiyun.genshin.client.damage;

import com.linweiyun.genshin.api.damage.DamageIndicatorData;
import com.linweiyun.genshin.api.damage.DamageIndicatorSink;
import net.minecraft.world.phys.Vec3;

/** 客户端侧的飘字实现：把公共侧传来的数据变成渲染实体并加入活跃列表。 */
public final class DamageIndicatorClientBridge implements DamageIndicatorSink.Impl {

    private DamageIndicatorClientBridge() {}

    public static void install() {
        DamageIndicatorSink.install(new DamageIndicatorClientBridge());
    }

    @Override
    public void show(DamageIndicatorData data) {
        DamageIndicator indicator = new DamageIndicator(
                new Vec3(data.originX(), data.originY(), data.originZ()),
                new Vec3(data.targetX(), data.targetY(), data.targetZ()),
                data.text(),
                data.topColor(),
                data.bottomColor(),
                data.style(),
                data.italic(),
                data.baseScale(),
                data.startScale(),
                data.durationMs(),
                data.mergeKey()
        );
        DamageIndicatorManager.upsert(indicator, data.merge());
        DamageIndicatorRenderer.onIndicatorAdded(indicator);
    }
}
