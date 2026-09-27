package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.client.render.geo.GenshinGeoModel;
import com.linweiyun.genshin.core.asset.GenshinAssets;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import lombok.Getter;
import org.slf4j.Logger;

/**
 * 角色模型 —— 一个角色一份，路径全部来自 {@link CharacterRenderData}。
 *
 * <p>三个资源方法的实现、路径规则链、以及「先查本 MOD 自己的缓存」都在
 * {@link GenshinGeoModel} 里，这里只负责把渲染数据和角色 id 递进去。
 *
 * <p>布局：{@code assets/minegenshin/character/<角色id>/} 下的
 * {@code <基名>.geo.json} / {@code <基名>.animation.json} / {@code <基名>.png}。
 */
public class CharacterPlayerModel extends GenshinGeoModel<GenshinReplacedPlayer> {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    @Getter
    private CharacterRenderData renderData;

    public CharacterPlayerModel() {
        super();
    }

    /**
     * 切换角色：换 id 的同时按新角色重算三项目标路径。
     *
     * <p>读取时的候选链是三档，三项各自独立判断（判据见 {@code AssetFallback}）：
     * <ol>
     *   <li><b>自己目录</b> {@code character/<角色id>/…}（{@code setCharacterId} 按 id 算出来）；</li>
     *   <li><b>角色数据里声明的那条</b>（允许和 id 不同名）；</li>
     *   <li><b>共用目录</b> {@code character/default/…}。</li>
     * </ol>
     * 所以「还没画自己的模型 / 只做了模型、贴图以后再补」都不需要改代码：
     * 文件放进角色目录就自动生效，没放就往下借。
     */
    public void updateRenderData(CharacterRenderData data) {
        if (data == null) {
            return;
        }
        this.renderData = data;

        // ① 主选：角色自己目录（character/<角色id>/…），同时让默认路径规则认得出「这是哪个角色」
        setCharacterId(data.id());
        // ② 角色数据里声明的那条
        setDeclaredPaths(data.modelIdentifier(), data.textureIdentifier(), data.animationIdentifier());
        // ③ 共用目录：character/default/ 那一套
        setSharedPaths(GenshinAssets.defaultModel(), GenshinAssets.defaultTexture(),
                GenshinAssets.defaultAnimation());
        // 额外动画文件（第一人称、动作包……）
        setAnimationFallbackPaths(data.extraAnimationPaths());

        LOGGER.info("[CharacterPlayerModel] 角色 '{}' 路径: 自己={}/{}/{}，声明={}/{}/{}，共用={}/{}/{}，额外动画={}",
                data.id(), defaultModelResource(), defaultTextureResource(), defaultAnimationResource(),
                data.modelIdentifier(), data.textureIdentifier(), data.animationIdentifier(),
                GenshinAssets.defaultModel(), GenshinAssets.defaultTexture(), GenshinAssets.defaultAnimation(),
                data.extraAnimationPaths());
    }
}
