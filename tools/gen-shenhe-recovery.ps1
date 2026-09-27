# Generates the shenhe (puppet) normal-attack recovery animation: sword_idle_attack_end
#
# 输出 / 合并进 src/main/resources/assets/minegenshin/character/shenhe/shenhe_puppet.animation.json
# （母本放在正常资源路径下，不提交仓库；跑 gradlew build / runClient 时自动收进整包）
#
# ── 这段动画在补什么 ────────────────────────────────────────────────────────
# 三段普攻（sword_idle_attack_01/02/03）是 YSM 搬过来的原素材，第三段收在一半：
#   0.00s  起手（承接第二段的弓步）
#   0.25s  MAllbody 转了 -250°、剑刃消失（RightSword.scale = 0）
#   0.875s 动画结束 —— 人就定在「转了 250°、手里没剑」这一帧上
# 引擎侧 thenPlayAndHold 会把最后一帧一直撑到状态机切走，所以不接收尾就是「卡住不动」。
#
# 收尾要干的三件事：
#   ① 把这一转转完：MAllbody -250° → -360°（continue 同方向，不是倒转回去）。
#      -360° 和 0° 画面上完全等价，但它让「收招」读起来是「转完站稳」而不是「倒着甩回来」。
#   ② 把第三段那几个静态姿势（UpBody -47.5 / Skirt -7.5 / Root -5 / 弓步的腿）放回站姿。
#   ③ 把剑接回来：RightSword 的位移/旋转在剑还看不见的时候就先归位，
#      再在转身结束那一刻把 scale 从 0 弹回 1（和第三段 0.2917 的召唤是同一个手感），
#      同时 RightHandLocator 放开（1）= 实体武器回到手里。
#   ④ 把胸口那块浮游屏熄掉：它从第二段起就一直亮着，收招末尾按参考视频那样
#      「垂直压成一条亮线 → 完全消失」（0.55 还亮 / 0.68 压扁 / 0.78 全灭），
#      齿轮跟着一起灭。屏幕与齿轮的「哪一段允许出现」由 CharacterPropBones 管。
#
# ── 结束姿势为什么是这些数 ──────────────────────────────────────────────────
# 收尾的最后一帧会被硬切到 idle（sword_idle_attack_end 在 SPECIAL_ANIMS 里，过渡刻数为 0），
# 所以终点必须**正好等于 idle 的姿势**，否则切过去的那一帧会跳一下。
# 下面每个骨骼的终值都抄自 shenhe.animation.json 的 idle，
# 没被 idle 写的（MAllbody / Skirt / RightSword / RightHandLocator）就用模型默认姿态
# （旋转 0、位移 0、缩放 1）—— GeckoLib 对「动画里没写的骨骼」就是用默认姿态渲染的。
#
# Pure ASCII output; keys are Bedrock/GeckoLib style, times in seconds (1 刻 = 0.05 秒).
$ErrorActionPreference = 'Stop'
# absolute path on purpose: 本文件是给 Codex 会话里用 Invoke-Expression 跑的（执行策略挡 .ps1），
# 那种模式下 $PSScriptRoot 是空的
$out = 'E:\MCMOD\MineGenshin-26.2\src\main\resources\assets\minegenshin\character\shenhe\shenhe_puppet.animation.json'

function V($x, $y, $z) { @{ vector = @([double]$x, [double]$y, [double]$z) } }
function T($t) { [string]::Format([cultureinfo]::InvariantCulture, '{0:0.####}', [double]$t) }
# 关键帧：post + catmullrom，和这个文件里已有的 decoding_mode 保持同一种写法
function K($x, $y, $z) { [ordered]@{ post = (V $x $y $z); lerp_mode = 'catmullrom' } }

$bones = [ordered]@{}

# ---- Root：第三段一直挂着的 -5° 偏航，站直的时候抹平 ----
$bones['Root'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0) = K 0 -5 0
        (T 0.5) = K 0 0 0
        (T 0.9) = K 0 0 0
    }
}

# ---- MAllbody：把这一转转完（-250 -> -360），缓出 ----
# 第三段是 0.25 秒里转 250°（≈1000°/s），这里按 560 -> 480 -> 400 -> 320 -> 220 -> 140 -> 60 -> 20 递减，
# 读起来是「转势还在、一点点刹住」，而不是「啪一下停住」。
$bones['MAllbody'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K 0 -250 0
        (T 0.05) = K 0 -278 0
        (T 0.1)  = K 0 -302 0
        (T 0.15) = K 0 -322 0
        (T 0.2)  = K 0 -338 0
        (T 0.25) = K 0 -349 0
        (T 0.3)  = K 0 -356 0
        (T 0.35) = K 0 -359 0
        (T 0.4)  = K 0 -360 0
        (T 0.9)  = K 0 -360 0
    }
}

