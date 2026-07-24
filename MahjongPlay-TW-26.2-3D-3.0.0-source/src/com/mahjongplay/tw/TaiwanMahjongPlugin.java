package com.mahjongplay.tw;

import com.mahjongplay.tw.TaiwanMahjongEngine.Claim;
import com.mahjongplay.tw.TaiwanMahjongEngine.ClaimKind;
import com.mahjongplay.tw.TaiwanMahjongEngine.GameState;
import com.mahjongplay.tw.TaiwanMahjongEngine.Meld;
import com.mahjongplay.tw.TaiwanMahjongEngine.Phase;
import com.mahjongplay.tw.TaiwanMahjongEngine.PlayerState;
import com.mahjongplay.tw.TaiwanMahjongEngine.Tile;
import com.mahjongplay.tw.TaiwanMahjongEngine.TileType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import static com.mahjongplay.tw.TaiwanMahjongEngine.*;

/**
 * MahjongPlay 台灣 16 張實體 3D 牌桌版。
 *
 * Minecraft/Paper 26.2：
 * - 以方塊建立牌桌。
 * - 以 ItemDisplay 顯示牌面與牌背。
 * - 以 Interaction 接收右鍵操作。
 * - 對每位玩家使用實體可見性，自己的牌顯示正面，別人的牌顯示牌背。
 */
