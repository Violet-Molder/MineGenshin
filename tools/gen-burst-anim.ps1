# Generates src/main/resources/assets/minegenshin/character/vesna/vesna_burst.animation.json
# (母本就放在正常资源路径下，不提交仓库；跑 gradlew build / runClient 时自动收进整包)
# One animation: burst_dive  (jump up -> rotate into head-down dive pose -> hold, code takes over the descent)
# Pure ASCII output; keys are Bedrock/GeckoLib style, times in seconds.
$ErrorActionPreference = 'Stop'
# absolute path on purpose: this file is run through Invoke-Expression (execution policy blocks .ps1),
# and $PSScriptRoot is empty in that mode
$out = 'E:\MCMOD\MineGenshin-26.2\src\main\resources\assets\minegenshin\character\vesna\vesna_burst.animation.json'

function V($x, $y, $z) { @{ vector = @([double]$x, [double]$y, [double]$z) } }
function T($t) { [string]::Format([cultureinfo]::InvariantCulture, '{0:0.####}', [double]$t) }

$bones = [ordered]@{}

# ---- whole body: crouch -> launch -> pitch forward into the dive, then hold ----
$bones['root'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = V 0 0 0
        (T 0.15) = V -10 0 0
        (T 0.4)  = V -22 0 0
        (T 0.7)  = V 25 0 0
        (T 0.95) = V 70 0 0
        (T 1.2)  = V 84 0 0
        (T 1.45) = V 86 0 0
        (T 2.0)  = V 86 0 0
    }
    position = [ordered]@{
        (T 0.0)  = V 0 0 0
        (T 0.15) = V 0 -6 0
        (T 0.4)  = V 0 3 0
        (T 0.7)  = V 0 6 0
        (T 0.95) = V 0 4 0
        (T 1.2)  = V 0 2 0
        (T 1.45) = V 0 1 0
        (T 2.0)  = V 0 1 0
    }
}

# ---- torso: fold forward so she faces the ground ----
$bones['torso'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = V 0 0 0
        (T 0.15) = V 12 0 0
        (T 0.4)  = V -14 0 0
        (T 0.95) = V 26 0 0
        (T 1.45) = V 30 0 0
        (T 2.0)  = V 30 0 0
    }
}
$bones['head'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = V 0 0 0
        (T 0.4)  = V -18 0 0
        (T 0.95) = V -30 0 0
        (T 1.45) = V -34 0 0
        (T 2.0)  = V -34 0 0
    }
}

# ---- sword arm thrust forward (blade leads the dive) ----
$bones['arm_right'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = V -20 22 -6
        (T 0.15) = V -6 24 -4
        (T 0.4)  = V -46 14 -10
        (T 0.95) = V -88 6 -4
        (T 1.45) = V -92 4 -2
        (T 2.0)  = V -92 4 -2
    }
    position = [ordered]@{
        (T 0.0)  = V 0 -0.7 -0.5
        (T 0.4)  = V 0 -0.6 -1.2
        (T 0.95) = V 0 -0.4 -2.4
        (T 1.45) = V 0 -0.4 -2.6
        (T 2.0)  = V 0 -0.4 -2.6
    }
}
$bones['arm_bot_right'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = V 0 0 0
        (T 0.4)  = V -12 0 0
        (T 0.95) = V -18 0 0
        (T 2.0)  = V -18 0 0
    }
}
$bones['blade_right'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = V 0 0 0
        (T 0.4)  = V -10 0 0
        (T 0.95) = V -14 0 0
        (T 2.0)  = V -14 0 0
    }
}
# left arm swept back for balance
$bones['arm_left'] = [ordered]@{
    rotation = [ordered]@{
        (T 0.0)  = V 20 -22 6
        (T 0.4)  = V 8 -30 10
        (T 0.95) = V 46 -26 16
        (T 1.45) = V 52 -24 18
        (T 2.0)  = V 52 -24 18
    }
}

# ---- legs trail behind the dive ----
$bones['leg_left'] = [ordered]@{
    rotation = [ordered]@{ (T 0.0) = V 0 0 0; (T 0.15) = V 22 0 0; (T 0.4) = V -10 0 0; (T 0.95) = V -26 0 0; (T 2.0) = V -26 0 0 }
}
$bones['leg_right'] = [ordered]@{
    rotation = [ordered]@{ (T 0.0) = V 0 0 0; (T 0.15) = V 22 0 0; (T 0.4) = V -10 0 0; (T 0.95) = V -22 0 0; (T 2.0) = V -22 0 0 }
}

# ---- effect cubes: circle the body while she plunges, each spinning on its own axis ----
$effectBones = @(
    'spirit', 'spr_ring', 'out_spin', 'out_size', 'obs', 'obs2',
    'bone8', 'bone9', 'bone11', 'bone12',
    'sfa', 'sfb', 'sfc', 'sfd', 'sfe', 'sff', 'sfg', 'sfh',
    'slash_a', 'slash_b', 'slash_c', 'slash_d', 'slash_e', 'slash_f', 'slash_g'
)
$orbitRadius = 22.0
$start = 0.95
$end = 2.0
$steps = 8
$count = $effectBones.Count

for ($i = 0; $i -lt $count; $i++) {
    $baseAngle = 360.0 * $i / $count
    $pos = [ordered]@{}
    $rot = [ordered]@{}
    $sca = [ordered]@{}
    # hidden before the dive starts, pops in as the plunge begins
    $pos[(T 0.0)] = V 0 0 0
    $pos[(T 0.9)] = V 0 0 0
    $rot[(T 0.0)] = V 0 0 0
    $rot[(T 0.9)] = V 0 0 0
    $sca[(T 0.0)] = V 0 0 0
    $sca[(T 0.85)] = V 0 0 0
    $sca[(T 0.95)] = V 1 1 1
    $sca[(T 2.0)] = V 1 1 1
    for ($s = 0; $s -le $steps; $s++) {
        $t = $start + ($end - $start) * $s / $steps
        $ang = ($baseAngle + 360.0 * $s / $steps) * [math]::PI / 180.0
        $x = [math]::Round([math]::Cos($ang) * $orbitRadius, 2)
        $z = [math]::Round([math]::Sin($ang) * $orbitRadius, 2)
        $y = [math]::Round(3.0 * [math]::Sin($ang * 2.0), 2)
        $pos[(T $t)] = V $x $y $z
        $rot[(T $t)] = V ([math]::Round(360.0 * $s / $steps, 2)) ([math]::Round(720.0 * $s / $steps, 2)) 0
    }
    $bones[$effectBones[$i]] = [ordered]@{ rotation = $rot; position = $pos; scale = $sca }
}

$json = [ordered]@{
    format_version = '1.8.0'
    animations = [ordered]@{
        burst_dive = [ordered]@{
            animation_length = 2.0
            loop = $false
            bones = $bones
        }
    }
}

$text = $json | ConvertTo-Json -Depth 24
[System.IO.File]::WriteAllText($out, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("WROTE " + $out)
$check = Get-Content $out -Raw | ConvertFrom-Json
Write-Output ("PARSE OK; animations=" + ($check.animations.PSObject.Properties.Name -join ',') + "; bones=" + $check.animations.burst_dive.bones.PSObject.Properties.Name.Count)
Write-Output ("length=" + $check.animations.burst_dive.animation_length + "; loop=" + $check.animations.burst_dive.loop)
