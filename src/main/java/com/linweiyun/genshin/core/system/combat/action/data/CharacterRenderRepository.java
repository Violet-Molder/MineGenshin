// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.system.combat.action.data;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public final class CharacterRenderRepository {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);
   private static final Map<String, CharacterRenderData> REGISTRY = new LinkedHashMap<>();
   private static final Map<String, CharacterRenderData> STANDARD = new ConcurrentHashMap<>();

   private CharacterRenderRepository() {
   }

   public static void register(CharacterRenderData data) {
      if (data != null && data.id() != null && !data.id().isEmpty()) {
         if (!REGISTRY.containsKey(data.id())) {
            REGISTRY.put(data.id(), data);
            LOGGER.debug("[CharacterRender] 注册角色渲染: {}", data.id());
         }
      }
   }

   public static CharacterRenderData get(String id) {
      if (id != null && !id.isEmpty()) {
         CharacterRenderData registered = REGISTRY.get(id);
         return registered != null ? registered : STANDARD.computeIfAbsent(id, CharacterRenderRepository::standardFor);
      } else {
         return null;
      }
   }

   private static CharacterRenderData standardFor(String id) {
      LOGGER.info("[CharacterRender] 角色 '{}' 没有登记自己的渲染定义，按标准的一套渲染（自己目录优先，缺的借 character/linweiyun/）", id);
      return CharacterRenderData.character(id, CharacterRenderData.defaultAnimMapping(), 1.0F).withModelAuthor("下一只风筝", "https://space.bilibili.com/281665959");
   }

   public static Map<String, CharacterRenderData> getAll() {
      return Collections.unmodifiableMap(REGISTRY);
   }

   public static int size() {
      return REGISTRY.size();
   }
}
