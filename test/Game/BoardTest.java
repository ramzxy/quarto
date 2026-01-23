package Game;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BoardTest {
    private Board board;
    private Piece[] pieces;
    private final int PIECE_NUMBER = 16;


    @BeforeEach
    public void setUp(){
        board = new Board();
        pieces = new Piece[PIECE_NUMBER];

        //Assigns a unique id and characteristics for every single possible piece
        for(int i = 0; i < PIECE_NUMBER; i++){
            //Turns i into list of booleans that can be put inside piece's constructor
            boolean[] flags = new boolean[4];

            for(int j = 0; j < 4; j++){
                /*Right shifts the binary representation
                Take the least significant position, make it into boolean
                Put it from the last in the list
                */
                flags[3-j] = ((i >> j) & 1) == 1;
            }
            pieces[i] = new Piece(i, flags[1], flags[0], flags[3], flags[2]);
            System.out.println(pieces[i].toString());
        }
    }



    @Test
    public void testSetGetPiece(){
        Assertions.assertNull(board.getPiece(0)); //Should not return anything
        Assertions.assertThrows(IllegalArgumentException.class, () -> board.getPiece(20)); //Should throw an exception
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> board.setPiece(31, pieces[0])); //Invalid set index
        board.setPiece(0, pieces[3]); //Valid set
        Assertions.assertEquals(board.getPiece(0), pieces[3]);
    }

    @Test
    public void testIsFull(){
        Assertions.assertFalse(board.isFull());
        for(int i = 0; i < pieces.length; i++){
            board.setPiece(i, pieces[i]);
        }
        Assertions.assertTrue(board.isFull());
    }

    @Test
    public void testHorizontalLine(){
        board.setPiece(0, pieces[0]);
        board.setPiece(1, pieces[1]);
        board.setPiece(2, pieces[2]);
        Assertions.assertFalse(board.hasWinningLine());
        board.setPiece(3, pieces[3]);
        Assertions.assertTrue(board.hasWinningLine());
    }

    @Test
    public void testVerticalLine(){
        board.setPiece(0, pieces[0]);
        board.setPiece(4, pieces[1]);
        board.setPiece(8, pieces[2]);
        Assertions.assertFalse(board.hasWinningLine());
        board.setPiece(12, pieces[3]);
        Assertions.assertTrue(board.hasWinningLine());
    }

    @Test
    public void testDiagonalLine(){
        board.setPiece(0, pieces[0]);
        board.setPiece(5, pieces[1]);
        board.setPiece(10, pieces[2]);
        Assertions.assertFalse(board.hasWinningLine());
        board.setPiece(15, pieces[3]);
        Assertions.assertTrue(board.hasWinningLine());
    }
}
