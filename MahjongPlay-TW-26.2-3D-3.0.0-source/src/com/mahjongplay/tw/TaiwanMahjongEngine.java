package com.mahjongplay.tw;

import java.util.*;
import java.util.function.Consumer;

/**
 * Pure Java Taiwanese 16-tile mahjong engine.
 *
 * Rules preset:
 * - 4 players, 144 tiles including 8 flowers.
 * - Dealer starts with 17 tiles; others 16.
 * - Flowers are exposed and replaced from the back of the wall.
 * - Standard winning shape: five melds plus one pair (17 effective tiles).
 * - Claim priority: win > kong/pong > chi.
 */
public final class TaiwanMahjongEngine {
    private TaiwanMahjongEngine() {}

    public enum Suit { CHARACTERS, DOTS, BAMBOOS, HONOR, FLOWER }

    public enum TileType {
        M1(Suit.CHARACTERS, 1, "一萬", "1m"), M2(Suit.CHARACTERS, 2, "二萬", "2m"),
        M3(Suit.CHARACTERS, 3, "三萬", "3m"), M4(Suit.CHARACTERS, 4, "四萬", "4m"),
        M5(Suit.CHARACTERS, 5, "五萬", "5m"), M6(Suit.CHARACTERS, 6, "六萬", "6m"),
        M7(Suit.CHARACTERS, 7, "七萬", "7m"), M8(Suit.CHARACTERS, 8, "八萬", "8m"),
        M9(Suit.CHARACTERS, 9, "九萬", "9m"),
        P1(Suit.DOTS, 1, "一筒", "1p"), P2(Suit.DOTS, 2, "二筒", "2p"),
        P3(Suit.DOTS, 3, "三筒", "3p"), P4(Suit.DOTS, 4, "四筒", "4p"),
        P5(Suit.DOTS, 5, "五筒", "5p"), P6(Suit.DOTS, 6, "六筒", "6p"),
        P7(Suit.DOTS, 7, "七筒", "7p"), P8(Suit.DOTS, 8, "八筒", "8p"),
        P9(Suit.DOTS, 9, "九筒", "9p"),
        S1(Suit.BAMBOOS, 1, "一索", "1s"), S2(Suit.BAMBOOS, 2, "二索", "2s"),
        S3(Suit.BAMBOOS, 3, "三索", "3s"), S4(Suit.BAMBOOS, 4, "四索", "4s"),
        S5(Suit.BAMBOOS, 5, "五索", "5s"), S6(Suit.BAMBOOS, 6, "六索", "6s"),
        S7(Suit.BAMBOOS, 7, "七索", "7s"), S8(Suit.BAMBOOS, 8, "八索", "8s"),
        S9(Suit.BAMBOOS, 9, "九索", "9s"),
        EAST(Suit.HONOR, 1, "東風", "E"), SOUTH(Suit.HONOR, 2, "南風", "S"),
        WEST(Suit.HONOR, 3, "西風", "W"), NORTH(Suit.HONOR, 4, "北風", "N"),
        RED(Suit.HONOR, 5, "紅中", "R"), GREEN(Suit.HONOR, 6, "發財", "G"),
        WHITE(Suit.HONOR, 7, "白板", "Wh"),
        SPRING(Suit.FLOWER, 1, "春", "F1"), SUMMER(Suit.FLOWER, 2, "夏", "F2"),
        AUTUMN(Suit.FLOWER, 3, "秋", "F3"), WINTER(Suit.FLOWER, 4, "冬", "F4"),
        PLUM(Suit.FLOWER, 5, "梅", "F5"), ORCHID(Suit.FLOWER, 6, "蘭", "F6"),
        BAMBOO_FLOWER(Suit.FLOWER, 7, "竹", "F7"), CHRYSANTHEMUM(Suit.FLOWER, 8, "菊", "F8");

        public final Suit suit;
        public final int rank;
        public final String display;
        public final String code;

        TileType(Suit suit, int rank, String display, String code) {
            this.suit = suit;
            this.rank = rank;
            this.display = display;
            this.code = code;
        }

        public boolean isFlower() { return suit == Suit.FLOWER; }
        public boolean isHonor() { return suit == Suit.HONOR; }
        public boolean isSuited() { return suit == Suit.CHARACTERS || suit == Suit.DOTS || suit == Suit.BAMBOOS; }
        public boolean isTerminal() { return isSuited() && (rank == 1 || rank == 9); }
        public boolean isDragon() { return this == RED || this == GREEN || this == WHITE; }
        public boolean isWind() { return this == EAST || this == SOUTH || this == WEST || this == NORTH; }

        public static TileType fromCode(String value) {
            if (value == null) return null;
            String v = value.trim();
            for (TileType type : values()) {
                if (type.code.equalsIgnoreCase(v) || type.display.equals(v)) return type;
            }
            return switch (v.toLowerCase(Locale.ROOT)) {
                case "東", "east" -> EAST;
                case "南", "south" -> SOUTH;
                case "西", "west" -> WEST;
                case "北", "north" -> NORTH;
                case "中", "red" -> RED;
                case "發", "发", "green" -> GREEN;
                case "白", "white" -> WHITE;
                default -> null;
            };
        }
    }