public final class TaiwanMahjongPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String PREFIX = "§6[台麻] §f";
    private static final float TILE_SCALE = 0.15f;
    private static final double TILE_SPACING = 0.1325;
    private final Manager manager = new Manager();
    private final Map<UUID, ClickAction> clickActions = new HashMap<>();
    private final Map<UUID, Long> lastClicks = new HashMap<>();

    @Override
    public void onEnable() {
        PluginCommand command = getCommand("mahjong");
        if (command == null) {
            getLogger().severe("plugin.yml 缺少 mahjong 指令，插件無法啟用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        manager.loadTables();
        getServer().getScheduler().runTaskTimer(this, manager::tick, 10L, 10L);
        getLogger().info("MahjongPlay 台灣 16 張 3D 牌桌版已啟用。已載入 " + manager.tables.size() + " 張牌桌。");
    }

    @Override
    public void onDisable() {
        manager.saveTables();
        for (Table table : manager.tables.values()) manager.clearEntities(table);
        manager.broadcastAll(PREFIX + "伺服器關閉，本次進行中的牌局已中止；實體牌桌方塊仍會保留。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "help", "幫助", "帮助" -> { sendHelp(sender); yield true; }
                case "rules", "rule", "規則", "规则" -> { sendRules(sender); yield true; }
                case "list", "列表" -> { manager.listTables(sender); yield true; }
                case "create", "建立", "創建", "创建" -> handleCreate(sender, args);
                case "destroy", "刪除", "删除" -> handleDestroy(sender, args);
                case "protection", "protect", "保護", "保护" -> handleProtection(sender, args);
                case "join", "加入" -> handlePlayer(sender, p -> manager.join(p, args.length >= 2 ? args[1] : null));
                case "leave", "離開", "离开" -> handlePlayer(sender, manager::leave);
                case "ready", "準備", "准备" -> handlePlayer(sender, p -> manager.ready(p, true));
                case "unready", "取消準備", "取消准备" -> handlePlayer(sender, p -> manager.ready(p, false));
                case "bot", "電腦", "电脑" -> handlePlayer(sender, manager::addBot);
                case "kick", "踢出" -> handleKick(sender, args);
                case "start", "開始", "开始" -> handlePlayer(sender, manager::start);
                case "hand", "手牌" -> handlePlayer(sender, manager::showHandHint);
                case "info", "資訊", "信息" -> handlePlayer(sender, manager::info);
                case "score", "分數", "分数" -> handlePlayer(sender, manager::score);
                case "discard", "出牌" -> handleDiscard(sender, args);
                case "action", "動作", "动作" -> handleAction(sender, args);
                case "rerender", "重繪", "重绘" -> handlePlayer(sender, manager::rerender);
                default -> { sender.sendMessage(PREFIX + "未知子指令。輸入 /mahjong help 查看用法。"); yield true; }
            };
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "執行麻將指令時發生錯誤", ex);
            sender.sendMessage(PREFIX + "執行失敗，伺服器主控台已有錯誤紀錄。");
            return true;
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mahjongplay.command.create")) return noPermission(sender);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "實體牌桌必須由遊戲內玩家建立。");
            return true;
        }
        String name = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "台灣麻將桌";
        manager.create(player, name);
        return true;
    }

    private boolean handleDestroy(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mahjongplay.command.destroy")) return noPermission(sender);
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "用法：/mahjong destroy <牌桌編號>");
            return true;
        }
        manager.destroy(sender, args[1]);
        return true;
    }

    private boolean handleProtection(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mahjongplay.command.protection")) return noPermission(sender);
        if (args.length < 3) {
            sender.sendMessage(PREFIX + "用法：/mahjong protection <牌桌編號> <on|off>");
            return true;
        }
        Boolean enabled = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "enabled", "開", "开启", "啟用" -> true;
            case "off", "false", "disable", "disabled", "關", "关闭", "停用" -> false;
            default -> null;
        };
        if (enabled == null) {
            sender.sendMessage(PREFIX + "保護設定必須是 on 或 off。");
            return true;
        }
        manager.setBlockProtection(sender, args[1], enabled);
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mahjongplay.command.kick")) return noPermission(sender);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "此指令只能由玩家使用。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "用法：/mahjong kick <座位 1-4>");
            return true;
        }
        try {
            manager.kick(player, Integer.parseInt(args[1]) - 1);
        } catch (NumberFormatException ex) {
            sender.sendMessage(PREFIX + "座位必須是 1 到 4。");
        }
        return true;
    }

    private boolean handleDiscard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "此指令只能由玩家使用。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "用法：/mahjong discard <牌代碼>，例如 1m、9p、E、R、Wh");
            return true;
        }
        manager.discardByCode(player, args[1]);
        return true;
    }

    private boolean handleAction(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "此指令只能由玩家使用。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "用法：/mahjong action <hu|pong|kong|chi|pass> [選項或牌代碼]");
            return true;
        }
        manager.action(player, args[1], args.length >= 3 ? args[2] : null);
        return true;
    }

    private boolean handlePlayer(CommandSender sender, Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "此指令只能由玩家使用。");
            return true;
        }
        action.accept(player);
        return true;
    }

    private boolean noPermission(CommandSender sender) {
        sender.sendMessage(PREFIX + "你沒有權限使用這個指令。");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== MahjongPlay 台灣麻將 3D ==========");
        sender.sendMessage("§e/mahjong create [名稱] §7在面前建造實體牌桌");
        sender.sendMessage("§e右鍵桌中央 §7加入牌桌／切換準備");
        sender.sendMessage("§e右鍵自己的 3D 手牌 §7直接出牌");
        sender.sendMessage("§e/mahjong join [編號] §7加入牌桌；§e/mahjong leave §7離開大廳");
        sender.sendMessage("§e/mahjong ready §7準備；§e/mahjong bot §7加入電腦；§e/mahjong start §7開始");
        sender.sendMessage("§e/mahjong action hu|pong|kong|chi|pass §7文字備用操作");
        sender.sendMessage("§e/mahjong info §7資訊；§e/mahjong score §7分數；§e/mahjong rules §7規則");
        sender.sendMessage("§e/mahjong rerender §7重新生成顯示實體（看不到牌時使用）");
        sender.sendMessage("§e/mahjong protection <編號> <on|off> §7切換牌桌方塊保護（管理員）");
        sender.sendMessage("§c必須安裝隨附的 26.2 材質包，否則麻將牌會顯示成紙張。");
    }

    private void sendRules(CommandSender sender) {
        sender.sendMessage("§6========== 台灣 16 張規則 ==========");
        sender.sendMessage("§f牌組：§7144 張，含萬、筒、索、字牌與春夏秋冬梅蘭竹菊八花。");
        sender.sendMessage("§f起手：§7莊家 17 張，其餘 16 張；花牌與槓牌從牌尾補牌。");
        sender.sendMessage("§f胡牌：§75 組面子加 1 對將眼；支援吃、碰、明槓、暗槓、自摸、放槍與一炮多響。");
        sender.sendMessage("§f優先權：§7胡 > 碰／槓 > 吃；吃只能吃上家的牌。");
        sender.sendMessage("§f結算：§7每台 100 分；放槍者支付，自摸由其他三家各自支付；牌型可疊加。");
        sender.sendMessage("§f圈數：§7每位玩家至少坐莊一次後結束；莊家胡牌或流局會續莊。");
        sender.sendMessage("§c注意：§7台麻各地細則不同，本插件採固定預設。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("help", "rules", "list", "create", "destroy", "protection", "join", "leave", "ready", "unready", "bot", "kick", "start", "hand", "info", "score", "discard", "action", "rerender"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("destroy") || args[0].equalsIgnoreCase("protection") || args[0].equalsIgnoreCase("protect"))) {
            return filter(args[1], manager.tables.keySet());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("protection") || args[0].equalsIgnoreCase("protect"))) {
            return filter(args[2], List.of("on", "off"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("action")) {
            return filter(args[1], List.of("hu", "pong", "kong", "chi", "pass"));
        }
        return List.of();
    }

    private static List<String> filter(String prefix, Collection<String> values) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ClickAction action = clickActions.get(event.getRightClicked().getUniqueId());
        if (action == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long previous = lastClicks.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 220L) return;
        lastClicks.put(player.getUniqueId(), now);
        manager.handleClick(player, action);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        for (Table table : manager.tables.values()) {
            if (table.blockProtection && manager.isTableBlock(table, event.getBlock())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(PREFIX + "這是麻將牌桌的一部分，請用 /mahjong destroy " + table.id + " 刪除。");
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Table table = manager.tableOf(event.getPlayer());
        if (table != null) getServer().getScheduler().runTaskLater(this, () -> manager.renderTable(table), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Table table = manager.tableOf(event.getPlayer());
        if (table != null && table.game != null && table.game.phase != Phase.GAME_END) {
            manager.broadcast(table, PREFIX + event.getPlayer().getName() + " 離線，暫時由電腦代打；重新登入後可繼續操作。");
        }
    }

    private enum ActionKind { CENTER, DISCARD, SELF_HU, CONCEALED_KONG, CLAIM_HU, CLAIM_PONG, CLAIM_KONG, CLAIM_CHI, CLAIM_PASS }
    private record ClickAction(String tableId, ActionKind kind, int seat, int tileId, TileType tileType, int option) {
        static ClickAction center(String tableId) { return new ClickAction(tableId, ActionKind.CENTER, -1, -1, null, -1); }
        static ClickAction discard(String tableId, int seat, int tileId) { return new ClickAction(tableId, ActionKind.DISCARD, seat, tileId, null, -1); }
        static ClickAction simple(String tableId, ActionKind kind, int seat) { return new ClickAction(tableId, kind, seat, -1, null, -1); }
        static ClickAction kong(String tableId, int seat, TileType type) { return new ClickAction(tableId, ActionKind.CONCEALED_KONG, seat, -1, type, -1); }
        static ClickAction chi(String tableId, int seat, int option) { return new ClickAction(tableId, ActionKind.CLAIM_CHI, seat, -1, null, option); }
    }

    private static final class Seat {
        final String id;
        final String name;
        final boolean bot;
        boolean ready;
        Seat(String id, String name, boolean bot) { this.id = id; this.name = name; this.bot = bot; }
    }

    private static final class Table {
        final String id;
        String name;
        final String worldName;
        final int x;
        final int y;
        final int z;
        final List<Seat> seats = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        boolean blockProtection = true;
        GameState game;
        long claimDeadline;
        long botActionAt;
        long continueAt;
        long resetAt;
        Table(String id, String name, String worldName, int x, int y, int z) {
            this.id = id;
            this.name = name;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        World world() { return Bukkit.getWorld(worldName); }
        Location center() { return new Location(world(), x + 0.5, y + 1.0, z + 0.5); }
    }

    private final class Manager {
        final Map<String, Table> tables = new LinkedHashMap<>();
        final Map<String, String> playerTables = new HashMap<>();
        int nextTableId = 1;
        int nextBotId = 1;

        void create(Player player, String rawName) {
            Location p = player.getLocation();
            int[] forward = cardinalForward(p.getYaw());
            int x = p.getBlockX() + forward[0] * 4;
            int y = p.getBlockY() - 1;
            int z = p.getBlockZ() + forward[1] * 4;
            String id = Integer.toString(nextTableId++);
            Table table = new Table(id, cleanName(rawName), p.getWorld().getName(), x, y, z);
            for (Table existing : tables.values()) {
                if (existing.worldName.equals(table.worldName)
                        && distanceSquared(existing.x, existing.y, existing.z, x, y, z) < 49) {
                    player.sendMessage(PREFIX + "附近已有牌桌 #" + existing.id + "，請換一個位置再建立。");
                    return;
                }
            }
            tables.put(id, table);
            buildPhysicalTable(table);
            renderTable(table);
            saveTables();
            player.sendMessage(PREFIX + "已在你面前建立牌桌 §e#" + id + " §f「" + table.name + "」。");
            player.sendMessage(PREFIX + "右鍵桌中央即可加入；材質包未安裝時，3D 麻將牌會顯示成紙張。");
        }

        void destroy(CommandSender sender, String id) {
            Table table = tables.remove(id);
            if (table == null) {
                sender.sendMessage(PREFIX + "找不到牌桌 #" + id + "。");
                return;
            }
            for (Seat seat : table.seats) playerTables.remove(seat.id);
            broadcast(table, PREFIX + "牌桌已被管理員刪除。");
            clearEntities(table);
            removePhysicalTable(table);
            saveTables();
            sender.sendMessage(PREFIX + "已刪除牌桌 #" + id + " 及其方塊。");
        }

        void listTables(CommandSender sender) {
            if (tables.isEmpty()) {
                sender.sendMessage(PREFIX + "目前沒有牌桌。使用 /mahjong create 建立實體牌桌。");
                return;
            }
            sender.sendMessage("§6========== 牌桌列表 ==========");
            for (Table table : tables.values()) {
                String state = table.game == null ? "大廳" : switch (table.game.phase) {
                    case DISCARD -> "進行中";
                    case CLAIM -> "等待回應";
                    case ROUND_END -> "本局結束";
                    case GAME_END -> "整場結束";
                    default -> "大廳";
                };
                sender.sendMessage("§e#" + table.id + " §f" + table.name + " §7玩家 " + table.seats.size() + "/4，" + state
                        + "，方塊保護 " + (table.blockProtection ? "§a開啟" : "§c關閉")
                        + "§7，座標 " + table.x + " " + (table.y + 1) + " " + table.z + "（" + table.worldName + "）");
            }
        }

        void setBlockProtection(CommandSender sender, String id, boolean enabled) {
            Table table = tables.get(id);
            if (table == null) {
                sender.sendMessage(PREFIX + "找不到牌桌 #" + id + "。");
                return;
            }
            table.blockProtection = enabled;
            saveTables();
            sender.sendMessage(PREFIX + "牌桌 #" + id + " 的方塊保護已" + (enabled ? "開啟" : "關閉") + "。");
        }

        void join(Player player, String requestedId) {
            String playerId = player.getUniqueId().toString();
            if (playerTables.containsKey(playerId)) {
                player.sendMessage(PREFIX + "你已經在一張牌桌內。請先使用 /mahjong leave。");
                return;
            }
            Table table;
            if (requestedId == null) {
                table = nearestJoinableTable(player.getLocation());
            } else {
                table = tables.get(requestedId);
            }
            if (table == null) {
                player.sendMessage(PREFIX + "找不到可加入的牌桌。");
                return;
            }
            if (table.game != null) {
                player.sendMessage(PREFIX + "這張牌桌已經開始，無法中途加入。");
                return;
            }
            if (table.seats.size() >= 4) {
                player.sendMessage(PREFIX + "這張牌桌已滿。");
                return;
            }
            Seat seat = new Seat(playerId, player.getName(), false);
            table.seats.add(seat);
            playerTables.put(playerId, table.id);
            broadcast(table, PREFIX + player.getName() + " 加入牌桌，座位 " + table.seats.size() + "（" + seatWindName(table.seats.size() - 1) + "家）。");
            player.sendMessage(PREFIX + "再次右鍵桌中央可切換準備，或輸入 /mahjong ready。");
            renderTable(table);
        }

        void leave(Player player) {
            Table table = tableOf(player);
            if (table == null) {
                player.sendMessage(PREFIX + "你目前不在牌桌內。");
                return;
            }
            if (table.game != null && table.game.phase != Phase.GAME_END) {
                player.sendMessage(PREFIX + "牌局進行中不能離開；離線時會由電腦暫時代打。");
                return;
            }
            String id = player.getUniqueId().toString();
            table.seats.removeIf(s -> s.id.equals(id));
            playerTables.remove(id);
            broadcast(table, PREFIX + player.getName() + " 離開牌桌。");
            renderTable(table);
        }

        void ready(Player player, boolean ready) {
            Table table = requireLobby(player);
            if (table == null) return;
            Seat seat = seatOf(table, player.getUniqueId().toString());
            seat.ready = ready;
            broadcast(table, PREFIX + player.getName() + (ready ? " 已準備。" : " 已取消準備。"));
            renderTable(table);
            if (table.seats.size() == 4 && table.seats.stream().allMatch(s -> s.ready || s.bot)) {
                broadcast(table, PREFIX + "所有玩家已準備，3 秒後自動開始。");
                getServer().getScheduler().runTaskLater(TaiwanMahjongPlugin.this, () -> {
                    if (table.game == null && table.seats.size() == 4 && table.seats.stream().allMatch(s -> s.ready || s.bot)) {
                        startTable(table);
                    }
                }, 60L);
            }
        }

        void addBot(Player player) {
            if (!player.hasPermission("mahjongplay.command.bot")) {
                noPermission(player);
                return;
            }
            Table table = requireLobby(player);
            if (table == null) return;
            if (table.seats.size() >= 4) {
                player.sendMessage(PREFIX + "座位已滿。");
                return;
            }
            Seat bot = new Seat("bot-" + nextBotId, "電腦" + nextBotId++, true);
            bot.ready = true;
            table.seats.add(bot);
            broadcast(table, PREFIX + bot.name + " 加入牌桌，座位 " + table.seats.size() + "。");
            renderTable(table);
        }

        void kick(Player player, int seatIndex) {
            Table table = requireLobby(player);
            if (table == null) return;
            if (seatIndex < 0 || seatIndex >= table.seats.size()) {
                player.sendMessage(PREFIX + "該座位沒有玩家。");
                return;
            }
            Seat removed = table.seats.remove(seatIndex);
            playerTables.remove(removed.id);
            broadcast(table, PREFIX + removed.name + " 已被移出牌桌。");
            renderTable(table);
        }

        void start(Player player) {
            if (!player.hasPermission("mahjongplay.command.start")) {
                noPermission(player);
                return;
            }
            Table table = requireLobby(player);
            if (table == null) return;
            if (table.seats.isEmpty()) {
                player.sendMessage(PREFIX + "至少需要一位真人玩家。");
                return;
            }
            while (table.seats.size() < 4) {
                Seat bot = new Seat("bot-" + nextBotId, "電腦" + nextBotId++, true);
                bot.ready = true;
                table.seats.add(bot);
            }
            startTable(table);
        }

        void startTable(Table table) {
            if (table.game != null || table.seats.size() != 4) return;
            List<PlayerState> states = new ArrayList<>();
            for (Seat seat : table.seats) states.add(new PlayerState(seat.id, seat.name, seat.bot));
            GameState game = newGame(states, System.nanoTime());
            table.game = game;
            game.eventSink = message -> broadcast(table, PREFIX + message);
            broadcast(table, PREFIX + game.status);
            broadcast(table, PREFIX + "輪到莊家出牌。右鍵自己的 3D 手牌即可出牌。");
            afterStateChange(table);
        }

        void info(Player player) {
            Table table = tableOf(player);
            if (table == null) {
                player.sendMessage(PREFIX + "你目前不在牌桌內。");
                return;
            }
            player.sendMessage("§6========== 牌桌 #" + table.id + " ==========");
            for (int i = 0; i < table.seats.size(); i++) {
                Seat seat = table.seats.get(i);
                String extra = table.game == null ? (seat.ready ? "已準備" : "未準備") : table.game.player(i).score + " 分";
                player.sendMessage("§e" + (i + 1) + ". §f" + seat.name + " §7" + seatWindName(table.game == null ? i : table.game.seatDistance(table.game.dealerSeat, i)) + "家，" + extra + (seat.bot ? "，電腦" : ""));
            }
            if (table.game != null) {
                player.sendMessage("§7狀態：" + table.game.status + "；牌山 " + table.game.wallRemaining() + " 張。");
            }
        }

        void score(Player player) {
            Table table = tableOf(player);
            if (table == null || table.game == null) {
                player.sendMessage(PREFIX + "目前沒有進行中的牌局。");
                return;
            }
            player.sendMessage("§6========== 目前分數 ==========");
            for (int i = 0; i < 4; i++) {
                player.sendMessage("§e" + seatWindName(table.game.seatDistance(table.game.dealerSeat, i)) + " §f" + table.game.player(i).name + "：§b" + table.game.player(i).score);
            }
        }

        void showHandHint(Player player) {
            Table table = tableOf(player);
            if (table == null || table.game == null) {
                player.sendMessage(PREFIX + "牌局尚未開始。");
                return;
            }
            int seat = seatIndex(table, player.getUniqueId().toString());
            if (seat < 0) return;
            String hand = table.game.player(seat).hand.stream().map(t -> t.type().display + "(" + t.type().code + ")").toList().toString();
            player.sendMessage(PREFIX + "你的手牌：" + hand);
            player.sendMessage(PREFIX + "牌桌邊緣的正面 3D 牌只有你看得到，右鍵即可出牌。");
        }

        void rerender(Player player) {
            Table table = tableOf(player);
            if (table == null) {
                player.sendMessage(PREFIX + "你目前不在牌桌內。");
                return;
            }
            renderTable(table);
            player.sendMessage(PREFIX + "已重新生成牌桌顯示。");
        }

        void discardByCode(Player player, String code) {
            Table table = tableOf(player);
            if (table == null || table.game == null) {
                player.sendMessage(PREFIX + "目前沒有進行中的牌局。");
                return;
            }
            int seat = seatIndex(table, player.getUniqueId().toString());
            TileType type = TileType.fromCode(code);
            if (type == null) {
                player.sendMessage(PREFIX + "看不懂牌代碼「" + code + "」。例如 1m、9p、E、R、Wh。");
                return;
            }
            Tile tile = table.game.player(seat).hand.stream().filter(t -> t.type() == type).findFirst().orElse(null);
            if (tile == null) {
                player.sendMessage(PREFIX + "你的手牌中沒有「" + type.display + "」。");
                return;
            }
            discardById(player, tile.id());
        }

        void discardById(Player player, int tileId) {
            Table table = tableOf(player);
            if (table == null || table.game == null) return;
            int seat = seatIndex(table, player.getUniqueId().toString());
            if (!discard(table.game, seat, tileId)) {
                player.sendMessage(PREFIX + "現在不能打出這張牌；可能尚未輪到你。");
                return;
            }
            afterStateChange(table);
        }

        void action(Player player, String rawAction, String argument) {
            Table table = tableOf(player);
            if (table == null || table.game == null) {
                player.sendMessage(PREFIX + "目前沒有進行中的牌局。");
                return;
            }
            GameState game = table.game;
            int seat = seatIndex(table, player.getUniqueId().toString());
            String action = rawAction.toLowerCase(Locale.ROOT);

            if (game.phase == Phase.DISCARD && game.currentSeat == seat) {
                if (action.equals("hu") || action.equals("自摸") || action.equals("胡")) {
                    if (!selfWin(game, seat)) player.sendMessage(PREFIX + "目前牌型不能自摸。");
                    else afterStateChange(table);
                    return;
                }
                if (action.equals("kong") || action.equals("槓") || action.equals("杠")) {
                    TileType type = TileType.fromCode(argument);
                    if (type == null) {
                        List<TileType> options = concealedKongOptions(game, seat);
                        player.sendMessage(PREFIX + (options.isEmpty() ? "目前沒有可暗槓的牌。" : "可暗槓：" + options.stream().map(t -> t.display + "(" + t.code + ")").toList()));
                    } else if (!TaiwanMahjongEngine.concealedKong(game, seat, type)) {
                        player.sendMessage(PREFIX + "目前不能暗槓這張牌。");
                    } else {
                        afterStateChange(table);
                    }
                    return;
                }
            }

            if (game.phase != Phase.CLAIM || !game.eligibleClaimSeats.contains(seat)) {
                player.sendMessage(PREFIX + "目前沒有等待你回應的動作。");
                return;
            }
            Claim claim;
            switch (action) {
                case "hu", "胡", "ron" -> claim = Claim.of(ClaimKind.HU);
                case "pong", "pon", "碰" -> claim = Claim.of(ClaimKind.PONG);
                case "kong", "kan", "槓", "杠" -> claim = Claim.of(ClaimKind.KONG);
                case "pass", "過", "过" -> claim = Claim.pass();
                case "chi", "吃" -> {
                    int index = 0;
                    if (argument != null) {
                        try { index = Integer.parseInt(argument) - 1; }
                        catch (NumberFormatException ignored) { index = -1; }
                    }
                    claim = new Claim(ClaimKind.CHI, index);
                }
                default -> {
                    player.sendMessage(PREFIX + "未知動作。可用 hu、pong、kong、chi、pass。");
                    return;
                }
            }
            submitPlayerClaim(player, table, seat, claim);
        }

        void submitPlayerClaim(Player player, Table table, int seat, Claim claim) {
            if (!submitClaim(table.game, seat, claim)) {
                player.sendMessage(PREFIX + "這個動作目前不可用，或吃牌選項編號錯誤。");
                return;
            }
            player.sendMessage(PREFIX + "已選擇「" + claimName(claim.kind()) + "」，等待其他玩家。");
            if (allClaimsAnswered(table.game)) {
                resolveClaims(table.game);
                afterStateChange(table);
            } else {
                renderTable(table);
            }
        }

        void handleClick(Player player, ClickAction click) {
            Table table = tables.get(click.tableId());
            if (table == null) return;
            if (click.kind() == ActionKind.CENTER) {
                Table current = tableOf(player);
                if (current == null) join(player, table.id);
                else if (current != table) player.sendMessage(PREFIX + "你已在另一張牌桌內。");
                else if (table.game == null) {
                    Seat seat = seatOf(table, player.getUniqueId().toString());
                    ready(player, !seat.ready);
                } else info(player);
                return;
            }
            int actualSeat = seatIndex(table, player.getUniqueId().toString());
            if (actualSeat < 0 || actualSeat != click.seat()) return;
            if (table.game == null) return;
            switch (click.kind()) {
                case DISCARD -> discardById(player, click.tileId());
                case SELF_HU -> {
                    if (selfWin(table.game, actualSeat)) afterStateChange(table);
                    else player.sendMessage(PREFIX + "目前不能自摸。");
                }
                case CONCEALED_KONG -> {
                    if (TaiwanMahjongEngine.concealedKong(table.game, actualSeat, click.tileType())) afterStateChange(table);
                    else player.sendMessage(PREFIX + "目前不能暗槓。");
                }
                case CLAIM_HU -> submitPlayerClaim(player, table, actualSeat, Claim.of(ClaimKind.HU));
                case CLAIM_PONG -> submitPlayerClaim(player, table, actualSeat, Claim.of(ClaimKind.PONG));
                case CLAIM_KONG -> submitPlayerClaim(player, table, actualSeat, Claim.of(ClaimKind.KONG));
                case CLAIM_CHI -> submitPlayerClaim(player, table, actualSeat, new Claim(ClaimKind.CHI, click.option()));
                case CLAIM_PASS -> submitPlayerClaim(player, table, actualSeat, Claim.pass());
                default -> { }
            }
        }

        void tick() {
            long now = System.currentTimeMillis();
            for (Table table : new ArrayList<>(tables.values())) {
                GameState game = table.game;
                if (game == null) continue;
                if (game.phase == Phase.CLAIM) {
                    for (int seat : new ArrayList<>(game.eligibleClaimSeats)) {
                        if (!game.claims.containsKey(seat) && isComputerControlled(table, seat)) {
                            submitClaim(game, seat, chooseBotClaim(game, seat));
                        }
                    }
                    if (allClaimsAnswered(game) || now >= table.claimDeadline) {
                        resolveClaims(game);
                        afterStateChange(table);
                    }
                } else if (game.phase == Phase.DISCARD && now >= table.botActionAt && isComputerControlled(table, game.currentSeat)) {
                    int seat = game.currentSeat;
                    if (canSelfWin(game, seat)) {
                        selfWin(game, seat);
                    } else {
                        List<TileType> kongs = concealedKongOptions(game, seat);
                        if (!kongs.isEmpty() && game.random.nextDouble() < 0.15) {
                            TaiwanMahjongEngine.concealedKong(game, seat, kongs.get(0));
                        } else {
                            discard(game, seat, chooseBotDiscard(game, seat));
                        }
                    }
                    afterStateChange(table);
                } else if (game.phase == Phase.ROUND_END && now >= table.continueAt) {
                    continueAfterRound(game);
                    afterStateChange(table);
                } else if (game.phase == Phase.GAME_END && now >= table.resetAt) {
                    resetToLobby(table);
                }
            }
        }

        void afterStateChange(Table table) {
            if (table.game == null) {
                renderTable(table);
                return;
            }
            GameState game = table.game;
            long now = System.currentTimeMillis();
            if (game.phase == Phase.CLAIM) {
                table.claimDeadline = now + 12_000L;
                showClaimMessages(table);
            } else if (game.phase == Phase.DISCARD) {
                table.botActionAt = now + 1_300L;
                Player online = onlinePlayer(table, game.currentSeat);
                if (online != null) online.sendMessage(PREFIX + "輪到你出牌。右鍵桌邊自己的正面麻將牌。");
            } else if (game.phase == Phase.ROUND_END) {
                table.continueAt = now + 8_000L;
                broadcast(table, PREFIX + "8 秒後開始下一局。輸入 /mahjong score 查看分數。");
            } else if (game.phase == Phase.GAME_END) {
                table.resetAt = now + 15_000L;
                scoreToAll(table);
                broadcast(table, PREFIX + "15 秒後返回大廳。");
            }
            renderTable(table);
        }

        void showClaimMessages(Table table) {
            GameState game = table.game;
            for (int seat : game.eligibleClaimSeats) {
                if (isComputerControlled(table, seat)) continue;
                Player player = onlinePlayer(table, seat);
                if (player == null) continue;
                Set<ClaimKind> options = availableClaims(game, seat);
                player.sendMessage(PREFIX + "有人打出「" + game.lastDiscard.type().display + "」，請右鍵桌邊浮動按鈕選擇；12 秒後自動過牌。");
                if (options.contains(ClaimKind.CHI)) {
                    List<List<TileType>> choices = game.chiOptions.getOrDefault(seat, List.of());
                    for (int i = 0; i < choices.size(); i++) {
                        player.sendMessage("§e吃 " + (i + 1) + "：§f" + choices.get(i).stream().map(t -> t.display).toList());
                    }
                }
            }
        }

        boolean isComputerControlled(Table table, int seat) {
            Seat lobbySeat = table.seats.get(seat);
            if (lobbySeat.bot) return true;
            try { return Bukkit.getPlayer(UUID.fromString(lobbySeat.id)) == null; }
            catch (IllegalArgumentException ex) { return true; }
        }

        Player onlinePlayer(Table table, int seatIndex) {
            Seat seat = table.seats.get(seatIndex);
            if (seat.bot) return null;
            try { return Bukkit.getPlayer(UUID.fromString(seat.id)); }
            catch (IllegalArgumentException ex) { return null; }
        }

        void resetToLobby(Table table) {
            table.game = null;
            table.seats.removeIf(s -> s.bot);
            for (Seat seat : table.seats) seat.ready = false;
            broadcast(table, PREFIX + "已返回牌桌大廳，請重新準備。");
            renderTable(table);
        }

        void scoreToAll(Table table) {
            if (table.game == null) return;
            broadcast(table, "§6========== 最終分數 ==========");
            table.game.players.stream().sorted(Comparator.comparingInt((PlayerState p) -> p.score).reversed())
                    .forEach(p -> broadcast(table, "§f" + p.name + "：§b" + p.score + " 分"));
        }

        void renderTable(Table table) {
            World world = table.world();
            if (world == null) return;
            clearEntities(table);
            if (table.blockProtection) buildPhysicalTable(table);

            Location center = table.center();
            String statusText = buildStatusText(table);
            spawnText(table, center.clone().add(0, 2.0, 0), statusText, null, null);
            Interaction centerHitbox = spawnInteraction(table, center.clone().add(0, 0.20, 0), 1.05f, 0.42f, null);
            clickActions.put(centerHitbox.getUniqueId(), ClickAction.center(table.id));

            if (table.game == null) {
                renderLobbySeats(table);
                return;
            }

            renderDecorativeWall(table);
            for (int seat = 0; seat < 4; seat++) {
                renderHand(table, seat);
                renderMeldsAndFlowers(table, seat);
                renderDiscards(table, seat);
            }
            renderActionButtons(table);
        }

        String buildStatusText(Table table) {
            StringBuilder text = new StringBuilder();
            text.append("§6§l台灣麻將 #").append(table.id).append("\n§f").append(table.name).append("\n");
            if (table.game == null) {
                text.append("§a右鍵中央加入／準備\n");
                for (int i = 0; i < 4; i++) {
                    if (i < table.seats.size()) {
                        Seat seat = table.seats.get(i);
                        text.append(i == 0 ? "" : "  ").append(seatWindName(i)).append(" ").append(seat.name)
                                .append(seat.ready || seat.bot ? " §a✓" : " §c✗");
                    } else {
                        text.append(i == 0 ? "" : "  ").append(seatWindName(i)).append(" §8空位");
                    }
                }
            } else {
                GameState game = table.game;
                text.append("§f").append(game.status).append("\n§7牌山：").append(game.wallRemaining()).append(" 張");
                if (game.phase == Phase.DISCARD) text.append("  §e輪到：").append(game.player(game.currentSeat).name);
                else if (game.phase == Phase.CLAIM) text.append("  §e等待吃碰槓胡");
            }
            return text.toString();
        }

        void renderLobbySeats(Table table) {
            for (int seat = 0; seat < 4; seat++) {
                String label;
                if (seat < table.seats.size()) {
                    Seat s = table.seats.get(seat);
                    label = "§e" + seatWindName(seat) + "\n§f" + s.name + "\n" + (s.ready || s.bot ? "§a已準備" : "§c未準備");
                } else {
                    label = "§7" + seatWindName(seat) + "\n空位";
                }
                spawnText(table, seatPoint(table, seat, 1.78, 0.75), label, null, null);
            }
        }

        void renderDecorativeWall(Table table) {
            int segments = Math.min(16, Math.max(0, (int) Math.ceil(table.game.wallRemaining() / 9.0)));
            for (int i = 0; i < segments; i++) {
                int side = i / 4;
                int pos = i % 4;
                double t = -0.48 + pos * 0.32;
                Location loc;
                float yaw;
                if (side == 0) { loc = table.center().clone().add(t, 0.09, 0.78); yaw = 180f; }
                else if (side == 1) { loc = table.center().clone().add(-0.78, 0.09, -t); yaw = 90f; }
                else if (side == 2) { loc = table.center().clone().add(-t, 0.09, -0.78); yaw = 0f; }
                else { loc = table.center().clone().add(0.78, 0.09, t); yaw = -90f; }
                spawnTileDisplay(table, loc, 1, yaw, -90f, 0.13f, true, null, false);
            }
        }

        void renderHand(Table table, int seat) {
            PlayerState state = table.game.player(seat);
            Player owner = onlinePlayer(table, seat);
            int count = state.hand.size();
            for (int i = 0; i < count; i++) {
                Tile tile = state.hand.get(i);
                Location loc = handTilePoint(table, seat, i, count);
                float yaw = seatYaw(seat);
                ItemDisplay back = spawnTileDisplay(table, loc, 1, yaw, 0f, TILE_SCALE, true, owner, false);
                if (owner != null) owner.hideEntity(TaiwanMahjongPlugin.this, back);
                if (owner != null) {
                    spawnTileDisplay(table, loc, tileModel(tile.type()), handFaceYaw(seat), 0f, TILE_SCALE, false, owner, true);
                    Interaction hitbox = spawnInteraction(table, interactionPointForHand(loc), 0.125f, 0.23f, owner);
                    clickActions.put(hitbox.getUniqueId(), ClickAction.discard(table.id, seat, tile.id()));
                }
            }
        }

        void renderMeldsAndFlowers(Table table, int seat) {
            PlayerState state = table.game.player(seat);
            List<TileType> exposed = new ArrayList<>();
            for (Meld meld : state.melds) exposed.addAll(meld.tiles());
            for (Tile flower : state.flowers) exposed.add(flower.type());
            int count = exposed.size();
            for (int i = 0; i < count; i++) {
                Location base = handTilePoint(table, seat, i, Math.max(count, 1));
                Location loc = moveTowardCenter(base, table.center(), 0.35).add(0, -0.01, 0);
                spawnTileDisplay(table, loc, tileModel(exposed.get(i)), seatYaw(seat), -90f, 0.12f, true, null, false);
            }
        }

        void renderDiscards(Table table, int seat) {
            List<Tile> river = table.game.player(seat).discards;
            for (int i = 0; i < river.size(); i++) {
                int column = i % 6;
                int row = i / 6;
                Location loc = discardPoint(table, seat, column, row);
                spawnTileDisplay(table, loc, tileModel(river.get(i).type()), seatYaw(seat), -90f, 0.13f, true, null, false);
            }
        }

        void renderActionButtons(Table table) {
            GameState game = table.game;
            if (game.phase == Phase.DISCARD) {
                int seat = game.currentSeat;
                Player player = onlinePlayer(table, seat);
                if (player == null) return;
                List<ButtonSpec> buttons = new ArrayList<>();
                if (canSelfWin(game, seat)) buttons.add(new ButtonSpec("§c§l自摸", ClickAction.simple(table.id, ActionKind.SELF_HU, seat)));
                for (TileType type : concealedKongOptions(game, seat)) {
                    buttons.add(new ButtonSpec("§b暗槓\n" + type.display, ClickAction.kong(table.id, seat, type)));
                }
                spawnButtons(table, player, seat, buttons);
            } else if (game.phase == Phase.CLAIM) {
                for (int seat : game.eligibleClaimSeats) {
                    if (game.claims.containsKey(seat)) continue;
                    Player player = onlinePlayer(table, seat);
                    if (player == null) continue;
                    Set<ClaimKind> options = availableClaims(game, seat);
                    List<ButtonSpec> buttons = new ArrayList<>();
                    if (options.contains(ClaimKind.HU)) buttons.add(new ButtonSpec("§c§l胡", ClickAction.simple(table.id, ActionKind.CLAIM_HU, seat)));
                    if (options.contains(ClaimKind.PONG)) buttons.add(new ButtonSpec("§e§l碰", ClickAction.simple(table.id, ActionKind.CLAIM_PONG, seat)));
                    if (options.contains(ClaimKind.KONG)) buttons.add(new ButtonSpec("§b§l槓", ClickAction.simple(table.id, ActionKind.CLAIM_KONG, seat)));
                    if (options.contains(ClaimKind.CHI)) {
                        List<List<TileType>> choices = game.chiOptions.getOrDefault(seat, List.of());
                        for (int i = 0; i < choices.size(); i++) {
                            String tiles = choices.get(i).stream().map(t -> t.display.substring(0, 1)).reduce("", String::concat);
                            buttons.add(new ButtonSpec("§a吃" + (i + 1) + "\n" + tiles, ClickAction.chi(table.id, seat, i)));
                        }
                    }
                    buttons.add(new ButtonSpec("§7過", ClickAction.simple(table.id, ActionKind.CLAIM_PASS, seat)));
                    spawnButtons(table, player, seat, buttons);
                }
            }
        }

        void spawnButtons(Table table, Player player, int seat, List<ButtonSpec> buttons) {
            if (buttons.isEmpty()) return;
            for (int i = 0; i < buttons.size(); i++) {
                Location loc = buttonPoint(table, seat, i, buttons.size());
                ButtonSpec spec = buttons.get(i);
                spawnText(table, loc.clone().add(0, 0.18, 0), spec.label(), player, null);
                Interaction hitbox = spawnInteraction(table, loc, 0.48f, 0.42f, player);
                clickActions.put(hitbox.getUniqueId(), spec.action());
            }
        }

        record ButtonSpec(String label, ClickAction action) { }

        ItemDisplay spawnTileDisplay(Table table, Location location, int modelData, float yaw, float pitch,
                                     float scale, boolean visibleByDefault, Player privateViewer, boolean privateOnly) {
            ItemDisplay display = (ItemDisplay) table.world().spawnEntity(location, EntityType.ITEM_DISPLAY);
            display.setPersistent(false);
            display.setViewRange(1.25f);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setItemStack(tileItem(modelData));
            display.setVisibleByDefault(visibleByDefault);
            applyTransform(display, yaw, pitch, scale);
            table.entities.add(display);
            if (privateOnly && privateViewer != null) privateViewer.showEntity(TaiwanMahjongPlugin.this, display);
            return display;
        }

        TextDisplay spawnText(Table table, Location location, String text, Player privateViewer, ClickAction ignored) {
            TextDisplay display = (TextDisplay) table.world().spawnEntity(location, EntityType.TEXT_DISPLAY);
            display.setPersistent(false);
            display.setViewRange(1.5f);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setText(text);
            if (privateViewer != null) {
                display.setVisibleByDefault(false);
                privateViewer.showEntity(TaiwanMahjongPlugin.this, display);
            }
            table.entities.add(display);
            return display;
        }

        Interaction spawnInteraction(Table table, Location location, float width, float height, Player privateViewer) {
            Interaction interaction = (Interaction) table.world().spawnEntity(location, EntityType.INTERACTION);
            interaction.setPersistent(false);
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setResponsive(true);
            if (privateViewer != null) {
                interaction.setVisibleByDefault(false);
                privateViewer.showEntity(TaiwanMahjongPlugin.this, interaction);
            }
            table.entities.add(interaction);
            return interaction;
        }

        void clearEntities(Table table) {
            for (Entity entity : new ArrayList<>(table.entities)) {
                clickActions.remove(entity.getUniqueId());
                try { entity.remove(); }
                catch (Exception ignored) { }
            }
            table.entities.clear();
        }

        void buildPhysicalTable(Table table) {
            World world = table.world();
            if (world == null) return;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(table.x + dx, table.y, table.z + dz).setType(Material.OAK_FENCE, false);
                    Material carpet = (dx == 0 && dz == 0) ? Material.LIGHT_BLUE_CARPET : Material.GREEN_CARPET;
                    world.getBlockAt(table.x + dx, table.y + 1, table.z + dz).setType(carpet, false);
                }
            }
            removeLegacySeatSlabs(table);
        }

        void removePhysicalTable(Table table) {
            World world = table.world();
            if (world == null) return;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(table.x + dx, table.y, table.z + dz).setType(Material.AIR, false);
                    world.getBlockAt(table.x + dx, table.y + 1, table.z + dz).setType(Material.AIR, false);
                }
            }
            removeLegacySeatSlabs(table);
        }

        void removeLegacySeatSlabs(Table table) {
            World world = table.world();
            if (world == null) return;
            int[][] seats = {{0, 2}, {-2, 0}, {0, -2}, {2, 0}};
            for (int[] offset : seats) {
                Block block = world.getBlockAt(table.x + offset[0], table.y, table.z + offset[1]);
                if (block.getType() == Material.OAK_SLAB) block.setType(Material.AIR, false);
            }
        }

        boolean isTableBlock(Table table, Block block) {
            if (block.getWorld() == null || !block.getWorld().getName().equals(table.worldName)) return false;
            int bx = block.getX();
            int by = block.getY();
            int bz = block.getZ();
            if (Math.abs(bx - table.x) <= 1 && Math.abs(bz - table.z) <= 1 && (by == table.y || by == table.y + 1)) return true;
            if (by != table.y) return false;
            return (bx == table.x && Math.abs(bz - table.z) == 2) || (bz == table.z && Math.abs(bx - table.x) == 2);
        }

        Table requireLobby(Player player) {
            Table table = tableOf(player);
            if (table == null) {
                player.sendMessage(PREFIX + "你目前不在牌桌內。");
                return null;
            }
            if (table.game != null) {
                player.sendMessage(PREFIX + "牌局已開始，現在不能變更大廳設定。");
                return null;
            }
            return table;
        }

        Table tableOf(Player player) {
            String tableId = playerTables.get(player.getUniqueId().toString());
            return tableId == null ? null : tables.get(tableId);
        }

        Seat seatOf(Table table, String id) {
            return table.seats.stream().filter(s -> s.id.equals(id)).findFirst().orElseThrow();
        }

        int seatIndex(Table table, String id) {
            for (int i = 0; i < table.seats.size(); i++) if (table.seats.get(i).id.equals(id)) return i;
            return -1;
        }

        Table nearestJoinableTable(Location location) {
            Table best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Table table : tables.values()) {
                if (table.game != null || table.seats.size() >= 4 || table.world() != location.getWorld()) continue;
                double d = table.center().distanceSquared(location);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = table;
                }
            }
            return best;
        }

        void broadcast(Table table, String message) {
            for (Seat seat : table.seats) {
                if (seat.bot) continue;
                try {
                    Player player = Bukkit.getPlayer(UUID.fromString(seat.id));
                    if (player != null) player.sendMessage(message);
                } catch (IllegalArgumentException ignored) { }
            }
        }

        void broadcastAll(String message) {
            for (Table table : tables.values()) broadcast(table, message);
        }

        void loadTables() {
            File file = new File(getDataFolder(), "tables.tsv");
            if (!file.isFile()) return;
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\t");
                    if (parts.length < 6) continue;
                    String id = parts[0];
                    String name = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    Table table = new Table(id, name, parts[2], Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
                    if (parts.length >= 7) table.blockProtection = Boolean.parseBoolean(parts[6]);
                    tables.put(id, table);
                    try { nextTableId = Math.max(nextTableId, Integer.parseInt(id) + 1); }
                    catch (NumberFormatException ignored) { }
                    getServer().getScheduler().runTaskLater(TaiwanMahjongPlugin.this, () -> renderTable(table), 1L);
                }
            } catch (Exception ex) {
                getLogger().log(Level.WARNING, "無法讀取牌桌資料", ex);
            }
        }

        void saveTables() {
            try {
                File folder = getDataFolder();
                if (!folder.exists() && !folder.mkdirs()) return;
                List<String> lines = new ArrayList<>();
                lines.add("# id\\tbase64_name\\tworld\\tx\\ty\\tz\\tblock_protection");
                for (Table table : tables.values()) {
                    String name = Base64.getUrlEncoder().withoutPadding().encodeToString(table.name.getBytes(StandardCharsets.UTF_8));
                    lines.add(table.id + "\t" + name + "\t" + table.worldName + "\t" + table.x + "\t" + table.y + "\t" + table.z + "\t" + table.blockProtection);
                }
                Files.write(new File(folder, "tables.tsv").toPath(), lines, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                getLogger().log(Level.WARNING, "無法儲存牌桌資料", ex);
            }
        }
    }

    private static ItemStack tileItem(int modelData) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) modelData));
            meta.setCustomModelDataComponent(component);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static int tileModel(TileType type) {
        return type.ordinal() + 2;
    }

    /** Uses reflection so the project can be compiled without bundling JOML; Paper provides JOML at runtime. */
    private void applyTransform(ItemDisplay display, float yawDegrees, float pitchDegrees, float scale) {
        try {
            Class<?> matrixClass = Class.forName("org.joml.Matrix4f");
            Object matrix = matrixClass.getConstructor().newInstance();
            Method rotateY = matrixClass.getMethod("rotateY", float.class);
            Method rotateX = matrixClass.getMethod("rotateX", float.class);
            Method scaleMethod = matrixClass.getMethod("scale", float.class);
            rotateY.invoke(matrix, (float) Math.toRadians(yawDegrees));
            rotateX.invoke(matrix, (float) Math.toRadians(pitchDegrees));
            scaleMethod.invoke(matrix, scale);
            Method setter = display.getClass().getMethod("setTransformationMatrix", matrixClass);
            setter.invoke(display, matrix);
        } catch (ReflectiveOperationException ex) {
            getLogger().log(Level.WARNING, "無法套用 3D 麻將牌變形；牌仍會生成，但方向或大小可能不正確。", ex);
        }
    }

    private static Location handTilePoint(Table table, int seat, int index, int count) {
        double total = (count - 1) * TILE_SPACING;
        double offset = index * TILE_SPACING - total / 2.0;
        Location center = table.center();
        return switch (seat) {
            case 0 -> center.clone().add(offset, 0.12, 1.40);
            case 1 -> center.clone().add(-1.40, 0.12, -offset);
            case 2 -> center.clone().add(-offset, 0.12, -1.40);
            default -> center.clone().add(1.40, 0.12, offset);
        };
    }

    private static Location interactionPointForHand(Location displayLocation) {
        return displayLocation.clone().add(0, -0.11, 0);
    }

    private static Location discardPoint(Table table, int seat, int column, int row) {
        double along = (column - 2.5) * 0.18;
        double inward = row * 0.20;
        Location c = table.center();
        return switch (seat) {
            case 0 -> c.clone().add(along, 0.08, 0.62 - inward);
            case 1 -> c.clone().add(-0.62 + inward, 0.08, -along);
            case 2 -> c.clone().add(-along, 0.08, -0.62 + inward);
            default -> c.clone().add(0.62 - inward, 0.08, along);
        };
    }

    private static Location seatPoint(Table table, int seat, double radius, double height) {
        Location c = table.center();
        return switch (seat) {
            case 0 -> c.clone().add(0, height, radius);
            case 1 -> c.clone().add(-radius, height, 0);
            case 2 -> c.clone().add(0, height, -radius);
            default -> c.clone().add(radius, height, 0);
        };
    }

    private static Location buttonPoint(Table table, int seat, int index, int count) {
        double spacing = 0.55;
        double offset = (index - (count - 1) / 2.0) * spacing;
        Location c = table.center();
        return switch (seat) {
            case 0 -> c.clone().add(offset, 0.52, 1.72);
            case 1 -> c.clone().add(-1.72, 0.52, -offset);
            case 2 -> c.clone().add(-offset, 0.52, -1.72);
            default -> c.clone().add(1.72, 0.52, offset);
        };
    }

    private static Location moveTowardCenter(Location point, Location center, double distance) {
        double dx = center.getX() - point.getX();
        double dz = center.getZ() - point.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.0001) return point;
        return point.add(dx / length * distance, 0, dz / length * distance);
    }

    private static float seatYaw(int seat) {
        return switch (seat) {
            case 0 -> 180f;
            case 1 -> 90f;
            case 2 -> 0f;
            default -> -90f;
        };
    }

    private static float handFaceYaw(int seat) {
        float yaw = seatYaw(seat) + 180f;
        return yaw > 180f ? yaw - 360f : yaw;
    }

    private static int[] cardinalForward(float yaw) {
        int direction = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (direction) {
            case 0 -> new int[]{0, 1};
            case 1 -> new int[]{-1, 0};
            case 2 -> new int[]{0, -1};
            default -> new int[]{1, 0};
        };
    }

    private static long distanceSquared(int ax, int ay, int az, int bx, int by, int bz) {
        long dx = ax - bx;
        long dy = ay - by;
        long dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static String cleanName(String value) {
        String cleaned = value == null ? "台灣麻將桌" : value.replaceAll("[\\r\\n\\t]", " ").trim();
        if (cleaned.isEmpty()) cleaned = "台灣麻將桌";
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private static String seatWindName(int relativeSeat) {
        return switch (Math.floorMod(relativeSeat, 4)) {
            case 0 -> "東";
            case 1 -> "南";
            case 2 -> "西";
            default -> "北";
        };
    }

    private static String claimName(ClaimKind kind) {
        return switch (kind) {
            case HU -> "胡";
            case PONG -> "碰";
            case KONG -> "槓";
            case CHI -> "吃";
            case PASS -> "過";
        };
    }
}
