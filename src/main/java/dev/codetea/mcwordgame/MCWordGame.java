package dev.codetea.mcwordgame;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.io.PrintWriter;
import java.io.FileWriter;

public class MCWordGame extends JavaPlugin implements Listener {
    private static final String[] DEFAULT_WORDS = {
        "apple", "banana", "computer", "diamond", "elephant",
        "forest", "guitar", "house", "island", "jungle",
        "keyboard", "lemon", "mountain", "notebook", "orange",
        "book", "cat", "dog", "egg", "fish",
        "game", "hat", "ice", "jam", "king",
        "lamp", "moon", "nest", "owl", "pen",
        "queen", "rain", "sun", "tree", "umbrella",
        "van", "water", "box", "year", "zebra",
        "beautiful", "chocolate", "dangerous", "education", "fantastic",
        "grateful", "happiness", "important", "journey", "knowledge",
        "language", "medicine", "necessary", "opposite", "possible",
        "achievement", "beneficial", "confidence", "determined", "environment",
        "foundation", "generation", "historical", "incredible", "javascript"
    };
    
    private String[] currentWordList = DEFAULT_WORDS;
    private boolean gameRunning = false;
    private String currentWord = null;
    private Player gameStarter = null;
    private RewardInfo currentReward = null;
    private Economy econ = null;
    private Map<String, Integer> playerWins = new ConcurrentHashMap<>();
    private boolean allowPlayerStart;
    private int minWords;
    private int defaultWinners;
    private List<Player> winners = new ArrayList<>();
    private int currentWinners;
    private int defaultTime;
    private BukkitRunnable gameTimer;

    // 添加 RewardInfo 内部类
    private static class RewardInfo {
        final String type;  // 奖励类型：xp, item, money
        final Object value; // 奖励值（对于物品是Material，对于金币和经验是数值）
        final int amount;   // 奖励数量

        RewardInfo(String type, Object value, int amount) {
            this.type = type;
            this.value = value;
            this.amount = amount;
        }
    }

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        // 加载配置
        loadConfig();
        
        if (!setupEconomy()) {
            getLogger().severe("未找到Vault插件或经济系统！金币奖励功能将被禁用！");
        }