# ---- 躯干 / 裙子：解开第三段的拧劲，0.65 处过一点点再落回 ----
$bones['UpBody'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K 0 -47.5 0
        (T 0.25) = K 0 -24 0
        (T 0.5)  = K 0 -6 0
        (T 0.65) = K 0 3 0
        (T 0.8)  = K 0 0 0
        (T 0.9)  = K 0 0 0
    }
}
$bones['Skirt'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0) = K 0 -7.5 0
        (T 0.4) = K 0 -3 0
        (T 0.7) = K 0 0 0
        (T 0.9) = K 0 0 0
    }
}

# ---- 头：第三段结束时头还落在身子后面（-51°），这里追上转向 ----
$bones['Head'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K -7.3008 -51.3554 -0.0177
        (T 0.2)  = K -4.5 -30 -3
        (T 0.45) = K -1.5 -12 -1
        (T 0.7)  = K 0.42262 0 1
        (T 0.9)  = K 0.42262 0 1
    }
}

# ---- 双臂：从左手的收势（-217°）回到自然下垂；idle 的胳膊还带着 0.15 的上抬 ----
$bones['LeftArm'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K 132.00999 -27.8 -217.32001
        (T 0.15) = K 96 -22 -160
        (T 0.35) = K 46 -12 -80
        (T 0.55) = K 12 -4 -22
        (T 0.75) = K 0 0 0
        (T 0.9)  = K 0 0 0
    }
    position = [ordered]@{
        (T 0.0)  = K 0 0 0
        (T 0.75) = K 0 0.15 0
        (T 0.9)  = K 0 0.15 0
    }
}
$bones['LeftForeArm'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K 5.3 -7.3 76.02
        (T 0.3)  = K 4 -5 42
        (T 0.6)  = K 1 -1 8
        (T 0.78) = K 0 0 0
        (T 0.9)  = K 0 0 0
    }
}
$bones['RightArm'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0) = K 4.9295 9.6876 24.1549
        (T 0.15) = K 8 14 30
        (T 0.4)  = K 5 8 18
        (T 0.65) = K 1 2 5
        (T 0.8)  = K 0 0 0.9939
        (T 0.9)  = K 0 0 0.9939
    }
    position = [ordered]@{
        (T 0.0) = K 0 0 0
        (T 0.8) = K 0 0.15 0
        (T 0.9) = K 0 0.15 0
    }
}
$bones['RightForeArm'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K 102.8027 83.0498 18.6611
        (T 0.2)  = K 80 62 20
        (T 0.45) = K 36 26 12
        (T 0.7)  = K 6 4 3
        (T 0.85) = K 1 0 0
        (T 0.9)  = K 1 0 0
    }
}

# ---- 腿：第三段那个弓步（左脚在前的压低站位）迈回 idle 的站姿 ----
$bones['LeftLeg'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K 1.3873 -14.7056 2.093
        (T 0.3)  = K 2 -10 2.5
        (T 0.6)  = K 5.5 6 3.5
        (T 0.78) = K 4.86076 8.6034 3.8084
        (T 0.9)  = K 4.86076 8.6034 3.8084
    }
    position = [ordered]@{
        (T 0.0)  = K -0.75 0 2
        (T 0.35) = K -0.3 0 0.8
        (T 0.7)  = K 0 0 0
        (T 0.9)  = K 0 0 0
    }
}
$bones['LeftLowerLeg'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K 25 0 0
        (T 0.35) = K 12 0 0
        (T 0.7)  = K 0 0 0
        (T 0.9)  = K 0 0 0
    }
}
$bones['RightLeg'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0) = K 2.1463 -26.7449 -6.2277
        (T 0.3) = K 0 -22 -5
        (T 0.6) = K -3 -15 -4
        (T 0.8) = K -2.06677 -12.55392 -3.95166
        (T 0.9) = K -2.06677 -12.55392 -3.95166
    }
    position = [ordered]@{
        (T 0.0)  = K 1 0 -0.75
        (T 0.35) = K 0.4 0 -0.3
        (T 0.7)  = K 0 0 0
        (T 0.9)  = K 0 0 0
    }
}
$bones['RightLowerLeg'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = K 10 0 0
        (T 0.4)  = K 5 0 0
        (T 0.75) = K 0 0 0
        (T 0.9)  = K 0 0 0
    }
}