    public record Tile(int id, TileType type) {
        @Override public String toString() { return type.display; }
    }

    public enum MeldKind { CHI, PONG, KONG, CONCEALED_KONG }

    public record Meld(MeldKind kind, List<TileType> tiles, int fromSeat) {
        public Meld {
            tiles = List.copyOf(tiles);
        }
        public boolean isOpen() { return kind != MeldKind.CONCEALED_KONG; }
        public TileType representative() { return tiles.get(0); }
    }

    public enum Phase { LOBBY, DISCARD, CLAIM, ROUND_END, GAME_END }
    public enum ClaimKind { PASS, CHI, PONG, KONG, HU }

    public record Claim(ClaimKind kind, int chiIndex) {
        public static Claim pass() { return new Claim(ClaimKind.PASS, -1); }
        public static Claim of(ClaimKind kind) { return new Claim(kind, -1); }
    }

    public static final class PlayerState {
        public final String id;
        public final String name;
        public final boolean bot;
        public final List<Tile> hand = new ArrayList<>();
        public final List<Tile> flowers = new ArrayList<>();
        public final List<Meld> melds = new ArrayList<>();
        public final List<Tile> discards = new ArrayList<>();
        public int score = 1000;

        public PlayerState(String id, String name, boolean bot) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.bot = bot;
        }

        public int effectiveTileCount() {
            return hand.size() + melds.size() * 3;
        }

