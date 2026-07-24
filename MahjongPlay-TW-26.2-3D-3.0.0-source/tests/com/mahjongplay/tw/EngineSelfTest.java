package com.mahjongplay.tw;

import java.util.*;
import static com.mahjongplay.tw.TaiwanMahjongEngine.*;

public final class EngineSelfTest {
    private static int id = 1;
    private static Tile t(TileType type) { return new Tile(id++, type); }
    private static List<Tile> tiles(TileType... types) {
        List<Tile> out = new ArrayList<>();
        for (TileType type : types) out.add(t(type));
        return out;
    }

    public static void main(String[] args) {
        testStandardWinningHand();
        testNonWinningHand();
        testOpenMeldWin();
        testChiOptions();
        testFullRoundSetup();
        testPongDoesNotAllowFakeSelfDraw();
        testFlowerReplacementIsNotKongDraw();
        testSeatWindRotatesWithDealer();
        System.out.println("EngineSelfTest: ALL TESTS PASSED");
    }

    private static void testStandardWinningHand() {
        List<Tile> hand = tiles(
                TileType.M1,TileType.M2,TileType.M3,
                TileType.M4,TileType.M5,TileType.M6,
                TileType.P2,TileType.P3,TileType.P4,
                TileType.S7,TileType.S8,TileType.S9,
                TileType.RED,TileType.RED,TileType.RED,
                TileType.EAST,TileType.EAST);
        assertTrue(isWinning(hand, List.of()), "standard 5 sets + pair should win");
    }

    private static void testNonWinningHand() {
        List<Tile> hand = tiles(
                TileType.M1,TileType.M2,TileType.M4,
                TileType.M4,TileType.M5,TileType.M6,
                TileType.P2,TileType.P3,TileType.P4,
                TileType.S7,TileType.S8,TileType.S9,
                TileType.RED,TileType.RED,TileType.RED,
                TileType.EAST,TileType.EAST);
        assertTrue(!isWinning(hand, List.of()), "broken sequence must not win");
    }

    private static void testOpenMeldWin() {
        List<Tile> concealed = tiles(
                TileType.M1,TileType.M2,TileType.M3,
                TileType.P2,TileType.P3,TileType.P4,
                TileType.S7,TileType.S8,TileType.S9,
                TileType.RED,TileType.RED,TileType.RED,
                TileType.EAST,TileType.EAST);
        Meld pong = new Meld(MeldKind.PONG, List.of(TileType.WHITE,TileType.WHITE,TileType.WHITE), 1);
        assertTrue(isWinning(concealed, List.of(pong)), "four concealed sets + open pong + pair should win");
    }

    private static void testChiOptions() {
        List<Tile> hand = tiles(TileType.M1, TileType.M2, TileType.M4, TileType.M5);
        List<List<TileType>> options = chiOptions(hand, TileType.M3);
        assertTrue(options.size() == 3, "M3 with 12/24/45 should have three chi choices");
    }

    private static void testFullRoundSetup() {
        List<PlayerState> players = List.of(
                new PlayerState("a","甲",false), new PlayerState("b","乙",false),
                new PlayerState("c","丙",false), new PlayerState("d","丁",false));
        GameState game = newGame(players, 1234L);
        assertTrue(game.wallRemaining() + players.stream().mapToInt(p -> p.hand.size()+p.flowers.size()).sum() == 144,
                "all 144 tiles must be accounted for");
        assertTrue(players.get(0).effectiveTileCount() == 17, "dealer should have 17 effective tiles");
        for (int i=1;i<4;i++) assertTrue(players.get(i).effectiveTileCount() == 16, "non-dealer should have 16 effective tiles");
    }

    private static void testPongDoesNotAllowFakeSelfDraw() {
        List<PlayerState> players = List.of(
                new PlayerState("a","甲",false), new PlayerState("b","乙",false),
                new PlayerState("c","丙",false), new PlayerState("d","丁",false));
        GameState game = new GameState(players, 1L);
        game.phase = Phase.DISCARD;
        game.currentSeat = 0;
        players.get(0).hand.add(t(TileType.RED));
        for (int i = 0; i < 16; i++) players.get(0).hand.add(t(TileType.M9));
        players.get(1).hand.addAll(tiles(
                TileType.M1,TileType.M2,TileType.M3,
                TileType.M4,TileType.M5,TileType.M6,
                TileType.P2,TileType.P3,TileType.P4,
                TileType.S7,TileType.S8,TileType.S9,
                TileType.EAST,TileType.EAST,TileType.RED,TileType.RED));
        int discardId = players.get(0).hand.get(0).id();
        assertTrue(discard(game, 0, discardId), "discard should succeed");
        assertTrue(submitClaim(game, 1, Claim.of(ClaimKind.PONG)), "pong should be available");
        resolveClaims(game);
        assertTrue(game.currentSeat == 1 && game.phase == Phase.DISCARD, "pong claimant should take turn");
        assertTrue(isWinning(players.get(1).hand, players.get(1).melds), "post-pong shape is complete for regression test");
        assertTrue(!canSelfWin(game, 1), "claiming pong must not be counted as self draw");
    }

    private static void testFlowerReplacementIsNotKongDraw() {
        List<PlayerState> players = List.of(
                new PlayerState("a","甲",false), new PlayerState("b","乙",false),
                new PlayerState("c","丙",false), new PlayerState("d","丁",false));
        GameState game = new GameState(players, 2L);
        game.phase = Phase.DISCARD;
        game.wall.addLast(t(TileType.SPRING));
        game.wall.addLast(t(TileType.M2));
        Tile replacement = t(TileType.M1);
        game.wall.addLast(replacement);
        beginTurnWithDraw(game, 0);
        assertTrue(game.lastDrawnTile != null && game.lastDrawnTile.id() == replacement.id(), "flower should be replaced from wall back");
        assertTrue(!game.lastDrawWasBack, "normal flower replacement must not award kong draw");
        assertTrue(game.maySelfWin, "normal draw should permit a self-win check");
    }

    private static void testSeatWindRotatesWithDealer() {
        List<PlayerState> players = List.of(
                new PlayerState("a","甲",false), new PlayerState("b","乙",false),
                new PlayerState("c","丙",false), new PlayerState("d","丁",false));
        GameState game = new GameState(players, 3L);
        game.dealerSeat = 1;
        game.roundWind = 2;
        PlayerState winner = players.get(1);
        winner.hand.addAll(tiles(
                TileType.EAST,TileType.EAST,TileType.EAST,
                TileType.M1,TileType.M2,TileType.M3,
                TileType.M4,TileType.M5,TileType.M6,
                TileType.P2,TileType.P3,TileType.P4,
                TileType.S7,TileType.S8,TileType.S9,
                TileType.RED,TileType.RED));
        Tile winning = winner.hand.get(winner.hand.size() - 1);
        ScoreResult score = scoreHand(game, 1, true, winning, false, false);
        assertTrue(score.patterns().contains("門風 東風 1"), "dealer must be East seat regardless of fixed table index");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
