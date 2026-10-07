package com.linweiyun.genshin.client.render.character.bones;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 本次渲染能改到的骨骼集合。
 *
 * <p>按名字取骨骼；名字不存在时静默跳过 —— 模型换名或换骨骼都不应该让整帧崩掉。
 */
public final class BoneSnapshots {

    private final BakedGeoModel model;

    public BoneSnapshots(BakedGeoModel model) {
        this.model = model;
    }

    /** 底层模型；没有模型时为空表。 */
    public BakedGeoModel model() {
        return model;
    }

    /** 按名字取骨骼；没有这块骨头就返回空。 */
    public Optional<BoneSnapshot> get(String name) {
        if (model == null || name == null) {
            return Optional.empty();
        }
        return model.getBone(name).map(BoneSnapshot::new);
    }

    /** 有这块骨头才执行；没有就什么都不做。 */
    public void ifPresent(String name, Consumer<BoneSnapshot> consumer) {
        get(name).ifPresent(consumer);
    }

    /** 本模型里所有骨骼（扁平化，含各级子骨骼）。 */
    public List<GeoBone> bones() {
        List<GeoBone> out = new ArrayList<>();
        if (model != null) {
            for (GeoBone root : model.topLevelBones()) {
                collect(root, out);
            }
        }
        return out;
    }

    private static void collect(GeoBone bone, List<GeoBone> out) {
        out.add(bone);
        for (GeoBone child : bone.getChildBones()) {
            collect(child, out);
        }
    }

    public boolean containsKey(String name) {
        return get(name).isPresent();
    }

    /** 本模型里挂着的骨骼数量（顶层骨骼数，仅供日志/调试）。 */
    public int size() {
        return model == null ? 0 : model.topLevelBones().size();
    }
}