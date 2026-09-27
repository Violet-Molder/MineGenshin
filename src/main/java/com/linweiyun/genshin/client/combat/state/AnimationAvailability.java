package com.linweiyun.genshin.client.combat.state;

import com.linweiyun.genshin.core.character.CharacterHelper;

import com.geckolib.cache.animation.BakedAnimations;
import com.linweiyun.genshin.client.render.geo.AssetFallback;
import com.linweiyun.genshin.client.render.geo.GenshinGeoCache;
import com.linweiyun.genshin.core.asset.GenshinAssets;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动画存在性校验 —— 防止把不存在的动画名交给 GeckoLib。
 *
 * <h2>为什么需要它</h2>
 * GeckoLib 拿到一个动画文件里没有的名字时，{@code AnimationTimeline.create()} 返回 null，
 * 控制器这一帧就不产生任何骨骼动画 —— 模型直接以「原始姿态」渲染：
 * 所有部件、附着物、特效全部露出来，角色呆站着不动，而且会一直持续到下一个存在的动画被设置为止。
 * 典型触发是资源里写了 {@code "burst"} 而 json 里叫 {@code "final"}。
 *
 * <p>所以任何切换之前都先问一句「这个动画真的存在吗」，不存在就<b>不切</b>
 * （保持当前动画），而不是切进一个空状态。
 *
 * <h2>判定方式</h2>
 * 直接查<b>已经烘培好的动画</b>，而不是让角色手写一份名单 ——
 * 手写名单正是上面那个 {@code "burst"} 事故的来源。两个来源都查：
 * <ol>
 *   <li>本 MOD 自己的缓存 {@link GenshinGeoCache}（{@code character/<角色id>/} 下的资源）；</li>
 *   <li>GeckoLib 的缓存（兼容仍放在 {@code geckolib/animations/} 下的资源）。</li>
 * </ol>
 *
 * <p>三种「无法判断」的情况一律放行，避免加载期把整套动画挡掉：
 * 拿不到角色 ID、角色没有渲染定义、动画文件还没烘培出来。
 * 只有「文件确实加载了、里面确实没有这个名字」才判定为不存在。
 */
public final class AnimationAvailability {

    /** 角色 ID → 该角色动画文件的动画名集合，缓存到动画文件换新为止。 */
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(Identifier primary, @Nullable BakedAnimations[] baked, Set<String> names) {
    }

    private AnimationAvailability() {
    }

    /** 某个动画名在玩家当前角色身上是否存在（含「无法判断 → 放行」）。 */
    public static boolean existsFor(Player player, @Nullable String animationName) {
        return exists(CharacterHelper.getActiveCharacterId(player), animationName);
    }

    /** 某个动画名在这个角色身上是否存在（含「无法判断 → 放行」）。 */
    public static boolean exists(@Nullable String characterId, @Nullable String animationName) {
        if (animationName == null || animationName.isEmpty()) {
            return false;
        }

        Set<String> names = namesFor(characterId);
        return names == null || names.contains(animationName);
    }

    /** 资源重载后清掉缓存。 */
    public static void invalidate() {
        CACHE.clear();
    }

    /**
     * @return 角色可用的动画名集合；{@code null} 表示「无法判断，别拦」
     */
    @Nullable
    private static Set<String> namesFor(@Nullable String characterId) {
        if (characterId == null || characterId.isEmpty()) {
            return null;
        }

        // 快路径：缓存还在、动画文件也没被重载过
        Cached cached = CACHE.get(characterId);
        if (cached != null && stillFresh(cached)) {
            return cached.names();
        }

        CharacterRenderData data = CharacterRenderRepository.get(characterId);
        if (data == null) {
            return null;
        }

        // 主动画文件先按「自己的目录 → 共用的 character/default/」定下来 ——
        // 与渲染时读的是同一条判据（见 AssetFallback），不然会出现
        // 「名字判定说存在、真正播的时候那个文件却没被读」的错位。
        // 之后再收额外动画文件（第一人称、动作包……）。
        List<Identifier> files = new java.util.ArrayList<>(data.allAnimationPaths().size() + 1);
        files.add(AssetFallback.animation(data.animationIdentifier(), GenshinAssets.defaultAnimation()));
        for (String extra : data.extraAnimationPaths()) {
            files.add(GenshinAssets.fromAnimationPath(extra));
        }

        Set<String> names = new java.util.LinkedHashSet<>();
        BakedAnimations[] baked = new BakedAnimations[files.size()];

        for (int i = 0; i < files.size(); i++) {
            baked[i] = GenshinGeoCache.animationFile(files.get(i));
            GenshinGeoCache.collectAnimationNames(files.get(i), null, names);
        }

        // 一份都没加载出来 → 无法判断
        boolean anyLoaded = false;
        for (BakedAnimations file : baked) {
            if (file != null) {
                anyLoaded = true;
                break;
            }
        }
        if (!anyLoaded || names.isEmpty()) {
            return null;
        }

        CACHE.put(characterId, new Cached(files.getFirst(), baked, Set.copyOf(names)));
        return Set.copyOf(names);
    }

    /** 缓存里的文件对象还是当前那几份吗（资源重载会换新对象）。 */
    private static boolean stillFresh(Cached cached) {
        BakedAnimations[] baked = cached.baked();
        if (baked == null || baked.length == 0) {
            return false;
        }
        for (BakedAnimations file : baked) {
            if (file == null) {
                continue;
            }
            return GenshinGeoCache.animationFile(cached.primary()) == file;
        }
        return false;
    }
}
