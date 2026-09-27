package com.linweiyun.genshin.core.system.combat.action.data;


import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.LinweiyunResources;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色渲染定义注册表。
 * <p>
 * 各角色在自己的包里定义 {@link CharacterRenderData}，通过 {@link #register(CharacterRenderData)} 注册。
 * 注册时机：角色类的 static 初始化块或构造函数中。
 * <p>
 * 查询时按角色 ID（与 PGCharacter.textureId 一致）匹配。
 *
 * <p><b>没登记的角色也有渲染定义</b>：{@link #get(String)} 会给它现算一套标准的
 * （{@link CharacterRenderData#character}），三项资源都指向角色自己的目录、
 * 缺的由读取侧借 {@code character/default/}。所以渲染这件事是零配置的 ——
 * 把资源放进 {@code character/&lt;角色id&gt;/} 就生效；
 * 登记的那一份只用来声明<b>挂点、额外动画文件、半透明骨骼、署名</b>这类额外信息。
 */
public final class CharacterRenderRepository {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);
    private static final Map<String, CharacterRenderData> REGISTRY = new LinkedHashMap<>();

    /** 没有自己那一套的角色 → 现算出来的标准定义；同一个 id 只算一次。 */
    private static final Map<String, CharacterRenderData> STANDARD = new ConcurrentHashMap<>();

    private CharacterRenderRepository() {}

    /**
     * 注册一个角色的渲染定义。通常在角色构造函数中调用。
     * 重复注册同一 ID 会被忽略（先到先得）。
     */
    public static void register(CharacterRenderData data) {
        if (data == null || data.id() == null || data.id().isEmpty()) return;
        if (REGISTRY.containsKey(data.id())) return;
        REGISTRY.put(data.id(), data);
        LOGGER.debug("[CharacterRender] 注册角色渲染: {}", data.id());
    }

    /**
     * 取某个角色的渲染定义；没人登记过就现算一套标准的。
     *
     * <p>返回 {@code null} 只有一种情况：传进来的 id 是空的（没戴角色饰品）。
     */
    public static CharacterRenderData get(String id) {
        if (id == null || id.isEmpty()) return null;
        CharacterRenderData registered = REGISTRY.get(id);
        return registered != null ? registered
                : STANDARD.computeIfAbsent(id, CharacterRenderRepository::standardFor);
    }

    /**
     * 标准定义：三项都指向角色自己的目录，缺的由读取侧借 {@code character/default/}。
     * 没有挂点、没有额外动画文件 —— 就是「这个角色的资源按统一布局摆」。
     *
     * <p><b>署名按"共用模型的作者"给</b>：这一支借的就是林薇云那套模型，
     * 所以作者栏（配置页左下那行）也要写上那一位，而不是留空。
     */
    private static CharacterRenderData standardFor(String id) {
        LOGGER.info("[CharacterRender] 角色 '{}' 没有登记自己的渲染定义，按标准的一套渲染"
                + "（自己目录优先，缺的借 character/linweiyun/）", id);
        return CharacterRenderData.character(id, CharacterRenderData.defaultAnimMapping(), 1.0f)
                .withModelAuthor(LinweiyunResources.MODEL_AUTHOR, LinweiyunResources.MODEL_AUTHOR_URL);
    }

    public static Map<String, CharacterRenderData> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public static int size() { return REGISTRY.size(); }
}
