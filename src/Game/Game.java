package Game;

import Server.ClientHandler;

import java.util.ArrayList;
import java.util.List;

public class Game {
    private Board board;
    private List<Piece> availablePieces;
    private Piece currentPieceToPlace;
    private ClientHandler[] playerList;
    private int currentTurn;
    private List<GameListener> gameListeners;

    private final int PIECE_NUMBER = 16;

    /**
     * Creates an instance of Quarto game for client-side use (no players).
     * Used to track game state locally.
     */
    public Game() {
        board = new Board();
        availablePieces = new ArrayList<>();
        gameListeners = new ArrayList<>();
        initializePieces();
    }

    /**
     * Creates an instance of Quarto game with 2 players (server-side).
     */
    public Game(ClientHandler player1, ClientHandler player2) {
        board = new Board();
        playerList = new ClientHandler[]{player1, player2};
        availablePieces = new ArrayList<>();
        gameListeners = new ArrayList<>();
        initializePieces();
    }

    /**
     * Initializes all 16 pieces for the game.
     */
    private void initializePieces() {
        for (int i = 0; i < PIECE_NUMBER; i++) {
            // Turns i into list of booleans that can be put inside piece's constructor
            boolean[] flags = new boolean[4];

            for (int j = 0; j < 4; j++) {
                /*
                 * Right shifts the binary representation
                 * Take the least significant position, make it into boolean
                 * Put it from the last in the list
                 */
                flags[3 - j] = ((i >> j) & 1) == 1;
            }
            availablePieces.add(new Piece(i, flags[0], flags[1], flags[2], flags[3]));
        }
    }

    /**
     * Main function to be called when starting a particular game.
     */
    public void start() {

    }

    /**
     * Function to do a move.
     * @param move The move to be played
     */
    public void doMove(Move move) {

    }

    /**
     * Gives all valid moves that can be played in the current state of the game.
     * @return List of all moves that can be played
     */
    public List<Move> getValidMoves() {
        return null;
    }

    /**
     * Function to add a game listener to the game.
     * @param gameListener Implementation of GameListener to be added
     */
    public void addListener(GameListener gameListener) {
        gameListeners.add(gameListener);
    }

    /**
     * Notifies all listeners to the game about the current state of the game.
     */
    public void notifyListeners() {

    }

    /**
     * @return the game board
     */
    public Board getBoard() {
        return board;
    }

    /**
     * @return list of available pieces
     */
    public List<Piece> getAvailablePieces() {
        return availablePieces;
    }

    /**
     * @return the current piece to be placed
     */
    public Piece getCurrentPieceToPlace() {
        return currentPieceToPlace;
    }

    /**
     * Sets the current piece to be placed.
     */
    public void setCurrentPieceToPlace(Piece piece) {
        this.currentPieceToPlace = piece;
    }
}
