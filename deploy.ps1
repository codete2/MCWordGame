# 设置标题和编码
$host.ui.RawUI.WindowTitle = "MCWordGame 部署脚本"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 清屏并显示标题
Clear-Host
Write-Host "╔════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     MCWordGame 部署脚本        ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# 检查必要工具
function Check-Command($cmdname) {
    return [bool](Get-Command -Name $cmdname -ErrorAction SilentlyContinue)
}

if (-not (Check-Command "git")) {
    Write-Host "错误：未安装 Git！" -ForegroundColor Red
    Write-Host "请从 https://git-scm.com/downloads 下载并安装 Git"
    pause
    exit 1
}

if (-not (Check-Command "mvn")) {
    Write-Host "错误：未安装 Maven！" -ForegroundColor Red
    Write-Host "请从 https://maven.apache.org/download.cgi 下载并安装 Maven"
    pause
    exit 1
}

# 读取当前版本
Write-Host "`n读取当前版本..." -ForegroundColor Green
$pomContent = Get-Content "pom.xml" -Raw
if ($pomContent -match '<version>(.*?)</version>') {
    $currentVersion = $matches[1]
    # 如果版本号是变量，则从 plugin.yml 读取
    if ($currentVersion -eq '$newVersion') {
        $ymlContent = Get-Content "src/main/resources/plugin.yml" -Raw
        if ($ymlContent -match "version: '(.*?)'") {
            $currentVersion = $matches[1]
        }
    }
    Write-Host "当前版本: $currentVersion" -ForegroundColor White
} else {
    Write-Host "错误：无法从 pom.xml 读取版本号！" -ForegroundColor Red
    pause
    exit 1
}

# 解析版本号
$versionParts = $currentVersion -replace "-RELEASE", "" -split "\."
try {
    $major = [int]($versionParts[0])
    $minor = [int]($versionParts[1])
    $patch = [int]($versionParts[2])
} catch {
    Write-Host "错误：版本号格式不正确！" -ForegroundColor Red
    Write-Host "当前读取到的版本号: $currentVersion" -ForegroundColor Yellow
    Write-Host "期望的格式: X.Y.Z-RELEASE (例如: 1.0.0-RELEASE)" -ForegroundColor Yellow
    pause
    exit 1
}

# 选择版本更新类型
Write-Host "`n选择版本更新类型:" -ForegroundColor Cyan
Write-Host "1. 主版本更新 (当前: $major)" -ForegroundColor Yellow
Write-Host "2. 次版本更新 (当前: $minor)" -ForegroundColor Yellow
Write-Host "3. 补丁更新 (当前: $patch)" -ForegroundColor Yellow
$updateType = Read-Host "`n请选择 (1-3)"

# 更新版本号
switch ($updateType) {
    "1" { 
        $major++
        $minor = 0
        $patch = 0
    }
    "2" { 
        $minor++
        $patch = 0
    }
    "3" { $patch++ }
    default {
        Write-Host "无效的选择！" -ForegroundColor Red
        pause
        exit 1
    }
}

$newVersion = "$major.$minor.$patch-RELEASE"
Write-Host "`n新版本: $newVersion" -ForegroundColor Green

$confirm = Read-Host "确认更新版本? (Y/N)"
if ($confirm -ne "Y") {
    Write-Host "已取消操作" -ForegroundColor Red
    pause
    exit 1
}

# 更新 pom.xml 版本号
Write-Host "`n更新 pom.xml 版本号..." -ForegroundColor Green
$pomContent = Get-Content "pom.xml" -Raw
$lines = $pomContent -split "`n"
$updated = $false

# 只更新第一个找到的版本号（项目版本号）
for ($i = 0; $i -lt $lines.Count; $i++) {
    if (-not $updated -and $lines[$i] -match '<version>.*</version>') {
        $lines[$i] = $lines[$i] -replace '<version>.*</version>', "<version>$newVersion</version>"
        $updated = $true
    }
}

$pomContent = $lines -join "`n"
$pomContent | Set-Content "pom.xml" -Encoding UTF8

# 更新 plugin.yml
Write-Host "`n更新 plugin.yml 版本号..." -ForegroundColor Green
$ymlContent = Get-Content "src/main/resources/plugin.yml" -Encoding UTF8
$ymlContent = $ymlContent -replace "version: '.*?'", "version: '$newVersion'"
$ymlContent = $ymlContent -replace "api-version: '.*?'", "api-version: '$newVersion'"
$ymlContent | Set-Content "src/main/resources/plugin.yml" -Encoding UTF8

