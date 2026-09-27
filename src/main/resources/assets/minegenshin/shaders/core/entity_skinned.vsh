#version 330

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#moj_import <minecraft:light.glsl>
#endif
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

// 属性名必须与 SkinnedMesh.FORMAT 逐字对应：管线编译期是按名字把顶点格式接到着色器输入上的。
in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in uvec4 BoneIds;

// 骨骼矩阵 + 光照 / 覆盖层 + 颜色。布局必须与 BoneMatrixPalette 的写入顺序严格一致。
layout(std140) uniform SkinData {
    mat4 Bones[128];
    mat3 NormalBones[128];
    ivec4 LightOverlay;
    vec4 Color;
};

#ifndef NO_OVERLAY
uniform sampler2D Sampler1;
#endif

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
#else
out vec4 vertexColor;
#endif

#ifndef EMISSIVE
out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
out vec4 overlayColor;
#endif

out vec2 texCoord0;

void main() {
    // 数组下标用 int：GLSL 各实现对 uint 下标的支持不一致，而这条管线在启动期就会被预编译，
    // 编译不过等于进不去游戏。int(BoneIds.x) 与原版 attribute 的语义一致，代价为零。
    int bone = int(BoneIds.x);
    vec4 skinColor = Color;

    // CPU 蒙皮是把「模型矩阵 × 骨骼矩阵」折进顶点再写进缓冲的，所以它能直接拿顶点位置算雾距离；
    // 这里顶点是模型局部的，同一个矩阵链必须在这里做，雾距离也得跟着换到同一空间，
    // 否则远处的角色不会像地形一样溶进雾里（表现为「只有角色不褪色」）。
    vec3 viewRelative = (ModelViewMat * Bones[bone] * vec4(Position, 1.0)).xyz;
    gl_Position = ProjMat * vec4(viewRelative, 1.0);

    // 视图矩阵是刚性的（旋转 + 平移，无缩放），所以它的逆就是转置旋转 + 反平移。
    // 还原成「相机相对世界坐标」后，球面雾与柱面雾的取值都与 CPU 路径逐位同源。
    vec3 worldRelative = transpose(mat3(ModelViewMat)) * (viewRelative - ModelViewMat[3].xyz);
    sphericalVertexDistance = fog_spherical_distance(worldRelative);
    cylindricalVertexDistance = fog_cylindrical_distance(worldRelative);

    // 不做 normalize：原版 entity.vsh 同样直接把法线喂给光照，符号修正依赖未归一化的值。
    vec3 normal = NormalBones[bone] * Normal;
    uint fix = BoneIds.w;

    if ((fix & 1u) != 0u && normal.x < 0.0) {
        normal.x = -normal.x;
    }

    if ((fix & 2u) != 0u && normal.y < 0.0) {
        normal.y = -normal.y;
    }

    if ((fix & 4u) != 0u && normal.z < 0.0) {
        normal.z = -normal.z;
    }

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, skinColor);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, skinColor);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = skinColor;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, skinColor);
#endif

#ifndef EMISSIVE
    lightMapColor = sample_lightmap(Sampler2, LightOverlay.xy);
#endif

#ifndef NO_OVERLAY
    overlayColor = texelFetch(Sampler1, LightOverlay.zw, 0);
#endif

    texCoord0 = UV0;
}