# ---- 右手的剑：先隐形归位，转身收完那一刻再显形 ----
# 第三段结束时 RightSword 是 scale=0（剑被打没了）+ 停在身体左侧；模型里这根骨骼是空的挂点，
# 但它的姿势决定了武器挂在哪 —— 归位到默认（旋转 [0,0,-32.5]、位移 0）就是 idle 的姿势。
$bones['RightSword'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0) = K 0 0 32.5
        (T 0.3) = K 0 0 -32.5
        (T 0.9) = K 0 0 -32.5
    }
    position = [ordered]@{
        (T 0.0) = K 0 -26 -29
        (T 0.3) = K 0 0 0
        (T 0.9) = K 0 0 0
    }
    scale = [ordered]@{
        (T 0.0)  = K 0 0 0
        (T 0.3)  = K 0 0 0
        (T 0.35) = K 1.2 1.2 1
        (T 0.45) = K 1 1 1
        (T 0.9)  = K 1 1 1
    }
}

# ---- 实体武器挂点：攻击期间是 0（藏起手里的实物武器），转身收完跟着剑一起放回手里 ----
$bones['RightHandLocator'] = [ordered]@{
    scale = [ordered]@{
        (T 0.0)  = K 0 0 0
        (T 0.3)  = K 0 0 0
        (T 0.35) = K 1 1 1
        (T 0.9)  = K 1 1 1
    }
}

# ---- 胸口屏幕：第三段把它往前推了 5 格，放回来；收招末尾整块熄掉 ----
# 第三段（sword_idle_attack_03）把屏幕推到身前 5 格并一直开着，这里先把它放回默认位置，
# 然后在收招还剩 0.3 秒的时候按参考视频那样「垂直收成一条亮线 → 完全消失」：
#   0.55  还亮着
#   0.68  高度压到 5%（横向反而过冲一点）= 那条亮线
#   0.78  全灭
# 齿轮（ysmGlow_texiao2）跟着屏幕一起灭 —— 它的位置/旋转由重击那段
# （decoding_mode）负责，这里只补 scale，不动别的通道。
$screenCollapse = [ordered]@{
    (T 0.0)  = K 1 1 1
    (T 0.55) = K 1 1 1
    (T 0.68) = K 1.1 0.05 1
    (T 0.78) = K 0 0 0
    (T 0.9)  = K 0 0 0
}
$bones['ysmGlow_texiao'] = [ordered]@{
    position = [ordered]@{
        (T 0.0) = K 0 0 -5
        (T 0.5) = K 0 0 0
        (T 0.9) = K 0 0 0
    }
    scale = $screenCollapse
}
$bones['ysmGlow_texiao2'] = [ordered]@{
    scale = $screenCollapse
}

$recovery = [ordered]@{
    animation_length = 0.9
    loop = $false
    bones = $bones
}

# ── 合并：这个文件里还有木偶重击 decoding_mode，只能加不能覆盖 ──
$animations = [ordered]@{}
if (Test-Path $out) {
    $existing = Get-Content $out -Raw | ConvertFrom-Json
    foreach ($prop in $existing.animations.PSObject.Properties) {
        $animations[$prop.Name] = $prop.Value
    }
}
$animations['sword_idle_attack_end'] = $recovery

$json = [ordered]@{
    format_version = '1.8.0'
    animations = $animations
}

$text = $json | ConvertTo-Json -Depth 32
[System.IO.File]::WriteAllText($out, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("WROTE " + $out)
$check = Get-Content $out -Raw | ConvertFrom-Json
Write-Output ("PARSE OK; animations=" + ($check.animations.PSObject.Properties.Name -join ',') + "; bones=" + $check.animations.sword_idle_attack_end.bones.PSObject.Properties.Name.Count)
Write-Output ("length=" + $check.animations.sword_idle_attack_end.animation_length + "; loop=" + $check.animations.sword_idle_attack_end.loop)
Write-Output ("decoding_mode bones=" + $check.animations.decoding_mode.bones.PSObject.Properties.Name.Count + "; length=" + $check.animations.decoding_mode.animation_length)
