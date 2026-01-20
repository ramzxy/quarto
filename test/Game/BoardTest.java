package Game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                flags[3-i] = ((i >> 1) & 1) == 1;
            }
            pieces[i] = new Piece(i, flags[0], flags[1], flags[2], flags[3]);
        }
    }



    @Test
    public void testSetPiece(){
        board.setPiece(31, pieces[0]); //Invalid index
        board.setPiece(0, pieces[3]); //Valid set

    }
}
