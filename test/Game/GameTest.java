package Game;

import Client.ComputerPlayer;
import Client.HumanPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GameTest {
    HumanPlayer hp1 = new HumanPlayer("Player 1");
    HumanPlayer hp2 = new HumanPlayer("Player 2");
    ComputerPlayer cp2;
    private Game game = new Game(hp1, hp2);

    @BeforeEach
    public void setUp(){
        game.start();
    }

    @Test
    public void testStart(){

    }

    @Test
    public void testGetValidMoves(){

    }

    @Test
    public void testDoMove(){

    }

    @Test
    public void testAddListener(){

    }

    @Test
    public void testNotifyListener(){

    }
}
