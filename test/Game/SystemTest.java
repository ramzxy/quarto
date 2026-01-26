package game;

import Game.Game;
import Game.Board;
import Game.Move;
import Server.ServerPlayer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SystemTest {
    private ServerPlayer player1;
    private ServerPlayer player2;
    private Game game;
    private Board gameBoard;

    @BeforeEach
    public void setUp(){
        player1 = new ServerPlayer("Player 1");
        player2 = new ServerPlayer("Player 2");
        game = new Game(player1, player2);
        gameBoard = game.getBoard();
    }


    @Test
    public void testMove(){
        //By testing doMove, getValidMoves is also tested
        Assertions.assertEquals(16, game.getValidMoves().size());
        Assertions.assertNotNull(gameBoard.findPieceById(game.getAvailablePieces(), 0));
        Assertions.assertTrue(game.doMove(new Move(-1, gameBoard.findPieceById(game.getAvailablePieces(), 0), gameBoard.findPieceById(game.getAvailablePieces(), 1))));
        //Valid First Move
        Assertions.assertTrue(game.doMove(new Move(0, gameBoard.findPieceById(game.getAvailablePieces(), 1), gameBoard.findPieceById(game.getAvailablePieces(), 2))));
        //Valid move
        Assertions.assertFalse(game.doMove(new Move(0, gameBoard.findPieceById(game.getAvailablePieces(), 2), gameBoard.findPieceById(game.getAvailablePieces(), 3))));
        //Invalid move
        System.out.println(gameBoard.toString());
        Assertions.assertEquals(15, game.getValidMoves().size());
    }

    @Test
    public void testCurrentPiece(){
        //Do first move
        game.doMove(new Move(-1, game.getPieceById(0), game.getPieceById(0)));
        System.out.println();
        game.doMove(new Move(1, gameBoard.findPieceById(game.getAvailablePieces(), 0), game.getPieceById(1)));
        System.out.println(gameBoard.toString());
        System.out.println(game.getAvailablePieces());
        System.out.println(game.getCurrentPiece());
        Assertions.assertFalse(game.getAvailablePieces().contains(game.getCurrentPiece()));
    }

    @Test
    public void testFullBoard(){
        game.doMove(new Move(-1, game.getPieceById(0), game.getPieceById(0)));
        for(int i = 0; i < 16; i++){
            game.doMove(new Move(i, game.getPieceById(i), game.getPieceById(i+1)));
        }
        Assertions.assertTrue(gameBoard.hasWinningLine());
        System.out.println(gameBoard.toString());
        Assertions.assertTrue(gameBoard.isFull());
    }

    @Test
    public void testWin(){
        game.doMove(new Move(-1, game.getPieceById(0), game.getPieceById(0)));
        for(int i = 4; i < 8; i++){
            game.doMove(new Move(i, game.getPieceById(i), game.getPieceById(i+1)));
        }
        System.out.println(gameBoard.toString());
        Assertions.assertTrue(gameBoard.hasWinningLine());
        Assertions.assertTrue(game.isGameOver());
    }

    @Test
    public void testDraw(){
        game.doMove(new Move(-1, game.getPieceById(0), game.getPieceById(0)));
        for(int i = 0; i < 16; i++){
            game.doMove(new Move(i, game.getPieceById(i), game.getPieceById(i+1)));
        }
        System.out.println(gameBoard.toString());
        Assertions.assertFalse(gameBoard.hasWinningLine());
        Assertions.assertTrue(game.isGameOver());
    }

    @Test
    public void testTurn(){
        String i = game.getCurrentPlayerName();
        game.doMove(new Move(-1, game.getPieceById(0), game.getPieceById(0)));
        String j = game.getCurrentPlayerName();
        Assertions.assertNotEquals(i, j);
    }




}
