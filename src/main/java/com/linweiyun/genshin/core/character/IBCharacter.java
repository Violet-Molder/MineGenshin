package com.linweiyun.genshin.core.character;

/**
 * 联动角色标记接口 —— 这一类角色的模型 / 动画 / 贴图不在本 MOD，而在对方模组（IB，
 * 命名空间 {@code imaginary_branch}）里。
 *
 * <h2>怎么用</h2>
 * <ol>
 *   <li>角色主类实现本接口；{@link #ibCharacterId()} 返回它在对方模组里的角色 id；</li>
 *   <li>在 {@code assets/minegenshin/character/&lt;id&gt;/resources.json} 里逐项声明模型 / 动画 /
 *       贴图 / 头像从哪里读，写法见 {@code CharacterResourceSources}；</li>
 *   <li>注册前先问一句「对方在不在」（{@code IBLink.isLoaded()}）—— 见 {@code ModCharacters}
 *       与 {@code CharacterAnimationRegistry}。</li>
 * </ol>
 *
 * <p>「对方不在时本角色不生效」是硬要求：对方不在时角色不注册、不登记动画、不进抽卡池，
 * 既不会出现半截渲染，也不会因为缺资源而报错。
 *
 * <h2>哪些资源不联动</h2>
 * 头像与立绘仍然归本 MOD（对方没有这两样）。头像可以直接指到对方的角色信物图标，
 * 见 {@code resources.json} 里的 {@code avatar} / {@code avatar_hud}。
 */
public interface IBCharacter {

    /**
     * 本角色在对方模组里的角色 id。
     *
     * <p>默认与自己的 id 同名（{@code PGCharacter.getTextureId()}）—— 「在对方项目里找和自己
     * 同名的角色」就是这个意思；只有两边名字对不上时才需要覆写。
     */
    default String ibCharacterId() {
        return ((PGCharacter) this).getTextureId();
    }
}
