package com.linweiyun.genshin.data.generators;

import com.google.common.hash.Hashing;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.content.items.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 物品定义 / 平面模型的数据生成 —— <b>落在本项目自己的布局里</b>。
 *
 * <h2>产出什么</h2>
 * <pre>
 * assets/minegenshin/item/&lt;物品id&gt;/definition.json   物品定义（原版入口是 items/&lt;id&gt;.json）
 * assets/minegenshin/item/&lt;物品id&gt;/model.json        平面模型（原版入口是 models/item/&lt;id&gt;.json）
 * </pre>
 * 贴图是<b>资源</b>不是生成物，放在 {@code item/&lt;物品id&gt;/texture.png}，模型里的
 * {@code layer0} 直接引用它（sprite id {@code minegenshin:item/&lt;id&gt;/texture}）。
 *
 * <p>这三条路径都由 {@code asset.AssetRedirects} 在运行期映射回原版入口，
 * 所以数据生成也必须产出到这个布局 —— 否则下一次 {@code runData} 会在原版根目录下
 * 重建一份「旧的」文件，布局又变回两套。
 *
 * <h2>三种物品</h2>
 * <table border="1">
 *   <caption>按「模型从哪来」分类</caption>
 *   <tr><th>情况</th><th>产出</th></tr>
 *   <tr><td>有自己的贴图（普通平面物品）</td><td>definition.json + model.json</td></tr>
 *   <tr><td>借原版模型（漆黑碎片 = 下界之星）</td><td>只有 definition.json，指向 {@code minecraft:item/nether_star}</td></tr>
 *   <tr><td>模型是手写的（geo 物品 / 特殊渲染器）</td><td>{@link #HAND_WRITTEN_MODELS} 里的跳过，手写文件原样生效</td></tr>
 * </table>
 *
 * <h2>为什么不用原版 ModelProvider</h2>
 * 原版 {@code ModelProvider} 的产出路径是写死的 {@code items/} 与 {@code models/}
 * （{@code createPathProvider(RESOURCE_PACK, "items"/"models")}），够不到我们的布局；
 * 它那条「每个已注册物品都必须有定义」的校验这里等价实现：漏掉的物品当场抛异常。
 */
public class ModModeProvider implements DataProvider {

    /**
     * 定义 / 模型是<b>手写</b>的物品 —— 数据生成既不产出、也不校验它们。
     *
     * <p>判断依据：{@code src/main/resources/.../item/&lt;id&gt;/definition.json} 存在。
     * 这些物品的定义形状原版生成器写不出来（用 {@code minecraft:select} 按展示场景切模型），
     * 所以只能手写；生成器一旦也产出同名文件，两条源集会撞成
     * {@code duplicate but no duplicate handling strategy has been set}。
     *
     * <p><b>以后每加一个 geo 物品 / 自定义特殊渲染器 / 需要 select 形状定义的物品，
     * 都要往这里加一行。</b>
     */
    private static final Set<Item> HAND_WRITTEN_MODELS = Set.of(
            ModItems.BEYOND_THE_CHRYSALIS.get(),
            ModItems.HYMN_OF_THE_MAELSTROM.get(),
            ModItems.ADVENTURERS_EXPERIENCE.get(),
            ModItems.HEROS_WIT.get(),
            ModItems.WANDERERS_ADVICE.get());

    /** 借原版模型的物品：只写定义，不写自己的模型。 */
    private static final Set<Item> BORROWED_MODELS = Set.of(
            ModItems.DARK_FRAGMENT.get());

    /** 借用的原版模型 id。 */
    private static final String BORROWED_MODEL = "minecraft:item/nether_star";

    private final PackOutput.PathProvider definitionPathProvider;
    private final PackOutput.PathProvider modelPathProvider;
    private final String modId;

    public ModModeProvider(PackOutput output) {
        // 两个 PathProvider 的根目录都是 item/：file(id.withSuffix("/definition")) → item/<id>/definition.json
        this.definitionPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "item");
        this.modelPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "item");
        this.modId = Minegenshin.MOD_ID;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        Set<Item> handled = new HashSet<>();
        List<Identifier> missing = new ArrayList<>();

        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (!modId.equals(id.getNamespace())) {
                continue;
            }
            Item item = itemOf(id);
            if (item == null || HAND_WRITTEN_MODELS.contains(item)) {
                continue;
            }

            handled.add(item);
            if (BORROWED_MODELS.contains(item)) {
                writes.add(write(cache, this.definitionPathProvider.file(id.withSuffix("/definition"), "json"),
                        borrowedDefinition()));
            } else {
                writes.add(write(cache, this.definitionPathProvider.file(id.withSuffix("/definition"), "json"),
                        flatDefinition(id)));
                writes.add(write(cache, this.modelPathProvider.file(id.withSuffix("/model"), "json"),
                        flatModel(id)));
            }
        }

        // 等价于原版 ModelProvider 的 finalizeAndValidate：本命名空间的物品一个都不能漏
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (!modId.equals(id.getNamespace())) {
                continue;
            }
            Item item = itemOf(id);
            if (item != null && !HAND_WRITTEN_MODELS.contains(item) && !handled.contains(item)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing item model definitions for: " + missing);
        }

        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "MineGenshin Item Definitions (item/<id>/…)";
    }

    // ==================== 输出 ====================

    /** 按 id 取物品；取不到返回 null。 */
    private static Item itemOf(Identifier id) {
        return BuiltInRegistries.ITEM.get(id).map(reference -> reference.value()).orElse(null);
    }

    private static CompletableFuture<?> write(CachedOutput cache, @Nullable Path path, String json) {
        if (path == null) {
            return CompletableFuture.completedFuture(null);
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return CompletableFuture.runAsync(() -> {
            try {
                cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes));
            } catch (Exception e) {
                throw new RuntimeException("写入 " + path + " 失败", e);
            }
        });
    }

    /** 平面物品的定义：指向同目录的 model.json（经 AssetRedirects 变成 models/item/&lt;id&gt;/model.json）。 */
    private static String flatDefinition(Identifier id) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s"
                  }
                }
                """.formatted(Minegenshin.MOD_ID + ":item/" + id.getPath() + "/model");
    }

    /** 平面物品的模型：layer0 指向同目录的 texture.png。 */
    private static String flatModel(Identifier id) {
        return """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "%s"
                  }
                }
                """.formatted(Minegenshin.MOD_ID + ":item/" + id.getPath() + "/texture");
    }

    /** 借原版模型的物品：只写定义（与原版生成器的 ClientItem.Properties(false,false,1.0F) 产出一致）。 */
    private static String borrowedDefinition() {
        return """
                {
                  "hand_animation_on_swap": false,
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s"
                  }
                }
                """.formatted(BORROWED_MODEL);
    }
}
