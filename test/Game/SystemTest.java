package game;

import Client.ComputerPlayer;
import Game.Game;
import Game.Board;
import Game.Move;
import Server.Server;
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
        Assertions.assertTrue(game.doMove(new Move(-1, gameBoard.findPieceById(game.getAvailablePieces(), 0), gameBoard.findPieceById(game.getAvailablePieces(), 0))));
        //Valid First Move
        Assertions.assertTrue(game.doMove(new Move(0, gameBoard.findPieceById(game.getAvailablePieces(), 0), gameBoard.findPieceById(game.getAvailablePieces(), 1))));
        //Valid move
        Assertions.assertFalse(game.doMove(new Move(0, gameBoard.findPieceById(game.getAvailablePieces(), 1), gameBoard.findPieceById(game.getAvailablePieces(), 2))));
        //Occupied board invalid move
        Assertions.assertFalse(game.doMove(new Move(0, gameBoard.findPieceById(game.getAvailablePieces(), 0), gameBoard.findPieceById(game.getAvailablePieces(), 2))));
        //Used piece invalid move
        Assertions.assertEquals(15, game.getValidMoves().size());
    }

    @Test
    public void testCurrentPiece(){
        //Do first move
        Assertions.assertTrue(game.doMove(new Move(-1, game.getPieceById(0), game.getPieceById(0))));
        Assertions.assertEquals(game.getPieceById(0), game.getCurrentPiece());
        game.doMove(new Move(1, gameBoard.findPieceById(game.getAvailablePieces(), 0), gameBoard.findPieceById(game.getAvailablePieces(), 1)));
        Assertions.assertEquals(game.getPieceById(1), game.getCurrentPiece());
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



}
