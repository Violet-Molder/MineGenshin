package com.linweiyun.genshin.core.character.claymore.sandrone;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.render.character.appearance.CharacterBonePhysics;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.world.entity.player.Player;

/**
 * sandrone 的布料重力物理（免注册）。
 *
 * <p>只做「向下垂 + 拖尾」：布料永远被重力往下/往身后带，绝不上翻。
 * 起步/急停时因惯性出现轻微滞后回摆，匀速 walk/run 时接近归零（不受扰）。
 * BackButterfly 下是链式尾流：BackAsideButterfly → 左/右 …Butterfly →(+2)。</p>
 */
public class SandroneBonePhysics extends CharacterBonePhysics {

    private static final String BACK_ASIDE = "BackAsideButterfly";
    private static final String LEFT1      = "BackLeftAsideButterfly";
    private static final String LEFT2      = "BackLeftAsideButterfly2";
    private static final String RIGHT1     = "BackRightAsideButterfly";
    private static final String RIGHT2     = "BackRightAsideButterfly2";
    private static final String LEFT_SKIRT = "LeftUpSkirt";
    private static final String RIGHT_SKIRT = "RightUpSkirt";

    private static final class Cloth {
        float ang;      // 与自然下垂位的偏差（平滑后的）
        float vel;      // 角速度
        float smooth;   // 低通滤波后的速度信号
        long lastTick;
    }

    private static final Cloth C = new Cloth();

    // 回正弹簧 + 阻尼
    private static final float SPRING  = 0.22F;
    private static final float DAMPING = 0.86F;
    // 低通系数：越小对步伐抖动越不敏感
    private static final float SMOOTH = 0.08F;
    // 死区：速度变化小于该值视为"匀速"，物理归零（保护 walk/run）
    private static final float DEADZONE = 0.010F;
    // 单边最大下垂偏差（弧度，约 16°），避免过冲乱飞
    private static final float MAX_DEV = 0.28F;
    private static float lastSpeed = 0F;

    /** 只响应「明显的」起动/急停，平滑并死区。返回 0(无物理) 或带符号的偏差。 */
    private static float driveSignal(float accel, Cloth c, float dt) {
        float a = (Math.abs(accel) < DEADZONE) ? 0F : accel;
        c.smooth = c.smooth * (1F - SMOOTH) + a * SMOOTH;
        c.vel = c.vel * DAMPING + (c.smooth - c.ang) * SPRING * dt;
        c.ang += c.vel * dt;
        // 夹在重力偏向上：向前冲=拖后(负)，刹车=惯性回摆但不过头
        return clamp(c.ang, -MAX_DEV, MAX_DEV);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    @Override
    public BoneUpdater<GeoRenderState> clothUpdater(Player player, PGCharacter character) {
        // 物理已停用：改为在 walk/run 动画里直接驱动骨骼。返回 null = 无物理叠加，保留文件以便日后恢复。
        return null;
    }
}