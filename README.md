# MCWordGame (文字竞速游戏)

![Version](https://img.shields.io/badge/Version-1.5.0-green.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.4-blue.svg)
![Paper](https://img.shields.io/badge/Paper-Latest-yellow.svg)

一个有趣的 Minecraft 文字竞速游戏插件，支持多种奖励方式和自定义词库。

## 🌟 特性

- 💎 多种奖励类型：经验值、物品、金币(需要Vault)
- 👥 支持多人获奖
- 📚 自定义词库系统
- ⏱️ 可调节游戏时间
- 🏆 胜利排行榜
- 🎮 炫酷的游戏界面
- 🔊 游戏音效

## 📋 命令

### 基础命令
- `/mcwg start` - 开始一场游戏
- `/mcwg top` - 查看胜利排行榜

### 游戏指令详解
```
/mcwg start xp <数量> [custom <词库>] [winners <人数>] [time <秒数>]
/mcwg start item <物品> <数量> [custom <词库>] [winners <人数>] [time <秒数>]
/mcwg start money <数量> [custom <词库>] [winners <人数>] [time <秒数>]
```

### 管理员命令
- `/mcwg reload` - 重载插件配置
- `/mcwg wordlist list` - 查看所有词库
- `/mcwg wordlist create <名称> [词语...]` - 创建词库
- `/mcwg wordlist delete <名称>` - 删除词库
- `/mcwg wordlist info <名称>` - 查看词库信息
- `/mcwg wordlist setdefault <名称>` - 设置默认词库

## 🔧 配置文件

```yaml
settings:
  # 是否允许普通玩家开始游戏（需要支付对应奖励）
  allow-player-start: true
  # 默认词库设置（default为内置词库）
  word-list: default
  # 自定义词库最小词语数量
  min-words: 10
  # 默认获奖人数
  default-winners: 1
  # 默认游戏时间（秒）
  default-time: 30
```

## 📥 安装

1. 下载最新版本的 MCWordGame.jar
2. 将文件放入服务器的 plugins 文件夹
3. 重启服务器或重载插件
4. 如需使用金币奖励功能，请确保已安装 Vault 插件

## 🎮 使用示例

1. 经验值奖励游戏：
   ```
   /mcwg start xp 100
   ```

2. 物品奖励游戏：
   ```
   /mcwg start item DIAMOND 5
   ```

3. 三人获奖的金币游戏：
   ```
   /mcwg start money 1000 winners 3
   ```

4. 使用自定义词库：
   ```
   /mcwg start xp 100 custom words
   ```

5. 设置60秒游戏时间：
   ```
   /mcwg start xp 100 time 60
   ```

## 📝 自定义词库

1. 在插件目录创建 .txt 文件
2. 每行输入一个词语
3. 使用 `/mcwg wordlist setdefault` 设置为默认词库
4. 或在游戏时使用 `custom` 参数临时使用

## 🔒 权限

- `mcwordgame.use` - 允许使用基础命令（默认所有人）
- OP 可以使用所有命令

## 📞 支持

- 作者：Codetea
- 问题反馈：[GitHub Issues](https://github.com/yourusername/MCWordGame/issues)

##  许可证

本项目采用 MIT 许可证 