# 尝试读取保存的 Token 和用户名
$tokenPath = ".github_token"
$usernamePath = ".github_username"

if ((Test-Path $tokenPath) -and (Test-Path $usernamePath)) {
    $githubTokenText = Get-Content $tokenPath
    $githubUsername = Get-Content $usernamePath
    Write-Host "已读取保存的 GitHub 配置" -ForegroundColor Green
} else {
    # 获取 GitHub 用户名和令牌
    $githubUsername = Read-Host "请输入你的 GitHub 用户名"
    $githubToken = Read-Host "请输入你的 GitHub Token (从 https://github.com/settings/tokens 获取，之后会保存不用重复输入)" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($githubToken)
    $githubTokenText = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
    
    # 保存配置
    $githubTokenText | Set-Content $tokenPath
    $githubUsername | Set-Content $usernamePath
    Write-Host "GitHub 配置已保存，下次无需重新输入" -ForegroundColor Green
}

# Git 操作
if (-not (Test-Path ".git")) {
    Write-Host "`n初始化 Git 仓库..." -ForegroundColor Green
    git init

    Write-Host "`n配置 Git 用户信息..." -ForegroundColor Green
    $gitName = Read-Host "请输入你的名字（用于 Git 提交）"
    $gitEmail = Read-Host "请输入你的邮箱（用于 Git 提交）"
    git config user.name $gitName
    git config user.email $gitEmail
}

# 构建项目
Write-Host "`n构建项目..." -ForegroundColor Green
mvn clean package
if ($LASTEXITCODE -ne 0) {
    Write-Host "构建失败！" -ForegroundColor Red
    pause
    exit 1
}

# Git 操作
Write-Host "`n添加文件到 Git..." -ForegroundColor Green
git add .

Write-Host "`n提交更改..." -ForegroundColor Green
git commit -m "Update MCWordGame plugin to version $newVersion"

Write-Host "`n创建版本标签..." -ForegroundColor Green
git tag -a "v$newVersion" -m "Version $newVersion"

# 推送到 GitHub
Write-Host "`n推送到 GitHub..." -ForegroundColor Green
$remoteUrl = "https://${githubUsername}:${githubTokenText}@github.com/${githubUsername}/MCWordGame.git"
git remote set-url origin $remoteUrl
git push -u origin main --force
git push origin --tags --force

# 创建 Release
Write-Host "`n创建 Release..." -ForegroundColor Green
$jarFile = Get-ChildItem "target/MCWordGame-*.jar" | Select-Object -First 1
if ($jarFile) {
    $releaseData = @{
        tag_name = "v$newVersion"
        name = "Version $newVersion"
        body = "MCWordGame Plugin Version $newVersion"
        draft = $false
        prerelease = $false
    } | ConvertTo-Json

    try {
        # 创建 Release
        $release = Invoke-RestMethod `
            -Uri "https://api.github.com/repos/$githubUsername/MCWordGame/releases" `
            -Method Post `
            -Headers @{
                Authorization = "token $githubTokenText"
                Accept = "application/vnd.github.v3+json"
            } `
            -Body $releaseData `
            -ContentType "application/json"

        # 上传构建好的 jar 文件
        $uploadUrl = $release.upload_url -replace "{.*}$", ""
        $fileName = $jarFile.Name
        
        Invoke-RestMethod `
            -Uri "${uploadUrl}?name=${fileName}" `
            -Method Post `
            -Headers @{
                Authorization = "token $githubTokenText"
                Accept = "application/vnd.github.v3+json"
            } `
            -InFile $jarFile.FullName `
            -ContentType "application/java-archive"

        Write-Host "Release 创建成功！" -ForegroundColor Green
    } catch {
        Write-Host "创建 Release 失败：" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
} else {
    Write-Host "未找到构建好的 jar 文件！" -ForegroundColor Red
}

Write-Host "`n=== 部署完成！===" -ForegroundColor Green
Write-Host "版本已更新至: $newVersion" -ForegroundColor Cyan
Write-Host "你的插件已经上传到：https://github.com/$githubUsername/MCWordGame" -ForegroundColor Yellow
Write-Host "构建好的插件在 target 目录下" -ForegroundColor Yellow
Write-Host ""

pause 