        public boolean isConcealed() {
            return melds.stream().noneMatch(Meld::isOpen);
        }
    }

    public static final class GameState {
        public final List<PlayerState> players;
        public final Deque<Tile> wall = new ArrayDeque<>();
        public final List<Tile> allTiles = new ArrayList<>();
        public final Map<Integer, Claim> claims = new HashMap<>();
        public final Set<Integer> eligibleClaimSeats = new HashSet<>();
        public final Map<Integer, List<List<TileType>>> chiOptions = new HashMap<>();
        public final Random random;

        public Phase phase = Phase.LOBBY;
        public int dealerSeat = 0;
        public int currentSeat = 0;
        public int roundWind = 0; // 0 = East circle
        public int dealerStreak = 0;
        public int dealerChanges = 0;
        public int handNumber = 1;
        public Tile lastDiscard;
        public int lastDiscardSeat = -1;
        public boolean lastDrawWasBack = false;
        public Tile lastDrawnTile;
        public boolean maySelfWin = false;
        public boolean firstTurnCycle = true;
        public boolean pendingRobKong = false;
        public String status = "等待開始";

        public Consumer<String> eventSink = ignored -> {};

        public GameState(List<PlayerState> players, long seed) {
            if (players.size() != 4) throw new IllegalArgumentException("Taiwan mahjong needs exactly 4 players");
            this.players = List.copyOf(players);
            this.random = new Random(seed);
        }

        public PlayerState player(int seat) { return players.get(Math.floorMod(seat, 4)); }
        public int nextSeat(int seat) { return (seat + 1) % 4; }
        public int seatDistance(int from, int to) { return Math.floorMod(to - from, 4); }
        public int wallRemaining() { return wall.size(); }
        public Tile peekLastDiscard() { return lastDiscard; }

        public void emit(String message) {
            status = message;
            eventSink.accept(message);
        }
    }

    public static GameState newGame(List<PlayerState> players, long seed) {
        GameState game = new GameState(players, seed);
        for (PlayerState player : players) player.score = 1000;
        startRound(game);
        return game;
    }

    public static void startRound(GameState game) {
        game.phase = Phase.DISCARD;
        game.claims.clear();
        game.eligibleClaimSeats.clear();
        game.chiOptions.clear();
        game.lastDiscard = null;
        game.lastDiscardSeat = -1;
        game.lastDrawWasBack = false;
        game.lastDrawnTile = null;
        game.maySelfWin = false;
        game.firstTurnCycle = true;
        game.pendingRobKong = false;
        game.wall.clear();
        game.allTiles.clear();

        for (PlayerState player : game.players) {
            player.hand.clear();
            player.flowers.clear();
            player.melds.clear();
            player.discards.clear();
        }

        int id = 1;
        for (TileType type : TileType.values()) {
            int copies = type.isFlower() ? 1 : 4;
            for (int i = 0; i < copies; i++) game.allTiles.add(new Tile(id++, type));
        }
        Collections.shuffle(game.allTiles, game.random);
        game.wall.addAll(game.allTiles);

        // Deal one tile at a time and replace flowers immediately from the back.
        for (int n = 0; n < 16; n++) {
            for (int seat = 0; seat < 4; seat++) drawForPlayer(game, seat, false);
        }
        drawForPlayer(game, game.dealerSeat, false);
        // 配牌期間可能因補花從牌尾取牌，但不算槓上開花。
        game.lastDrawWasBack = false;
        game.maySelfWin = true;
        game.currentSeat = game.dealerSeat;
        sortAllHands(game);
        game.emit("第 " + game.handNumber + " 局開始，" + game.player(game.dealerSeat).name + " 坐莊");
    }

    public static Tile drawForPlayer(GameState game, int seat, boolean fromBack) {
        PlayerState player = game.player(seat);
        Tile drawn = pollWall(game, fromBack);
        while (drawn != null && drawn.type.isFlower()) {
            player.flowers.add(drawn);
            game.emit(player.name + " 補到花牌「" + drawn.type.display + "」");
            drawn = pollWall(game, true);
            fromBack = true;
        }
        if (drawn != null) {
            player.hand.add(drawn);
            sortHand(player.hand);
            game.lastDrawnTile = drawn;
            game.lastDrawWasBack = fromBack;
        }
        return drawn;
    }

    private static Tile pollWall(GameState game, boolean fromBack) {
        return fromBack ? game.wall.pollLast() : game.wall.pollFirst();
    }

    public static void beginTurnWithDraw(GameState game, int seat) {
        if (game.phase == Phase.GAME_END) return;
        game.currentSeat = seat;
        if (game.wall.isEmpty()) {
            exhaustiveDraw(game);
            return;
        }
        Tile tile = drawForPlayer(game, seat, false);
        if (tile == null) {
            exhaustiveDraw(game);
            return;
        }
        game.lastDrawWasBack = false;
        game.maySelfWin = true;
        game.phase = Phase.DISCARD;
        game.emit(game.player(seat).name + " 摸牌，牌山剩餘 " + game.wallRemaining() + " 張");
    }

    public static boolean discard(GameState game, int seat, int tileId) {
        if (game.phase != Phase.DISCARD || seat != game.currentSeat) return false;
        PlayerState player = game.player(seat);
        Tile tile = player.hand.stream().filter(t -> t.id == tileId).findFirst().orElse(null);
        if (tile == null) return false;
        if (player.effectiveTileCount() != 17) return false;

        player.hand.remove(tile);
        game.maySelfWin = false;
        game.lastDrawnTile = null;
        player.discards.add(tile);
        game.lastDiscard = tile;
        game.lastDiscardSeat = seat;
        game.claims.clear();
        game.eligibleClaimSeats.clear();
        game.chiOptions.clear();
        game.emit(player.name + " 打出「" + tile.type.display + "」");
        prepareClaims(game);
        return true;
    }

    private static void prepareClaims(GameState game) {
        Tile discarded = game.lastDiscard;
        if (discarded == null) return;
        int discarder = game.lastDiscardSeat;

        for (int seat = 0; seat < 4; seat++) {
            if (seat == discarder) continue;
            PlayerState player = game.player(seat);
            boolean eligible = false;
            List<Tile> testHand = new ArrayList<>(player.hand);
            testHand.add(discarded);
            if (isWinning(testHand, player.melds)) eligible = true;
            long same = player.hand.stream().filter(t -> t.type == discarded.type).count();
            if (same >= 2) eligible = true;
            if (same >= 3) eligible = true;
            if (seat == game.nextSeat(discarder)) {
                List<List<TileType>> options = chiOptions(player.hand, discarded.type);
                if (!options.isEmpty()) {
                    game.chiOptions.put(seat, options);
                    eligible = true;
                }
            }
            if (eligible) game.eligibleClaimSeats.add(seat);
        }

        if (game.eligibleClaimSeats.isEmpty()) {
            game.firstTurnCycle = false;
            beginTurnWithDraw(game, game.nextSeat(discarder));
        } else {
            game.phase = Phase.CLAIM;
            game.emit("等待其他玩家回應：胡／碰／槓／吃／過");
        }
    }

    public static Set<ClaimKind> availableClaims(GameState game, int seat) {
        if (game.phase != Phase.CLAIM || !game.eligibleClaimSeats.contains(seat) || game.lastDiscard == null) {
            return EnumSet.noneOf(ClaimKind.class);
        }
        PlayerState player = game.player(seat);
        EnumSet<ClaimKind> result = EnumSet.of(ClaimKind.PASS);
        List<Tile> testHand = new ArrayList<>(player.hand);
        testHand.add(game.lastDiscard);
        if (isWinning(testHand, player.melds)) result.add(ClaimKind.HU);
        long same = player.hand.stream().filter(t -> t.type == game.lastDiscard.type).count();
        if (same >= 2) result.add(ClaimKind.PONG);
        if (same >= 3) result.add(ClaimKind.KONG);
        if (game.chiOptions.containsKey(seat)) result.add(ClaimKind.CHI);
        return result;
    }

    public static boolean submitClaim(GameState game, int seat, Claim claim) {
        if (game.phase != Phase.CLAIM || !game.eligibleClaimSeats.contains(seat)) return false;
        Set<ClaimKind> available = availableClaims(game, seat);
        if (!available.contains(claim.kind)) return false;
        if (claim.kind == ClaimKind.CHI) {
            List<List<TileType>> options = game.chiOptions.getOrDefault(seat, List.of());
            if (claim.chiIndex < 0 || claim.chiIndex >= options.size()) return false;
        }
        game.claims.put(seat, claim);
        return true;
    }

    public static boolean allClaimsAnswered(GameState game) {
        return game.phase == Phase.CLAIM && game.claims.keySet().containsAll(game.eligibleClaimSeats);
    }

    public static void resolveClaims(GameState game) {
        if (game.phase != Phase.CLAIM) return;
        for (int seat : game.eligibleClaimSeats) game.claims.putIfAbsent(seat, Claim.pass());

        List<Integer> winners = game.claims.entrySet().stream()
                .filter(e -> e.getValue().kind == ClaimKind.HU)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(seat -> game.seatDistance(game.lastDiscardSeat, seat)))
                .toList();
        if (!winners.isEmpty()) {
            settleWin(game, winners, false, game.lastDiscardSeat, game.lastDiscard, false, false);
            return;
        }

        Optional<Map.Entry<Integer, Claim>> pongOrKong = game.claims.entrySet().stream()
                .filter(e -> e.getValue().kind == ClaimKind.PONG || e.getValue().kind == ClaimKind.KONG)
                .min(Comparator.comparingInt(e -> game.seatDistance(game.lastDiscardSeat, e.getKey())));
        if (pongOrKong.isPresent()) {
            int seat = pongOrKong.get().getKey();
            ClaimKind kind = pongOrKong.get().getValue().kind;
            if (kind == ClaimKind.KONG) executeExposedKong(game, seat);
            else executePong(game, seat);
            return;
        }

        Optional<Map.Entry<Integer, Claim>> chi = game.claims.entrySet().stream()
                .filter(e -> e.getValue().kind == ClaimKind.CHI)
                .findFirst();
        if (chi.isPresent()) {
            executeChi(game, chi.get().getKey(), chi.get().getValue().chiIndex);
            return;
        }

        game.firstTurnCycle = false;
        beginTurnWithDraw(game, game.nextSeat(game.lastDiscardSeat));
    }

    private static void executePong(GameState game, int seat) {
        PlayerState player = game.player(seat);
        TileType type = game.lastDiscard.type;
        removeTiles(player.hand, type, 2);
        player.melds.add(new Meld(MeldKind.PONG, List.of(type, type, type), game.lastDiscardSeat));
        removeLastDiscardFromRiver(game);
        game.currentSeat = seat;
        game.maySelfWin = false;
        game.lastDrawnTile = null;
        game.lastDrawWasBack = false;
        game.phase = Phase.DISCARD;
        game.emit(player.name + " 碰「" + type.display + "」");
    }

    private static void executeExposedKong(GameState game, int seat) {
        PlayerState player = game.player(seat);
        TileType type = game.lastDiscard.type;
        removeTiles(player.hand, type, 3);
        player.melds.add(new Meld(MeldKind.KONG, List.of(type, type, type, type), game.lastDiscardSeat));
        removeLastDiscardFromRiver(game);
        game.currentSeat = seat;
        Tile replacement = drawForPlayer(game, seat, true);
        if (replacement == null) {
            exhaustiveDraw(game);
            return;
        }
        game.maySelfWin = true;
        game.phase = Phase.DISCARD;
        game.emit(player.name + " 明槓「" + type.display + "」並補牌");
    }

    private static void executeChi(GameState game, int seat, int optionIndex) {
        PlayerState player = game.player(seat);
        List<TileType> sequence = game.chiOptions.get(seat).get(optionIndex);
        boolean skippedDiscard = false;
        for (TileType type : sequence) {
            if (!skippedDiscard && type == game.lastDiscard.type) {
                skippedDiscard = true;
            } else {
                removeTiles(player.hand, type, 1);
            }
        }
        player.melds.add(new Meld(MeldKind.CHI, sequence, game.lastDiscardSeat));
        removeLastDiscardFromRiver(game);
        game.currentSeat = seat;
        game.maySelfWin = false;
        game.lastDrawnTile = null;
        game.lastDrawWasBack = false;
        game.phase = Phase.DISCARD;
        game.emit(player.name + " 吃「" + sequence.get(0).display + "、" + sequence.get(1).display + "、" + sequence.get(2).display + "」");
    }

    private static void removeLastDiscardFromRiver(GameState game) {
        List<Tile> river = game.player(game.lastDiscardSeat).discards;
        if (!river.isEmpty() && river.get(river.size() - 1).id == game.lastDiscard.id) {
            river.remove(river.size() - 1);
        }
    }

    public static boolean canSelfWin(GameState game, int seat) {
        return game.phase == Phase.DISCARD && game.currentSeat == seat && game.maySelfWin && isWinning(game.player(seat).hand, game.player(seat).melds);
    }

    public static boolean selfWin(GameState game, int seat) {
        if (!canSelfWin(game, seat)) return false;
        Tile winningTile = game.lastDrawnTile;
        settleWin(game, List.of(seat), true, -1, winningTile, game.lastDrawWasBack, false);
        return true;
    }

    public static List<TileType> concealedKongOptions(GameState game, int seat) {
        if (game.phase != Phase.DISCARD || game.currentSeat != seat) return List.of();
        Map<TileType, Long> counts = game.player(seat).hand.stream()
                .collect(java.util.stream.Collectors.groupingBy(Tile::type, LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return counts.entrySet().stream().filter(e -> e.getValue() == 4).map(Map.Entry::getKey).toList();
    }

    public static boolean concealedKong(GameState game, int seat, TileType type) {
        if (!concealedKongOptions(game, seat).contains(type)) return false;
        PlayerState player = game.player(seat);
        removeTiles(player.hand, type, 4);
        player.melds.add(new Meld(MeldKind.CONCEALED_KONG, List.of(type, type, type, type), seat));
        Tile replacement = drawForPlayer(game, seat, true);
        if (replacement == null) {
            exhaustiveDraw(game);
            return true;
        }
        game.lastDrawWasBack = true;
        game.maySelfWin = true;
        game.emit(player.name + " 暗槓「" + type.display + "」並補牌");
        return true;
    }

    private static void removeTiles(List<Tile> hand, TileType type, int amount) {
        int removed = 0;
        Iterator<Tile> iterator = hand.iterator();
        while (iterator.hasNext() && removed < amount) {
            if (iterator.next().type == type) {
                iterator.remove();
                removed++;
            }
        }
        if (removed != amount) throw new IllegalStateException("Not enough tiles: " + type);
    }

    public static List<List<TileType>> chiOptions(List<Tile> hand, TileType discarded) {
        if (!discarded.isSuited()) return List.of();
        Set<TileType> types = EnumSet.noneOf(TileType.class);
        for (Tile tile : hand) types.add(tile.type);
        List<List<TileType>> result = new ArrayList<>();
        for (int start = discarded.rank - 2; start <= discarded.rank; start++) {
            if (start < 1 || start > 7) continue;
            TileType a = suited(discarded.suit, start);
            TileType b = suited(discarded.suit, start + 1);
            TileType c = suited(discarded.suit, start + 2);
            boolean hasA = a == discarded || types.contains(a);
            boolean hasB = b == discarded || types.contains(b);
            boolean hasC = c == discarded || types.contains(c);
            if (hasA && hasB && hasC) result.add(List.of(a, b, c));
        }
        return result;
    }

    private static TileType suited(Suit suit, int rank) {
        int offset = switch (suit) {
            case CHARACTERS -> 0;
            case DOTS -> 9;
            case BAMBOOS -> 18;
            default -> throw new IllegalArgumentException("not suited");
        };
        return TileType.values()[offset + rank - 1];
    }

    public static boolean isWinning(List<Tile> concealedTiles, List<Meld> melds) {
        if (concealedTiles.stream().anyMatch(t -> t.type.isFlower())) return false;
        int setsNeeded = 5 - melds.size();
        if (setsNeeded < 0) return false;
        if (concealedTiles.size() != setsNeeded * 3 + 2) return false;

        int[] counts = new int[34];
        for (Tile tile : concealedTiles) {
            int index = baseIndex(tile.type);
            if (index < 0) return false;
            counts[index]++;
        }
        for (int pair = 0; pair < counts.length; pair++) {
            if (counts[pair] >= 2) {
                counts[pair] -= 2;
                if (canFormSets(counts, setsNeeded, new HashMap<>())) {
                    counts[pair] += 2;
                    return true;
                }
                counts[pair] += 2;
            }
        }
        return false;
    }

    private static boolean canFormSets(int[] counts, int setsLeft, Map<String, Boolean> memo) {
        if (setsLeft == 0) {
            for (int count : counts) if (count != 0) return false;
            return true;
        }
        int first = -1;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) { first = i; break; }
        }
        if (first < 0) return false;
        String key = setsLeft + ":" + Arrays.toString(counts);
        Boolean cached = memo.get(key);
        if (cached != null) return cached;

        boolean result = false;
        if (counts[first] >= 3) {
            counts[first] -= 3;
            result = canFormSets(counts, setsLeft - 1, memo);
            counts[first] += 3;
        }
        if (!result && first < 27 && first % 9 <= 6 && counts[first + 1] > 0 && counts[first + 2] > 0) {
            counts[first]--; counts[first + 1]--; counts[first + 2]--;
            result = canFormSets(counts, setsLeft - 1, memo);
            counts[first]++; counts[first + 1]++; counts[first + 2]++;
        }
        memo.put(key, result);
        return result;
    }

    private static int baseIndex(TileType type) {
        if (type.isFlower()) return -1;
        return type.ordinal();
    }

    public record ScoreResult(int tai, List<String> patterns) {
        public ScoreResult {
            patterns = List.copyOf(patterns);
        }
        public String summary() { return String.join("、", patterns) + "，共 " + tai + " 台"; }
    }

    public static ScoreResult scoreHand(GameState game, int winnerSeat, boolean selfDraw, Tile winningTile,
                                        boolean kongDraw, boolean robKong) {
        PlayerState p = game.player(winnerSeat);
        List<String> patterns = new ArrayList<>();
        int tai = 1;
        patterns.add("底台 1");

        if (selfDraw) { tai += 1; patterns.add("自摸 1"); }
        if (p.isConcealed()) { tai += 1; patterns.add("門清 1"); }
        if (selfDraw && p.isConcealed()) { tai += 1; patterns.add("不求人 1"); }
        if (winnerSeat == game.dealerSeat) { tai += 1; patterns.add("莊家 1"); }
        if (winnerSeat == game.dealerSeat && game.dealerStreak > 0) {
            int bonus = game.dealerStreak * 2;
            tai += bonus;
            patterns.add("連莊拉莊 " + bonus);
        }
        if (kongDraw) { tai += 1; patterns.add("槓上開花 1"); }
        if (robKong) { tai += 1; patterns.add("搶槓 1"); }
        if (game.wallRemaining() == 0) { tai += 1; patterns.add(selfDraw ? "海底撈月 1" : "河底撈魚 1"); }

        Map<TileType, Integer> setCounts = winningSetTypeCounts(p, winningTile, selfDraw);
        TileType seatWind = windForSeat(game.seatDistance(game.dealerSeat, winnerSeat));
        TileType roundWind = windForSeat(game.roundWind % 4);
        for (TileType dragon : List.of(TileType.RED, TileType.GREEN, TileType.WHITE)) {
            if (setCounts.getOrDefault(dragon, 0) > 0) { tai += 1; patterns.add(dragon.display + "刻 1"); }
        }
        if (setCounts.getOrDefault(seatWind, 0) > 0) { tai += 1; patterns.add("門風 " + seatWind.display + " 1"); }
        if (setCounts.getOrDefault(roundWind, 0) > 0) { tai += 1; patterns.add("圈風 " + roundWind.display + " 1"); }

        int dragonSets = 0;
        for (TileType dragon : List.of(TileType.RED, TileType.GREEN, TileType.WHITE)) {
            if (setCounts.getOrDefault(dragon, 0) > 0) dragonSets++;
        }
        int dragonPair = pairType(p, winningTile, selfDraw) != null && pairType(p, winningTile, selfDraw).isDragon() ? 1 : 0;
        if (dragonSets == 3) { tai += 8; patterns.add("大三元 8"); }
        else if (dragonSets == 2 && dragonPair == 1) { tai += 4; patterns.add("小三元 4"); }

        int windSets = 0;
        for (TileType wind : List.of(TileType.EAST, TileType.SOUTH, TileType.WEST, TileType.NORTH)) {
            if (setCounts.getOrDefault(wind, 0) > 0) windSets++;
        }
        TileType pair = pairType(p, winningTile, selfDraw);
        if (windSets == 4) { tai += 16; patterns.add("大四喜 16"); }
        else if (windSets == 3 && pair != null && pair.isWind()) { tai += 8; patterns.add("小四喜 8"); }

        List<TileType> allTypes = allNonFlowerTypes(p, winningTile, selfDraw);
        boolean anyHonor = allTypes.stream().anyMatch(TileType::isHonor);
        Set<Suit> suitedSuits = EnumSet.noneOf(Suit.class);
        allTypes.stream().filter(TileType::isSuited).forEach(t -> suitedSuits.add(t.suit));
        if (suitedSuits.isEmpty() && anyHonor) { tai += 16; patterns.add("字一色 16"); }
        else if (suitedSuits.size() == 1 && !anyHonor) { tai += 8; patterns.add("清一色 8"); }
        else if (suitedSuits.size() == 1) { tai += 4; patterns.add("混一色 4"); }

        boolean allTriplets = isAllTriplets(p, winningTile, selfDraw);
        if (allTriplets) { tai += 4; patterns.add("碰碰胡 4"); }
        if (allTriplets && p.isConcealed()) { tai += 8; patterns.add("五暗刻 8"); }

        boolean allTerminalOrHonor = allTypes.stream().allMatch(t -> t.isTerminal() || t.isHonor());
        boolean allTerminals = allTypes.stream().allMatch(TileType::isTerminal);
        if (allTerminals) { tai += 8; patterns.add("清么九 8"); }
        else if (allTerminalOrHonor) { tai += 4; patterns.add("混么九 4"); }

        int flowerTai = flowerTai(p, game.seatDistance(game.dealerSeat, winnerSeat));
        if (flowerTai > 0) {
            tai += flowerTai;
            patterns.add("花牌 " + flowerTai);
        }

        if (winningTile != null && isSingleWait(p, winningTile.type, selfDraw)) {
            tai += 1;
            patterns.add("獨聽 1");
        }
        return new ScoreResult(tai, patterns);
    }

    private static int flowerTai(PlayerState p, int seat) {
        Set<TileType> flowerTypes = EnumSet.noneOf(TileType.class);
        for (Tile flower : p.flowers) flowerTypes.add(flower.type);
        if (flowerTypes.size() == 8) return 8;
        int tai = 0;
        TileType season = TileType.values()[TileType.SPRING.ordinal() + seat];
        TileType plant = TileType.values()[TileType.PLUM.ordinal() + seat];
        if (flowerTypes.contains(season)) tai++;
        if (flowerTypes.contains(plant)) tai++;
        if (flowerTypes.containsAll(List.of(TileType.SPRING, TileType.SUMMER, TileType.AUTUMN, TileType.WINTER))) tai += 2;
        if (flowerTypes.containsAll(List.of(TileType.PLUM, TileType.ORCHID, TileType.BAMBOO_FLOWER, TileType.CHRYSANTHEMUM))) tai += 2;
        return tai;
    }

    private static TileType windForSeat(int seat) {
        return switch (Math.floorMod(seat, 4)) {
            case 0 -> TileType.EAST;
            case 1 -> TileType.SOUTH;
            case 2 -> TileType.WEST;
            default -> TileType.NORTH;
        };
    }

    private static List<TileType> allNonFlowerTypes(PlayerState p, Tile winningTile, boolean selfDraw) {
        List<TileType> list = new ArrayList<>();
        for (Tile tile : p.hand) list.add(tile.type);
        if (!selfDraw && winningTile != null) list.add(winningTile.type);
        for (Meld meld : p.melds) list.addAll(meld.tiles);
        return list;
    }

    private static Map<TileType, Integer> winningSetTypeCounts(PlayerState p, Tile winningTile, boolean selfDraw) {
        Map<TileType, Integer> result = new EnumMap<>(TileType.class);
        for (Meld meld : p.melds) {
            if (meld.kind != MeldKind.CHI) result.merge(meld.representative(), 1, Integer::sum);
        }
        List<Tile> concealed = new ArrayList<>(p.hand);
        if (!selfDraw && winningTile != null) concealed.add(winningTile);
        Decomposition decomposition = decompose(concealed, 5 - p.melds.size());
        if (decomposition != null) {
            for (List<TileType> set : decomposition.sets) {
                if (set.get(0) == set.get(1)) result.merge(set.get(0), 1, Integer::sum);
            }
        }
        return result;
    }

    private static TileType pairType(PlayerState p, Tile winningTile, boolean selfDraw) {
        List<Tile> concealed = new ArrayList<>(p.hand);
        if (!selfDraw && winningTile != null) concealed.add(winningTile);
        Decomposition d = decompose(concealed, 5 - p.melds.size());
        return d == null ? null : d.pair;
    }

    private static boolean isAllTriplets(PlayerState p, Tile winningTile, boolean selfDraw) {
        if (p.melds.stream().anyMatch(m -> m.kind == MeldKind.CHI)) return false;
        List<Tile> concealed = new ArrayList<>(p.hand);
        if (!selfDraw && winningTile != null) concealed.add(winningTile);
        Decomposition d = decompose(concealed, 5 - p.melds.size());
        return d != null && d.sets.stream().allMatch(set -> set.get(0) == set.get(1));
    }

    private record Decomposition(TileType pair, List<List<TileType>> sets) {}

    private static Decomposition decompose(List<Tile> tiles, int setsNeeded) {
        int[] counts = new int[34];
        for (Tile tile : tiles) {
            int idx = baseIndex(tile.type);
            if (idx < 0) return null;
            counts[idx]++;
        }
        for (int pair = 0; pair < 34; pair++) {
            if (counts[pair] < 2) continue;
            counts[pair] -= 2;
            List<List<TileType>> sets = new ArrayList<>();
            if (buildSets(counts, setsNeeded, sets)) {
                counts[pair] += 2;
                return new Decomposition(TileType.values()[pair], sets);
            }
            counts[pair] += 2;
        }
        return null;
    }

    private static boolean buildSets(int[] counts, int left, List<List<TileType>> out) {
        if (left == 0) {
            for (int count : counts) if (count != 0) return false;
            return true;
        }
        int first = -1;
        for (int i = 0; i < 34; i++) if (counts[i] > 0) { first = i; break; }
        if (first < 0) return false;
        TileType t = TileType.values()[first];
        if (counts[first] >= 3) {
            counts[first] -= 3;
            out.add(List.of(t, t, t));
            if (buildSets(counts, left - 1, out)) { counts[first] += 3; return true; }
            out.remove(out.size() - 1);
            counts[first] += 3;
        }
        if (first < 27 && first % 9 <= 6 && counts[first + 1] > 0 && counts[first + 2] > 0) {
            counts[first]--; counts[first + 1]--; counts[first + 2]--;
            out.add(List.of(t, TileType.values()[first + 1], TileType.values()[first + 2]));
            if (buildSets(counts, left - 1, out)) { counts[first]++; counts[first + 1]++; counts[first + 2]++; return true; }
            out.remove(out.size() - 1);
            counts[first]++; counts[first + 1]++; counts[first + 2]++;
        }
        return false;
    }

    private static boolean isSingleWait(PlayerState p, TileType winningType, boolean selfDraw) {
        List<Tile> base = new ArrayList<>(p.hand);
        // 自摸時手牌已包含胡牌張，先移除一張；榮胡時手牌尚未加入棄牌。
        if (selfDraw) {
            for (int i = base.size() - 1; i >= 0; i--) {
                if (base.get(i).type == winningType) { base.remove(i); break; }
            }
        }
        int wins = 0;
        for (int i = 0; i < 34; i++) {
            TileType type = TileType.values()[i];
            List<Tile> test = new ArrayList<>(base);
            test.add(new Tile(-1000 - i, type));
            if (isWinning(test, p.melds)) wins++;
            if (wins > 1) return false;
        }
        return wins == 1;
    }

    private static void settleWin(GameState game, List<Integer> winners, boolean selfDraw, int discarder,
                                  Tile winningTile, boolean kongDraw, boolean robKong) {
        game.phase = Phase.ROUND_END;
        for (int winner : winners) {
            ScoreResult score = scoreHand(game, winner, selfDraw, winningTile, kongDraw, robKong);
            int payment = score.tai * 100;
            if (selfDraw) {
                for (int seat = 0; seat < 4; seat++) {
                    if (seat == winner) continue;
                    game.player(seat).score -= payment;
                    game.player(winner).score += payment;
                }
            } else {
                game.player(discarder).score -= payment;
                game.player(winner).score += payment;
            }
            game.emit(game.player(winner).name + (selfDraw ? " 自摸！" : " 胡牌！") + score.summary());
        }
        boolean dealerWon = winners.contains(game.dealerSeat);
        finishRound(game, dealerWon);
    }

    public static void exhaustiveDraw(GameState game) {
        game.phase = Phase.ROUND_END;
        game.emit("牌山摸完，本局流局，莊家續莊");
        finishRound(game, true);
    }

    private static void finishRound(GameState game, boolean dealerContinues) {
        if (dealerContinues) {
            game.dealerStreak++;
        } else {
            game.dealerStreak = 0;
            game.dealerSeat = game.nextSeat(game.dealerSeat);
            game.dealerChanges++;
        }
        game.handNumber++;
        if (game.dealerChanges >= 4) {
            game.phase = Phase.GAME_END;
            PlayerState champion = game.players.stream().max(Comparator.comparingInt(p -> p.score)).orElse(game.player(0));
            game.emit("一圈結束，冠軍是 " + champion.name + "（" + champion.score + " 分）");
        }
    }

    public static void continueAfterRound(GameState game) {
        if (game.phase == Phase.ROUND_END) startRound(game);
    }

    public static void sortAllHands(GameState game) {
        game.players.forEach(p -> sortHand(p.hand));
    }

    public static void sortHand(List<Tile> hand) {
        hand.sort(Comparator.comparingInt(t -> t.type.ordinal()));
    }

    public static int chooseBotDiscard(GameState game, int seat) {
        PlayerState p = game.player(seat);
        if (p.hand.isEmpty()) return -1;
        Map<TileType, Long> counts = p.hand.stream().collect(java.util.stream.Collectors.groupingBy(Tile::type, java.util.stream.Collectors.counting()));
        Tile best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Tile tile : p.hand) {
            TileType t = tile.type;
            int usefulness = (int) (counts.getOrDefault(t, 0L) * 5);
            if (t.isHonor()) {
                if (counts.get(t) == 1) usefulness -= 3;
            } else {
                for (Tile other : p.hand) {
                    if (other == tile || other.type.suit != t.suit) continue;
                    int distance = Math.abs(other.type.rank - t.rank);
                    if (distance == 1) usefulness += 3;
                    else if (distance == 2) usefulness += 1;
                }
                if (t.isTerminal()) usefulness -= 1;
            }
            if (usefulness < bestScore || (usefulness == bestScore && game.random.nextBoolean())) {
                best = tile;
                bestScore = usefulness;
            }
        }
        return best == null ? p.hand.get(p.hand.size() - 1).id : best.id;
    }

    public static Claim chooseBotClaim(GameState game, int seat) {
        Set<ClaimKind> options = availableClaims(game, seat);
        if (options.contains(ClaimKind.HU)) return Claim.of(ClaimKind.HU);
        if (options.contains(ClaimKind.KONG) && game.random.nextDouble() < 0.65) return Claim.of(ClaimKind.KONG);
        if (options.contains(ClaimKind.PONG) && game.random.nextDouble() < 0.40) return Claim.of(ClaimKind.PONG);
        if (options.contains(ClaimKind.CHI) && game.random.nextDouble() < 0.30) return new Claim(ClaimKind.CHI, 0);
        return Claim.pass();
    }
}
