@echo off
echo 正在构建 MCWordGame 插件...

REM 检查是否安装了Maven
mvn -v > nul 2>&1
if errorlevel 1 (
    echo 请先安装Maven并添加到系统环境变量PATH中
    echo 下载地址 https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip
    pause
    exit /b 1
)

REM 运行Maven构建
mvn clean package

echo.
if errorlevel 1 (
    echo 构建失败，请检查错误信息
) else (
    echo 构建成功！
    echo 插件文件已经生成在 target 文件夹中
    echo 文件名为：mcwordgame-1.0-SNAPSHOT.jar
)

pause 