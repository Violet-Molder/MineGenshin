// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.util.log;

public enum LogGroup {
   COMBAT("战斗（动作 / 伤害 / 目标选择）"),
   ELEMENT("元素（附着 / 反应）"),
   CHARACTER("角色（角色 / 角色效果）"),
   CONTENT("内容（物品 / 实体 / 技能节点）"),
   RENDER("渲染（几何 / 资源 / 界面 / 飘字）"),
   ANIMATION("动画（状态切换 / 播放起止）"),
   CORE("核心服务（网络 / 存档 / 状态 / 资源包 / 刷怪 / 卡池 / 音效）"),
   MIXIN("混入（Mixin）");

   private final String label;
   private volatile boolean enabled = true;

   LogGroup(String label) {
      this.label = label;
   }

   public String label() {
      return this.label;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }
}
