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

# 更新版本号
Write-Host "`n更新 pom.xml 版本号..." -ForegroundColor Green
mvn versions:set -DnewVersion=$newVersion -DgenerateBackupPoms=false

# 更新 plugin.yml
Write-Host "`n更新 plugin.yml 版本号..." -ForegroundColor Green
$content = Get-Content "src/main/resources/plugin.yml" -Encoding UTF8
$content = $content -replace "version: '.*'", "version: '$newVersion'"
$content | Set-Content "src/main/resources/plugin.yml" -Encoding UTF8

# 获取 GitHub 用户名
$githubUsername = Read-Host "请输入你的 GitHub 用户名"

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

# 创建 GitHub Actions 配置
if (-not (Test-Path ".github\workflows")) {
    New-Item -ItemType Directory -Path ".github\workflows" -Force | Out-Null
}

Write-Host "`n创建 GitHub Actions 配置..." -ForegroundColor Green
$workflowContent = @"
name: Build MCWordGame

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven
        
    - name: Build with Maven
      run: mvn -B package --file pom.xml
      
    - name: Upload artifact
      uses: actions/upload-artifact@v3
      with:
        name: MCWordGame
        path: target/MCWordGame-*.jar

    - name: Create Release
      if: startsWith(github.ref, 'refs/tags/')
      uses: softprops/action-gh-release@v1
      with:
        files: target/MCWordGame-*.jar
        generate_release_notes: true
"@
$workflowContent | Set-Content ".github\workflows\build.yml" -Encoding UTF8

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

# 检查远程仓库
$remoteExists = git remote -v | Select-String "origin"
if (-not $remoteExists) {
    Write-Host "`n创建 GitHub 仓库..." -ForegroundColor Green
    $body = @{
        name = "MCWordGame"
        description = "Minecraft 文字竞速游戏插件"
        private = $false
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "https://api.github.com/user/repos" `
        -Method Post -Headers @{Authorization = "Basic $([Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$($githubUsername):$($githubToken)")))"} `
        -Body $body -ContentType "application/json"

    Write-Host "`n添加远程仓库..." -ForegroundColor Green
    git remote add origin "https://github.com/$githubUsername/MCWordGame.git"
}

# 推送到 GitHub
Write-Host "`n推送到 GitHub..." -ForegroundColor Green
git push -u origin main --tags

Write-Host "`n=== 部署完成！===" -ForegroundColor Green
Write-Host "版本已更新至: $newVersion" -ForegroundColor Cyan
Write-Host "你的插件已经上传到：https://github.com/$githubUsername/MCWordGame" -ForegroundColor Yellow
Write-Host "构建好的插件在 target 目录下" -ForegroundColor Yellow
Write-Host "GitHub Actions 将自动构建并创建发布" -ForegroundColor Yellow
Write-Host ""

pause 