package game;

import Game.Game;
import Game.Board;
import Game.Move;
import Server.ServerPlayer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

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
        /*  Row 0: LSQF  DTCH  DSQH  LTCF
            Row 1: DSCH  LTQH  DTQF  LSCH
            Row 2: LTCH  DSCF  LSQH  DTCF
            Row 3: DSQF  LTQF  LSCF  DTQH
         */
        gameBoard.setPiece(0, game.getPieceById(0));
        gameBoard.setPiece(1, game.getPieceById(7));
        gameBoard.setPiece(2, game.getPieceById(14));
        gameBoard.setPiece(3, game.getPieceById(9));
        gameBoard.setPiece(4, game.getPieceById(11));
        gameBoard.setPiece(5, game.getPieceById(12));
        gameBoard.setPiece(6, game.getPieceById(5));
        gameBoard.setPiece(7, game.getPieceById(2));
        gameBoard.setPiece(8, game.getPieceById(13));
        gameBoard.setPiece(9, game.getPieceById(10));
        gameBoard.setPiece(10, game.getPieceById(3));
        gameBoard.setPiece(11, game.getPieceById(4));
        gameBoard.setPiece(12, game.getPieceById(6));
        gameBoard.setPiece(13, game.getPieceById(1));
        gameBoard.setPiece(14, game.getPieceById(8));
        gameBoard.setPiece(15, game.getPieceById(15));
        System.out.println(gameBoard.toString());
        Assertions.assertTrue(gameBoard.isFull());
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

    @Test
    public void testRandomLegalMoves(){
        Random r = new Random();
        int freeIndex;
        game.doMove(new Move(-1, game.getPieceById(r.nextInt(16)), game.getPieceById(r.nextInt(16))));
        while(!game.isGameOver()){
            for (int i = 0; i < 15; i++) {
                if(gameBoard.getPiece(i) == null) freeIndex=i;
                Assertions.assertTrue(game.doMove(new Move(i, game.getCurrentPiece(), game.getAvailablePieces().get(0))));
            }
        }
        Assertions.assertTrue(game.isGameOver());

    }

    @Test
    public void testRandomGame(){
        //Everything here is random with many illegal moves
        Random r = new Random();

        game.doMove(new Move(-1, game.getPieceById(r.nextInt(16)), game.getPieceById(r.nextInt(16))));
        while(!game.isGameOver()){
            game.doMove(new Move(r.nextInt(16), game.getCurrentPiece(), gameBoard.findPieceById(game.getAvailablePieces(), r.nextInt(16))));
        }
        System.out.println(gameBoard.toString());
        Assertions.assertNotNull(game.getEndReason());
    }


}
