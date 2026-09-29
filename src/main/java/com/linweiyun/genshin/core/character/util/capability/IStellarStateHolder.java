package com.linweiyun.genshin.core.character.util.capability;

/**
 * 「能进入星烁状态」—— 可以被挂上<b>辉映·星扩散 / 辉映·星超导</b>状态的角色。
 *
 * <h2>和户口的区别（重要）</h2>
 * 这个接口<b>不代表</b>转化能力，也不代表体系加成 —— 它只说「这个角色能持有星烁状态」。
 *
 * <ul>
 *   <li><b>有户口</b>（{@link IStellarHousehold}）：负责转化 + 给全队加成，通常也会给自己上状态；</li>
 *   <li><b>只有状态</b>：自己没户口，但天赋/ buff 让他能进入星烁状态并吃到对应加成
 *       （「有的角色可以进入月曜、星烁状态并获得加成，但本身并不具备户口天赋」就是这一类）。</li>
 * </ul>
 *
 * <p>状态本身用效果实现（{@code RadianceStellarSwirlEffect} / {@code RadianceStellarConduceEffect}），
 * 两者互斥且星超导优先 —— 见 {@code StellarGlimmer}。
 */
public interface IStellarStateHolder {

    /** 能不能被挂上星烁状态（默认 true；实现这个接口就是要能）。 */
    default boolean canHoldStellarState() {
        return true;
    }
}