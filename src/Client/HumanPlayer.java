package Client;

import Game.AbstractPlayer;
import Game.Game;
import Game.Move;
import Game.Piece;

public class HumanPlayer extends AbstractPlayer {
    private java.util.Scanner input = new java.util.Scanner(System.in);

    public HumanPlayer(String name){
        super(name);
    }
    
    @Override
    public Move determineMove(Game game) {
        System.out.println("It is your turn, " + getName() + "!");
        
        // 1. Placement (if we have a piece)
        int placementIndex = -1;
        if (game.getCurrentPiece() != null) {
            System.out.println("You must place the piece: " + game.getCurrentPiece());
            System.out.println("Available board positions: " + game.getValidMoves().stream().map(Move::getBoardIndex).toList());
            
            while (true) {
                System.out.print("Enter board position (0-15): ");
                if (input.hasNextInt()) {
                    placementIndex = input.nextInt();
                    // Validate
                    if (placementIndex >= 0 && placementIndex < 16 && game.getBoard().getPiece(placementIndex) == null) {
                        break;
                    }
                } else {
                    input.next(); // consume invalid input
                }
                System.out.println("Invalid position. Try again.");
            }
        } else {
            System.out.println("No piece to place (First move).");
        }
        
        // 2. Pick next piece
        System.out.println("Pick a piece for your opponent:");
        System.out.println("Available pieces (IDs): " + game.getAvailablePieces().stream().map(Piece::getId).toList());
        
        Piece nextPiece = null;
        while (true) {
            System.out.print("Enter piece ID: ");
            if (input.hasNextInt()) {
                int id = input.nextInt();
                nextPiece = game.getPieceById(id);
                if (nextPiece != null) {
                    break;
                }
            } else {
                input.next();
            }
            System.out.println("Invalid piece ID. Try again.");
        }
        
        input.nextLine(); // consume newline
        
        return new Move(placementIndex, game.getCurrentPiece(), nextPiece);
    }
}
