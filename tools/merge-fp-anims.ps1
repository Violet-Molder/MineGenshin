# 把第三人称动画的「非手臂」轨道合并进第一人称动画（fp_*）。
#
# 为什么需要它
# ------------
# 这套角色模型的**特效骨骼**（slash_a..g / sfa..sfh / out_size / obs2 / bone9 / bone12 …）
# 在 rest pose 下是**可见**的：第三人称动画靠「t = 0 时把它们的 scale 写成 0、
# 到挥砍那一瞬再放大」来让特效只在正确的时刻出现。
#
# 所以第一人称动画如果**只写手臂**（fp_attack_1 只写了 5 根骨骼），特效骨骼就没有任何轨道
# → 保持 rest pose → 一播动画满屏特效，而不是跟着剑走。
#
# 结论（硬要求）：**fp 动画必须以第三人称动画为底，只替换手臂/躯干/头那几根**，
# 长度与特效轨道原样保留。这个脚本就是做这件事的。
#
# 用法（本机执行策略禁止直接跑 .ps1，用 Invoke-Expression 绕过）
# ---------------------------------------------------------------
#   $code = Get-Content -Raw tools\merge-fp-anims.ps1
#   Invoke-Expression $code
#
# 或者显式指定角色目录：
#   $code = Get-Content -Raw tools\merge-fp-anims.ps1
#   Invoke-Expression "$code; Merge-FpAnims -CharacterDir 'src\main\resources\assets\minegenshin\character\lumine'"
#
# 注意：**会直接覆盖 fp 文件**。改 fp 姿势请改覆盖的那几根（脚本保留它们），
# 或者先备份。特效轨道永远取自第三人称文件，不需要手动同步。
#
# 目录说明：geo 动画的母本就放在 src\main\resources 的正常资源路径下
# （objects 里那些 .geo.json / .animation.json 本身不提交仓库），脚本直接读写它们。
# 改完跑 gradlew build（或 gradlew verifyGeoAssets 单独确认），
# 构建会重新收成整包文件。

param(
    [string]$CharacterDir = 'src\main\resources\assets\minegenshin\character\vesna',
    [string]$ThirdPersonFile = '',
    [string]$FirstPersonFile = ''
)

$ErrorActionPreference = 'Stop'

if (-not $ThirdPersonFile) {
    $name = Split-Path $CharacterDir -Leaf
    $ThirdPersonFile = Join-Path $CharacterDir "$name.animation.json"
}
if (-not $FirstPersonFile) {
    $name = Split-Path $CharacterDir -Leaf
    $FirstPersonFile = Join-Path $CharacterDir "${name}_fp.animation.json"
}

Write-Host "第三人称: $ThirdPersonFile"
Write-Host "第一人称: $FirstPersonFile"

$tp = Get-Content $ThirdPersonFile -Raw | ConvertFrom-Json
$fp = Get-Content $FirstPersonFile -Raw | ConvertFrom-Json

$animations = [ordered]@{}

foreach ($fpProp in $fp.animations.PSObject.Properties) {
    $fpName = $fpProp.Name
    $tpName = if ($fpName.StartsWith('fp_')) { $fpName.Substring(3) } else { $fpName }

    if (-not $tp.animations.PSObject.Properties[$tpName]) {
        Write-Warning "$fpName 找不到对应的第三人称动画 '$tpName'，原样保留"
        $animations[$fpName] = $fpProp.Value
        continue
    }

    # 深拷贝：序列化再解析，避免和源对象共享引用
    $base = $tp.animations.$tpName | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $fpAnim = $fpProp.Value

    $bones = [ordered]@{}
    foreach ($p in $base.bones.PSObject.Properties) { $bones[$p.Name] = $p.Value }
    # fp 里写了的骨骼覆盖掉第三人称的同名轨道（这就是「只替换手臂」）
    foreach ($p in $fpAnim.bones.PSObject.Properties) { $bones[$p.Name] = $p.Value }

    $len = [double]$base.animation_length
    if ([double]$fpAnim.animation_length -gt $len) { $len = [double]$fpAnim.animation_length }

    $animations[$fpName] = [ordered]@{
        loop             = $false
        animation_length = $len
        bones            = $bones
    }

    Write-Host ("{0,-20} 骨骼 {1}（第三人称 {2}）长度 {3}" -f $fpName,
        @($bones.Keys).Count, @($tp.animations.$tpName.bones.PSObject.Properties).Count, $len)
}

$out = [ordered]@{
    format_version = $fp.format_version
    animations     = $animations
}

$json = $out | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText((Resolve-Path $FirstPersonFile), $json,
    (New-Object System.Text.UTF8Encoding($false)))
Write-Host "已写入 $FirstPersonFile"
