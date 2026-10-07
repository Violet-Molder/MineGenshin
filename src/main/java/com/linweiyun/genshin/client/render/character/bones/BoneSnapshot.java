package com.linweiyun.genshin.client.render.character.bones;

import software.bernie.geckolib.cache.object.GeoBone;

/**
 * 一块骨骼的可写视图：旋转、平移、缩放与显隐。
 *
 * <p>单位与模型文件一致：旋转用<b>角度</b>、平移用<b>像素</b>（16 像素 = 1 格）、缩放是倍率。
 * 显隐分两级：{@link #skipRender} 只管自己这一块，{@link #skipChildrenRender} 连子骨骼一起。
 */
public final class BoneSnapshot {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private final GeoBone bone;

    public BoneSnapshot(GeoBone bone) {
        this.bone = bone;
    }

    public GeoBone bone() {
        return bone;
    }

    public String getName() {
        return bone.getName();
    }

    // ==================== 旋转（角度） ====================

    public void setRotation(float rotX, float rotY, float rotZ) {
        bone.setRotX(rotX * DEG_TO_RAD);
        bone.setRotY(rotY * DEG_TO_RAD);
        bone.setRotZ(rotZ * DEG_TO_RAD);
    }

    public void setRotationX(float rotX) {
        bone.setRotX(rotX * DEG_TO_RAD);
    }

    public void setRotationY(float rotY) {
        bone.setRotY(rotY * DEG_TO_RAD);
    }

    public void setRotationZ(float rotZ) {
        bone.setRotZ(rotZ * DEG_TO_RAD);
    }

    public float getRotX() {
        return bone.getRotX() / DEG_TO_RAD;
    }

    public float getRotY() {
        return bone.getRotY() / DEG_TO_RAD;
    }

    public float getRotZ() {
        return bone.getRotZ() / DEG_TO_RAD;
    }

    // ==================== 平移（像素） ====================

    public void setTranslate(float x, float y, float z) {
        bone.setPosX(x / PIXELS_PER_BLOCK);
        bone.setPosY(y / PIXELS_PER_BLOCK);
        bone.setPosZ(z / PIXELS_PER_BLOCK);
    }

    public void translate(float x, float y, float z) {
        setTranslate(getTranslateX() + x, getTranslateY() + y, getTranslateZ() + z);
    }

    public void setTranslateX(float x) {
        bone.setPosX(x / PIXELS_PER_BLOCK);
    }

    public void setTranslateY(float y) {
        bone.setPosY(y / PIXELS_PER_BLOCK);
    }

    public void setTranslateZ(float z) {
        bone.setPosZ(z / PIXELS_PER_BLOCK);
    }

    public float getTranslateX() {
        return bone.getPosX() * PIXELS_PER_BLOCK;
    }

    public float getTranslateY() {
        return bone.getPosY() * PIXELS_PER_BLOCK;
    }

    public float getTranslateZ() {
        return bone.getPosZ() * PIXELS_PER_BLOCK;
    }

    // ==================== 缩放 ====================

    public void setScale(float scaleX, float scaleY, float scaleZ) {
        bone.setScaleX(scaleX);
        bone.setScaleY(scaleY);
        bone.setScaleZ(scaleZ);
    }

    public float getScaleX() {
        return bone.getScaleX();
    }

    public float getScaleY() {
        return bone.getScaleY();
    }

    public float getScaleZ() {
        return bone.getScaleZ();
    }

    // ==================== 可见性 ====================

    /** 这一块不画。 */
    public void skipRender(boolean skip) {
        bone.setHidden(skip);
    }

    /** 这块的子骨骼也不画。 */
    public void skipChildrenRender(boolean skip) {
        bone.setChildrenHidden(skip);
    }

    public boolean isHidden() {
        return bone.isHidden();
    }

    public boolean areChildrenHidden() {
        return bone.isHidingChildren();
    }
}