        MCWGCommand commandExecutor = new MCWGCommand(this);
        this.getCommand("mcwg").setExecutor(commandExecutor);
        this.getCommand("mcwg").setTabCompleter(commandExecutor);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MCWordGame 插件已启用！");
        getLogger().info("MADE BY Codetea");
        loadPlayerData();
    }

    private void loadConfig() {
        reloadConfig();
        allowPlayerStart = getConfig().getBoolean("settings.allow-player-start", true);
        minWords = getConfig().getInt("settings.min-words", 10);
        defaultWinners = getConfig().getInt("settings.default-winners", 1);
        defaultTime = getConfig().getInt("settings.default-time", 30);
        
        // 加载词库
        String wordListName = getConfig().getString("settings.word-list", "default");
        if (wordListName.equalsIgnoreCase("default")) {
            currentWordList = DEFAULT_WORDS;
            getLogger().info("使用默认词库");
        } else {
            loadCustomWordList(wordListName);
        }
    }

    private void loadCustomWordList(String fileName) {
        File wordFile = new File(getDataFolder(), fileName + ".txt");
        if (!wordFile.exists()) {
            getLogger().warning("找不到词库文件: " + fileName + ".txt，将使用默认词库");
            currentWordList = DEFAULT_WORDS;
            return;
        }

        try {
            List<String> words = new ArrayList<>();
            Scanner scanner = new Scanner(wordFile, "UTF-8");
            
            while (scanner.hasNext()) {
                // 读取单词，去除首尾空白，并过滤掉空行
                String word = scanner.next().trim();
                if (!word.isEmpty()) {
                    words.add(word.toLowerCase());
                }
            }
            
            scanner.close();

            if (words.isEmpty()) {
                getLogger().warning("词库文件为空，将使用默认词库");
                currentWordList = DEFAULT_WORDS;
            } else {
                currentWordList = words.toArray(new String[0]);
                getLogger().info("成功加载自定义词库: " + fileName + ".txt，包含 " + words.size() + " 个单词");
            }
        } catch (IOException e) {
            getLogger().severe("读取词库文件时出错: " + e.getMessage());
            getLogger().warning("将使用默认词库");
            currentWordList = DEFAULT_WORDS;
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public void onDisable() {
        if (gameTimer != null) {
            gameTimer.cancel();
        }
        savePlayerData();
        getLogger().info("MCWordGame 插件已禁用！");
    }

    private void loadPlayerData() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "playerdata.yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = config.getConfigurationSection("wins");
            if (section != null) {
                for (String player : section.getKeys(false)) {
                    playerWins.put(player, section.getInt(player));
                }
            }
        }
    }

    private void savePlayerData() {
        File file = new File(getDataFolder(), "playerdata.yml");
        YamlConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<String, Integer> entry : playerWins.entrySet()) {
            config.set("wins." + entry.getKey(), entry.getValue());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().severe("无法保存玩家数据: " + e.getMessage());
        }
    }

    private void updatePlayerWins(Player player) {
        String playerName = player.getName();
        playerWins.put(playerName, playerWins.getOrDefault(playerName, 0) + 1);
        savePlayerData();
    }

    private String getPlayerRank(String playerName) {
        List<Map.Entry<String, Integer>> sortedPlayers = playerWins.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());

        int rank = 1;
        for (Map.Entry<String, Integer> entry : sortedPlayers) {
            if (entry.getKey().equals(playerName)) {
                return String.valueOf(rank);
            }
            rank++;
        }
        return "未上榜";
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!gameRunning || currentWord == null) return;

        String message = event.getMessage().toLowerCase().trim();
        if (message.equals(currentWord)) {
            // 不拦截聊天消息
            // event.setCancelled(true);
            Player winner = event.getPlayer();
            
            Bukkit.getScheduler().runTask(this, () -> {
                if (!winners.contains(winner)) {
                    winners.add(winner);
                    // 使用 title 显示获胜信息
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        player.sendTitle(
                            "§a" + winner.getName() + " §e获得第 " + winners.size() + " 名！",
                            "§7还有 " + (currentWinners - winners.size()) + " 个获奖名额",
                            10, 40, 10
                        );
                    });
                    
                    if (winners.size() >= currentWinners) {
                        endGame();
                    }
                }
            });
        }
    }

    private void endGame() {
        gameRunning = false;
        currentWord = null;

        // 计算每个人的奖励
        if (currentReward != null && !winners.isEmpty()) {
            int baseAmount = currentReward.amount / currentWinners;
            int extraAmount = currentReward.amount % currentWinners;

            // 使用 title 显示游戏结束
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendTitle(
                    "§a游戏结束",
                    "§e正在发放奖励...",
                    10, 40, 10
                );
            });

            for (int i = 0; i < winners.size(); i++) {
                Player winner = winners.get(i);
                int amount = baseAmount + (i < extraAmount ? 1 : 0);
                giveReward(winner, amount);
                updatePlayerWins(winner);
            }
        }

        winners.clear();
        // 最后显示一个结束的 title
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.sendTitle(
                "§6游戏结束",
                "§a感谢参与！",
                10, 40, 10
            );
        });
    }

    private void giveReward(Player player, int amount) {
        if (currentReward == null) return;

        switch (currentReward.type) {
            case "xp":
                player.giveExp(amount);
                Bukkit.broadcastMessage("§a[MCWordGame] " + player.getName() + " 获得了 " + amount + " 点经验值！");
                break;
            case "item":
                ItemStack item = new ItemStack((Material) currentReward.value, amount);
                player.getInventory().addItem(item);
                Bukkit.broadcastMessage("§a[MCWordGame] " + player.getName() + " 获得了 " + amount + " 个 " + 
                    ((Material) currentReward.value).name() + "！");
                break;
            case "money":
                if (econ != null) {
                    econ.depositPlayer(player, amount);
                    Bukkit.broadcastMessage("§a[MCWordGame] " + player.getName() + " 获得了 " + amount + " 金币！");
                }
                break;
        }
    }

    private void startGame(Player starter, RewardInfo reward) {
        if (gameRunning) {
            Bukkit.broadcastMessage("§c[MCWordGame] 游戏正在进行中！");
            return;
        }

        gameRunning = true;
        gameStarter = starter;
        currentReward = reward;
        winners.clear();

        // 使用 title 显示倒计时
        new BukkitRunnable() {
            int count = 3;

            @Override
            public void run() {
                if (count > 0) {
                    // 倒计时使用大号 title
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        player.sendTitle(
                            "§e" + count,
                            "§b游戏即将开始...",
                            5, 20, 5
                        );
                        // 播放音效增加趣味性
                        player.playSound(player.getLocation(), "block.note_block.pling", 1.0f, 1.0f);
                    });
                    count--;
                } else {
                    currentWord = currentWordList[new Random().nextInt(currentWordList.length)];
                    // 游戏开始时显示单词
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        player.sendTitle(
                            "§a游戏开始！",
                            "§e" + currentWord,
                            10, 40, 10
                        );
                        // 播放游戏开始音效
                        player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
                    });
                    // 在聊天栏也显示一次单词，方便复制
                    Bukkit.broadcastMessage("§a[MCWordGame] 游戏开始！请在聊天框中输入: §e" + currentWord);
                    startGameTimer(defaultTime);
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startGameTimer(int seconds) {
        if (gameTimer != null) {
            gameTimer.cancel();
        }

        gameTimer = new BukkitRunnable() {
            int timeLeft = seconds;

            @Override
            public void run() {
                if (!gameRunning) {
                    this.cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    // 时间到，结束游戏
                    if (winners.isEmpty()) {
                        // 没有人参与，退回奖励
                        if (gameStarter != null && !gameStarter.isOp()) {
                            refundReward(gameStarter);
                            // 使用 title 显示游戏结束
                            Bukkit.getOnlinePlayers().forEach(player -> {
                                player.sendTitle(
                                    "§c游戏结束",
                                    "§7没有人参与，已退回奖励",
                                    10, 40, 10
                                );
                            });
                        } else {
                            Bukkit.getOnlinePlayers().forEach(player -> {
                                player.sendTitle(
                                    "§c游戏结束",
                                    "§7没有人获胜",
                                    10, 40, 10
                                );
                            });
                        }
                    } else {
                        // 有人参与，分发奖励
                        endGame();
                    }
                    this.cancel();
                } else if (timeLeft <= 5) {
                    // 最后5秒使用 title 显示倒计时
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        player.sendTitle(
                            "§e" + timeLeft,
                            "§7秒后结束",
                            0, 20, 0
                        );
                    });
                } else if (timeLeft == 10 || timeLeft == 20 || timeLeft == 30) {
                    // 重要时间点使用 title 显示
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        player.sendTitle(
                            "§e还剩 " + timeLeft + " 秒",
                            "§7当前单词: §e" + currentWord,
                            10, 20, 10
                        );
                    });
                }
                timeLeft--;
            }
        };
        gameTimer.runTaskTimer(this, 0L, 20L);
    }

    private void refundReward(Player player) {
        if (currentReward == null) return;

        switch (currentReward.type) {
            case "xp":
                player.giveExp(currentReward.amount);
                player.sendMessage("§a[MCWordGame] 已退回 " + currentReward.amount + " 点经验值！");
                break;
            case "item":
                ItemStack item = new ItemStack((Material) currentReward.value, currentReward.amount);
                player.getInventory().addItem(item);
                player.sendMessage("§a[MCWordGame] 已退回 " + currentReward.amount + " 个 " + 
                    ((Material) currentReward.value).name() + "！");
                break;
            case "money":
                if (econ != null) {
                    econ.depositPlayer(player, currentReward.amount);
                    player.sendMessage("§a[MCWordGame] 已退回 " + currentReward.amount + " 金币！");
                }
                break;
        }
    }

    class MCWGCommand implements CommandExecutor, TabCompleter {
        private final MCWordGame plugin;

        public MCWGCommand(MCWordGame plugin) {
            this.plugin = plugin;
            plugin.getCommand("mcwg").setTabCompleter(this);
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                showHelp(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("top")) {
                showLeaderboard(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c只有管理员才能重载配置！");
                    return true;
                }
                plugin.loadConfig();
                sender.sendMessage("§a[MCWordGame] 配置已重载！");
                return true;
            }

            // 修改权限检查逻辑
            if (!sender.isOp() && !plugin.allowPlayerStart) {
                sender.sendMessage("§c只有管理员才能开始游戏！");
                return true;
            }

            if (!(sender instanceof Player) && !sender.isOp()) {
                sender.sendMessage("§c非OP的控制台不能开始游戏！");
                return true;
            }

            if (args[0].equalsIgnoreCase("start")) {
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /mcwg start <奖励类型> <参数...> [custom <词库名>]");
                    sender.sendMessage("§7示例: /mcwg start xp 100 custom words");
                    sender.sendMessage("§7不指定词库则使用配置文件中的设置");
                    return true;
                }

                String rewardType = args[1].toLowerCase();
                String wordListName = null;
                int winners = plugin.defaultWinners;
                int time = plugin.defaultTime;

                // 查找词库、获奖人数和时间参数
                for (int i = 0; i < args.length - 1; i++) {
                    if (args[i].equalsIgnoreCase("custom")) {
                        wordListName = args[i + 1];
                        args = removeArgs(args, i, 2);
                        i--;
                    } else if (args[i].equalsIgnoreCase("winners")) {
                        winners = Integer.parseInt(args[i + 1]);
                        args = removeArgs(args, i, 2);
                        i--;
                    } else if (args[i].equalsIgnoreCase("time")) {
                        time = Integer.parseInt(args[i + 1]);
                        if (time < 5) {
                            sender.sendMessage("§c游戏时间不能少于5秒！");
                            return true;
                        }
                        args = removeArgs(args, i, 2);
                        i--;
                    }
                }

                // 验证获奖人数
                if (winners <= 0) {
                    sender.sendMessage("§c获奖人数必须大于0！");
                    return true;
                }

                // 如果指定了词库，先尝试加载
                if (wordListName != null) {
                    if (wordListName.equalsIgnoreCase("default")) {
                        plugin.currentWordList = DEFAULT_WORDS;
                    } else {
                        File wordFile = new File(plugin.getDataFolder(), wordListName + ".txt");
                        if (!wordFile.exists()) {
                            sender.sendMessage("§c找不到词库：" + wordListName);
                            return true;
                        }
                        plugin.loadCustomWordList(wordListName);
                    }
                }

                try {
                    RewardInfo reward = null;
                    Player player = sender instanceof Player ? (Player) sender : null;
                    
                    switch (rewardType) {
                        case "xp":
                            if (args.length != 3) {
                                sender.sendMessage("§c用法: /mcwg start xp <数量> [custom <词库名>]");
                                sender.sendMessage("§7示例: /mcwg start xp 100 custom words");
                                return true;
                            }
                            int xpAmount = Integer.parseInt(args[2]);
                            if (xpAmount <= 0) {
                                sender.sendMessage("§c奖励数量必须大于0！");
                                return true;
                            }
                            // 检查玩家是否有足够的经验
                            if (!sender.isOp() && player != null) {
                                int totalXp = getTotalExperience(player);
                                if (totalXp < xpAmount) {
                                    sender.sendMessage("§c你没有足够的经验值！需要: " + xpAmount + ", 拥有: " + totalXp);
                                    return true;
                                }
                                player.setTotalExperience(0);
                                player.setLevel(0);
                                player.setExp(0);
                                player.giveExp(totalXp - xpAmount);
                            }
                            reward = new RewardInfo("xp", xpAmount, xpAmount);
                            break;
                            
                        case "item":
                            if (args.length != 4) {
                                sender.sendMessage("§c用法: /mcwg start item <物品> <数量> [custom <词库名>]");
                                sender.sendMessage("§7示例: /mcwg start item DIAMOND 5 custom words");
                                return true;
                            }
                            Material material = Material.valueOf(args[2].toUpperCase());
                            int itemAmount = Integer.parseInt(args[3]);
                            if (itemAmount <= 0) {
                                sender.sendMessage("§c奖励数量必须大于0！");
                                return true;
                            }
                            // 检查玩家是否有足够的物品
                            if (!sender.isOp() && player != null) {
                                if (!hasEnoughItems(player, material, itemAmount)) {
                                    sender.sendMessage("§c你没有足够的物品！");
                                    return true;
                                }
                                removeItems(player, material, itemAmount);
                            }
                            reward = new RewardInfo("item", material, itemAmount);
                            break;
                            
                        case "money":
                            if (args.length != 3) {
                                sender.sendMessage("§c用法: /mcwg start money <数量> [custom <词库名>]");
                                sender.sendMessage("§7示例: /mcwg start money 1000 custom words");
                                return true;
                            }
                            if (plugin.econ == null) {
                                sender.sendMessage("§c经济系统未启用，无法使用金币奖励！");
                                return true;
                            }
                            double moneyAmount = Double.parseDouble(args[2]);
                            if (moneyAmount <= 0) {
                                sender.sendMessage("§c奖励数量必须大于0！");
                                return true;
                            }
                            // 检查玩家是否有足够的金币
                            if (!sender.isOp() && player != null) {
                                if (plugin.econ.getBalance(player) < moneyAmount) {
                                    sender.sendMessage("§c你没有足够的金币！");
                                    return true;
                                }
                                plugin.econ.withdrawPlayer(player, moneyAmount);
                            }
                            reward = new RewardInfo("money", moneyAmount, (int) moneyAmount);
                            break;
                            
                        default:
                            sender.sendMessage("§c未知的奖励类型！");
                            sender.sendMessage("§7可用类型: xp（经验）, item（物品）, money（金币）");
                            return true;
                    }

                    // 检查奖励数量是否足够
                    if (reward.amount < winners) {
                        sender.sendMessage("§c奖励数量必须大于等于获奖人数！");
                        return true;
                    }

                    plugin.currentWinners = winners;
                    plugin.winners.clear();

                    Bukkit.broadcastMessage("§a[MCWordGame] 文字游戏即将开始！");
                    Bukkit.broadcastMessage("§a[MCWordGame] 本局游戏将有 " + winners + " 名获奖者！");
                    if (!sender.isOp() && player != null) {
                        Bukkit.broadcastMessage("§a[MCWordGame] 本局游戏由 " + player.getName() + " 发起并提供奖励！");
                    }

                    // 添加词库信息到开始提示
                    if (wordListName != null) {
                        Bukkit.broadcastMessage("§a[MCWordGame] 本局游戏使用词库: " + wordListName);
                    }

                    plugin.startGame(sender instanceof Player ? (Player) sender : null, reward);

                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§c参数错误！请检查输入的数值或物品名称是否正确。");
                    return true;
                }
            }

            // 添加词库管理指令处理
            if (args[0].equalsIgnoreCase("wordlist")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c只有管理员才能管理词库！");
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§c用法: /mcwg wordlist <list|create|delete|info|setdefault>");
                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "list":
                        List<String> wordLists = listWordLists();
                        sender.sendMessage("§6§l========= 词库列表 =========");
                        String defaultList = plugin.getConfig().getString("settings.word-list", "default");
                        for (String name : wordLists) {
                            if (name.equals("default")) {
                                sender.sendMessage("§e➤ §fdefault §7(内置词库，包含 " + DEFAULT_WORDS.length + " 个单词)" + 
                                    (name.equals(defaultList) ? " §a[默认]" : ""));
                            } else {
                                File wordFile = new File(plugin.getDataFolder(), name + ".txt");
                                try {
                                    List<String> words = new ArrayList<>();
                                    Scanner scanner = new Scanner(wordFile, "UTF-8");
                                    while (scanner.hasNext()) {
                                        words.add(scanner.next().trim());
                                    }
                                    scanner.close();
                                    sender.sendMessage("§e➤ §f" + name + " §7(包含 " + words.size() + " 个单词)" + 
                                        (name.equals(defaultList) ? " §a[默认]" : ""));
                                } catch (IOException e) {
                                    sender.sendMessage("§e➤ §f" + name + " §c(读取失败)");
                                }
                            }
                        }
                        sender.sendMessage("§6§l===========================");
                        break;

                    case "create":
                        if (args.length < 4) {
                            sender.sendMessage("§c用法: /mcwg wordlist create <词库名> <词语...>");
                            sender.sendMessage("§7示例: /mcwg wordlist create easy apple banana cat dog");
                            return true;
                        }
                        String name = args[2];
                        if (name.equalsIgnoreCase("default")) {
                            sender.sendMessage("§c不能使用 'default' 作为词库名！");
                return true;
            }
                        List<String> words = new ArrayList<>(Arrays.asList(args).subList(3, args.length));
                        if (createWordList(name, words)) {
                            sender.sendMessage("§a词库 '" + name + "' 创建成功！");
                        } else {
                            sender.sendMessage("§c词库创建失败！词语数量必须不少于 " + plugin.minWords + " 个");
                        }
                        break;

                    case "delete":
                        if (args.length != 3) {
                            sender.sendMessage("§c用法: /mcwg wordlist delete <词库名>");
                            return true;
                        }
                        if (deleteWordList(args[2])) {
                            sender.sendMessage("§a词库删除成功！");
                        } else {
                            sender.sendMessage("§c词库删除失败！默认词库无法删除");
                        }
                        break;

                    case "info":
                        if (args.length != 3) {
                            sender.sendMessage("§c用法: /mcwg wordlist info <词库名>");
                            return true;
                        }
                        String wordListName = args[2];
                        if (wordListName.equalsIgnoreCase("default")) {
                            sender.sendMessage("§6§l========= 词库信息 =========");
                            sender.sendMessage("§f词库名称: §edefault §7(内置词库)");
                            sender.sendMessage("§f词语数量: §a" + DEFAULT_WORDS.length);
                            sender.sendMessage("§f词语预览: §7" + String.join(", ", 
                                Arrays.copyOfRange(DEFAULT_WORDS, 0, Math.min(5, DEFAULT_WORDS.length))));
                            sender.sendMessage("§6§l=========================");
                        } else {
                            File wordFile = new File(plugin.getDataFolder(), wordListName + ".txt");
                            if (!wordFile.exists()) {
                                sender.sendMessage("§c找不到词库：" + wordListName);
                                return true;
                            }
                            try {
                                List<String> wordList = new ArrayList<>();
                                Scanner scanner = new Scanner(wordFile, "UTF-8");
                                while (scanner.hasNext()) {
                                    wordList.add(scanner.next().trim());
                                }
                                scanner.close();
                                sender.sendMessage("§6§l========= 词库信息 =========");
                                sender.sendMessage("§f词库名称: §e" + wordListName);
                                sender.sendMessage("§f词语数量: §a" + wordList.size());
                                sender.sendMessage("§f词语预览: §7" + String.join(", ", 
                                    wordList.subList(0, Math.min(5, wordList.size()))));
                                if (wordList.size() < plugin.minWords) {
                                    sender.sendMessage("§c警告：词语数量少于最小要求（" + plugin.minWords + "个）");
                                }
                                sender.sendMessage("§6§l=========================");
                            } catch (IOException e) {
                                sender.sendMessage("§c读取词库失败！");
                            }
                        }
                        break;

                    case "setdefault":
                        if (args.length != 3) {
                            sender.sendMessage("§c用法: /mcwg wordlist setdefault <词库名>");
                            return true;
                        }
                        String defaultName = args[2];
                        if (!defaultName.equals("default")) {
                            File wordFile = new File(plugin.getDataFolder(), defaultName + ".txt");
                            if (!wordFile.exists()) {
                                sender.sendMessage("§c找不到词库：" + defaultName);
                                return true;
                            }
                        }
                        plugin.getConfig().set("settings.word-list", defaultName);
                        plugin.saveConfig();
                        plugin.loadConfig(); // 重新加载配置和词库
                        sender.sendMessage("§a默认词库已设置为：" + defaultName);
                        break;

                    default:
                        sender.sendMessage("§c未知的词库管理指令！");
                        sender.sendMessage("§7可用指令: list, create, delete, info, setdefault");
                }
                return true;
            }

            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();
            
            if (args.length == 1) {
                // 第一个参数：主命令
                completions.addAll(Arrays.asList("start", "top"));
                if (sender.isOp()) {
                    completions.addAll(Arrays.asList("reload", "wordlist"));
                }
            } else if (args.length >= 2) {
                switch (args[0].toLowerCase()) {
                    case "start":
                        if (args.length == 2) {
                            // 第二个参数：奖励类型
                            completions.addAll(Arrays.asList("xp", "item", "money"));
                        } else if (args.length == 3) {
                            // 第三个参数：根据奖励类型提供不同的补全
                            switch (args[1].toLowerCase()) {
                                case "item":
                                    // 提供常用物品ID
                                    completions.addAll(Arrays.asList(
                                        "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
                                        "DIAMOND_BLOCK", "EMERALD_BLOCK", "GOLD_BLOCK", "IRON_BLOCK"
                                    ));
                                    break;
                                case "xp":
                                case "money":
                                    // 提供一些常用数值
                                    completions.addAll(Arrays.asList("100", "500", "1000"));
                                    break;
                            }
                        } else {
                            // 后续参数：可选参数
                            String prev = args[args.length - 2].toLowerCase();
                            if (prev.equals("custom")) {
                                // 词库名补全
                                completions.addAll(listWordLists());
                            } else if (prev.equals("winners")) {
                                // 获奖人数补全
                                completions.addAll(Arrays.asList("1", "2", "3", "5"));
                            } else if (prev.equals("time")) {
                                // 游戏时间补全
                                completions.addAll(Arrays.asList("10", "20", "30", "60"));
                            } else if (!Arrays.asList("custom", "winners", "time").contains(prev)) {
                                // 提供可选参数关键字
                                completions.addAll(Arrays.asList("custom", "winners", "time"));
                            }
                        }
                        break;

                    case "wordlist":
                        if (sender.isOp()) {
                            if (args.length == 2) {
                                // 词库管理子命令
                                completions.addAll(Arrays.asList("list", "create", "delete", "info", "setdefault"));
                            } else if (args.length == 3) {
                                // 词库名补全
                                String subCommand = args[1].toLowerCase();
                                if (subCommand.equals("delete") || subCommand.equals("info") || 
                                    subCommand.equals("setdefault")) {
                                    completions.addAll(listWordLists());
                                }
                            }
                        }
                        break;
                }
            }

            // 过滤补全结果
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
        }

        private void showLeaderboard(CommandSender sender) {
            List<Map.Entry<String, Integer>> topPlayers = plugin.playerWins.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

            sender.sendMessage("");
            sender.sendMessage("§6§l✧ §b§l============== §d§l胜利排行榜 §b§l============== §6§l✧");
            sender.sendMessage("");

            if (topPlayers.isEmpty()) {
                sender.sendMessage("§7暂无排行数据...");
            } else {
                for (int i = 0; i < Math.min(3, topPlayers.size()); i++) {
                    Map.Entry<String, Integer> entry = topPlayers.get(i);
                    String crown = "";
                    String color = "";
                    switch (i) {
                        case 0: 
                            crown = "§e§l👑"; 
                            color = "§e";
                            break;
                        case 1: 
                            crown = "§7§l👑";
                            color = "§7";
                            break;
                        case 2: 
                            crown = "§6§l👑";
                            color = "§6";
                            break;
                    }
                    sender.sendMessage(" " + crown + " " + color + "第" + (i + 1) + "名 §f" + 
                        entry.getKey() + color + " - §b" + entry.getValue() + " §7场胜利");
                }
            }

            if (sender instanceof Player) {
                sender.sendMessage("");
                String playerName = sender.getName();
                String rank = getPlayerRank(playerName);
                int wins = plugin.playerWins.getOrDefault(playerName, 0);
                sender.sendMessage("§a§l你的战绩：");
                sender.sendMessage(" §f排名: §e#" + rank + " §7- §b" + wins + " §7场胜利");
            }

            sender.sendMessage("");
            sender.sendMessage("§6§l✧ §b§l====================================== §6§l✧");
            sender.sendMessage("");
        }

        private void showHelp(CommandSender sender) {
            sender.sendMessage("");
            sender.sendMessage("§6§l✧ §b§l================ §d§lMCWordGame §b§l================ §6§l✧");
            sender.sendMessage("");
            
            // 基础指令部分
            sender.sendMessage("§d§l基础指令：");
            sender.sendMessage(" §e➤ §f/mcwg start §7- 开始一场文字竞速游戏");
            sender.sendMessage(" §e➤ §f/mcwg top §7- 查看胜利次数排行榜");
            sender.sendMessage("");
            
            // 开始游戏指令详解
            sender.sendMessage("§d§l游戏指令详解：");
            sender.sendMessage(" §b✦ §f经验奖励： §e/mcwg start xp <数量> §7[custom <词库>] [winners <人数>] [time <秒数>]");
            sender.sendMessage(" §b✦ §f物品奖励： §e/mcwg start item <物品> <数量> §7[custom <词库>] [winners <人数>] [time <秒数>]");
            sender.sendMessage(" §b✦ §f金币奖励： §e/mcwg start money <数量> §7[custom <词库>] [winners <人数>] [time <秒数>]");
            sender.sendMessage("");

            // 可选参数说明
            sender.sendMessage("§d§l可选参数：");
            sender.sendMessage(" §a❈ §fcustom <词库名> §7- 使用指定词库进行游戏");
            sender.sendMessage(" §a❈ §fwinners <数量> §7- 设置获奖人数 §8(默认: " + plugin.defaultWinners + "人)");
            sender.sendMessage(" §a❈ §ftime <秒数> §7- 设置游戏时间 §8(默认: " + plugin.defaultTime + "秒)");
            sender.sendMessage("");

            // 示例部分
            sender.sendMessage("§d§l使用示例：");
            sender.sendMessage(" §3➊ §f/mcwg start xp 100 §7- 经验奖励游戏");
            sender.sendMessage(" §3➋ §f/mcwg start item DIAMOND 5 §7- 物品奖励游戏");
            sender.sendMessage(" §3➌ §f/mcwg start money 1000 winners 3 §7- 3人获奖的金币游戏");
            sender.sendMessage(" §3➍ §f/mcwg start xp 100 custom words §7- 使用自定义词库");
            sender.sendMessage(" §3➎ §f/mcwg start xp 100 time 60 §7- 设置60秒游戏时间");
            sender.sendMessage("");

            // 管理员指令
            if (sender.isOp()) {
                sender.sendMessage("§d§l管理员指令：");
                sender.sendMessage(" §c⚡ §f/mcwg reload §7- 重载插件配置");
                sender.sendMessage(" §c⚡ §f/mcwg wordlist list §7- 查看所有词库");
                sender.sendMessage(" §c⚡ §f/mcwg wordlist create <名称> [词语...] §7- 创建词库");
                sender.sendMessage(" §c⚡ §f/mcwg wordlist delete <名称> §7- 删除词库");
                sender.sendMessage(" §c⚡ §f/mcwg wordlist info <名称> §7- 查看词库信息");
                sender.sendMessage(" §c⚡ §f/mcwg wordlist setdefault <名称> §7- 设置默认词库");
                sender.sendMessage("");
            }

            // 权限提示
            if (!sender.isOp()) {
                sender.sendMessage("§d§l权限说明：");
                if (plugin.allowPlayerStart) {
                    sender.sendMessage(" §e⚠ §a你可以通过支付对应奖励来开始游戏");
                    sender.sendMessage(" §7   例如：/mcwg start money 100 需要支付100金币");
                } else {
                    sender.sendMessage(" §e⚠ §c目前仅允许管理员开始游戏");
                }
                sender.sendMessage("");
            }

            // 物品ID提示
            if (sender instanceof Player) {
                sender.sendMessage("§d§l常用物品ID：");
                sender.sendMessage(" §b⚔ §fDIAMOND§7(钻石) §b⚔ §fEMERALD§7(绿宝石)");
                sender.sendMessage(" §b⚔ §fGOLD_INGOT§7(金锭) §b⚔ §fIRON_INGOT§7(铁锭)");
                sender.sendMessage("");
            }

            sender.sendMessage("§6§l✧ §b§l============================================ §6§l✧");
            sender.sendMessage("");
        }

        // 新增的辅助方法
        private boolean hasEnoughItems(Player player, Material material, int amount) {
            int count = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    count += item.getAmount();
                }
            }
            return count >= amount;
        }

        private void removeItems(Player player, Material material, int amount) {
            int remaining = amount;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (item != null && item.getType() == material) {
                    if (item.getAmount() <= remaining) {
                        remaining -= item.getAmount();
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - remaining);
                        remaining = 0;
                    }
                }
            }
        }

        private int getTotalExperience(Player player) {
            int level = player.getLevel();
            float exp = player.getExp();
            int total = 0;
            
            // 计算等级经验
            for (int i = 0; i < level; i++) {
                if (i >= 30) {
                    total += 112 + (i - 30) * 9;
                } else if (i >= 15) {
                    total += 37 + (i - 15) * 5;
                } else {
                    total += 7 + i * 2;
                }
            }
            
            // 添加当前等级的部分经验
            int current = 0;
            if (level >= 30) {
                current = (int) ((112 + (level - 30) * 9) * exp);
            } else if (level >= 15) {
                current = (int) ((37 + (level - 15) * 5) * exp);
            } else {
                current = (int) ((7 + level * 2) * exp);
            }
            
            return total + current;
        }

        // 添加词库管理指令
        private boolean createWordList(String name, List<String> words) {
            if (words.size() < plugin.minWords) {
                return false;
            }

            File wordFile = new File(plugin.getDataFolder(), name + ".txt");
            try {
                if (!wordFile.exists()) {
                    wordFile.createNewFile();
                }
                
                PrintWriter writer = new PrintWriter(new FileWriter(wordFile));
                for (String word : words) {
                    writer.println(word.trim().toLowerCase());
                }
                writer.close();
                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("创建词库文件时出错: " + e.getMessage());
                return false;
            }
        }

        private List<String> listWordLists() {
            List<String> wordLists = new ArrayList<>();
            wordLists.add("default");
            
            File dataFolder = plugin.getDataFolder();
            if (dataFolder.exists()) {
                File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".txt"));
                if (files != null) {
                    for (File file : files) {
                        wordLists.add(file.getName().replace(".txt", ""));
                    }
                }
            }
            
            return wordLists;
        }

        private boolean deleteWordList(String name) {
            if (name.equalsIgnoreCase("default")) {
                return false;
            }
            
            File wordFile = new File(plugin.getDataFolder(), name + ".txt");
            return wordFile.exists() && wordFile.delete();
        }

        private String[] removeArgs(String[] args, int start, int count) {
            String[] newArgs = new String[args.length - count];
            System.arraycopy(args, 0, newArgs, 0, start);
            if (start + count < args.length) {
                System.arraycopy(args, start + count, newArgs, start, args.length - start - count);
            }
            return newArgs;
        }
    }
} 