package com.linweiyun.genshin.asset.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.linweiyun.genshin.core.system.combat.action.data.BoneMountSource;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterBoneMount;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一个角色模型的「骨骼约定」—— 本体长在哪根骨骼下、武器挂在哪根骨骼上、还有哪些骨骼要额外藏掉。
 *
 * <h2>为什么必须有这一条</h2>
 * 本 MOD 自己的模型有一套固定约定：本体根骨骼叫 {@code allbody}，武器骨骼叫 {@code weapon}，
 * 渲染时只保留 {@code allbody} 子树、其余一律藏掉（见 {@code CharacterBoneVisibility}）。
 * 联动角色的模型是<b>对方的</b>，名字与层级由对方决定（例如对方的星见雅本体根叫 {@code bone2}），
 * 我们不能改对方的模型，所以只能在这里声明。
 *
 * <h2>{@code resources.json} 里的写法</h2>
 * <pre>
 * "bones": {
 *   "body_root": "bone2",                       // 本体根骨骼；省略 = 本 MOD 约定 allbody，"*" = 不裁剪（全渲染）
 *   "weapon":   ["blade_right"],                // 武器挂点，可给多个；字符串 = 只给骨骼名
 *   "hide":     ["some_prop"]                   // 额外要藏掉的骨骼（可选）
 * }
 * </pre>
 * 武器挂点也可以写成对象，用来微调摆放（物品模型通常大小/朝向都不对，需要现场调）：
 * <pre>
 * "weapon": [
 *   {
 *     "bone": "blade_right",
 *     "source": "weapon",                       // 默认就是武器槽；"weapon_sub_bone:blade" 取武器的某一根骨骼
 *     "scale": 1.0,
 *     "offset": [0, 0, 0],                      // 像素，1 格 = 16
 *     "rotation": [0, 0, 0]                     // 度
 *   }
 * ]
 * </pre>
 *
 * @param bodyRoot    本体根骨骼名；{@code null} = 不裁剪（整棵模型都渲染）
 * @param conventional 是否按本 MOD 的模型约定解释（本体根 {@code allbody}、武器骨骼 {@code weapon}）。
 *                     只有<b>没写</b> {@code body_root} 时才是 true：写了别的名字说明这个模型不吃本 MOD 的约定，
 *                     此时不再自动隐藏名为 {@code weapon} 的骨骼
 * @param weaponBones 武器骨骼挂点
 * @param hidden      额外要藏掉的骨骼名
 */
public record CharacterBoneSpec(@Nullable String bodyRoot, boolean conventional,
                                List<CharacterBoneMount> weaponBones, Set<String> hidden) {

    /** 本 MOD 自己的模型约定。 */
    public static final String DEFAULT_BODY_ROOT = "allbody";

    /** 没写 {@code bones} 段时的结果：沿用本 MOD 约定。 */
    public static final CharacterBoneSpec DEFAULT =
            new CharacterBoneSpec(DEFAULT_BODY_ROOT, true, List.of(), Set.of());

    /** 解析 {@code resources.json} 的 {@code bones} 段；没有这一段时返回 {@link #DEFAULT}。 */
    public static CharacterBoneSpec parse(@Nullable JsonObject bones) {
        if (bones == null) {
            return DEFAULT;
        }

        String bodyRoot = DEFAULT_BODY_ROOT;
        boolean conventional = true;
        if (bones.has("body_root")) {
            JsonElement raw = bones.get("body_root");
            String text = raw.isJsonNull() ? null : raw.getAsString().trim();
            bodyRoot = text == null || text.isEmpty() || "*".equals(text) ? null : text;
            conventional = false;
        }

        return new CharacterBoneSpec(bodyRoot, conventional, parseWeapons(bones.get("weapon")), parseHidden(bones.get("hide")));
    }

    private static List<CharacterBoneMount> parseWeapons(@Nullable JsonElement raw) {
        if (raw == null || !raw.isJsonArray()) {
            return List.of();
        }

        List<CharacterBoneMount> mounts = new ArrayList<>();
        for (JsonElement entry : raw.getAsJsonArray()) {
            if (entry.isJsonPrimitive()) {
                mounts.add(CharacterBoneMount.of(entry.getAsString()));
            } else if (entry.isJsonObject()) {
                CharacterBoneMount mount = parseMount(entry.getAsJsonObject());
                if (mount != null) {
                    mounts.add(mount);
                }
            }
        }
        return List.copyOf(mounts);
    }

    @Nullable
    private static CharacterBoneMount parseMount(JsonObject json) {
        if (!json.has("bone") || json.get("bone").isJsonNull()) {
            return null;
        }

        String bone = json.get("bone").getAsString();
        CharacterBoneMount mount = json.has("source") && !json.get("source").isJsonNull()
                ? CharacterBoneMount.of(bone, sourceOf(json.get("source").getAsString()))
                : CharacterBoneMount.of(bone);

        if (json.has("scale") && json.get("scale").isJsonPrimitive()) {
            mount = mount.withScale(json.get("scale").getAsFloat());
        }

        float[] offset = triple(json.get("offset"));
        if (offset != null) {
            mount = mount.withOffset(offset[0], offset[1], offset[2]);
        }

        float[] rotation = triple(json.get("rotation"));
        if (rotation != null) {
            mount = mount.withRotation(rotation[0], rotation[1], rotation[2]);
        }

        return mount;
    }

    /** {@code "weapon"}（默认）或 {@code "weapon_sub_bone:<骨骼名>"}。 */
    private static BoneMountSource sourceOf(String raw) {
        String text = raw == null ? "" : raw.trim();
        String prefix = "weapon_sub_bone:";
        return text.startsWith(prefix)
                ? BoneMountSource.weaponSubBone(text.substring(prefix.length()).trim())
                : BoneMountSource.WEAPON_SLOT;
    }

    private static Set<String> parseHidden(@Nullable JsonElement raw) {
        if (raw == null || !raw.isJsonArray()) {
            return Set.of();
        }

        Set<String> hidden = new LinkedHashSet<>();
        for (JsonElement entry : raw.getAsJsonArray()) {
            if (entry.isJsonPrimitive()) {
                hidden.add(entry.getAsString());
            }
        }
        return Set.copyOf(hidden);
    }

    @Nullable
    private static float[] triple(@Nullable JsonElement raw) {
        if (raw == null || !raw.isJsonArray()) {
            return null;
        }

        JsonArray array = raw.getAsJsonArray();
        if (array.size() < 3) {
            return null;
        }

        return new float[]{
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        };
    }
}
