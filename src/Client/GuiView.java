package Client;

import Game.Game;
import Game.Move;
import Game.Piece;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;


import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * JavaFX implementation of the ClientView.
 * Manages the Stage and switches scenes (Login -> Lobby -> Game).
 */
public class GuiView implements ClientView {

    private Stage primaryStage;
    private GameClient client; // Reference to the client to send actions

    // Logic State
    private boolean isFirstMove = false;
    
    // UI Elements for Game Scene
    private VBox messageLog;
    private GridPane boardGrid;
    private FlowPane availablePiecesPane;
    private StackPane currentPiecePane;
    private Label gameStatusLabel;

    public GuiView(Stage stage) {
        this.primaryStage = stage;
        showLoginScene();
    }

    public void setGameClient(GameClient client) {
        this.client = client;
    }

    // --- Scene Setup Helpers ---

    private void showLoginScene() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #f0f4f8;");

        Label title = new Label("QUARTO");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        title.setTextFill(Color.DARKSLATEBLUE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        TextField hostField = new TextField("127.0.0.1");
        TextField portField = new TextField("4444");
        TextField userField = new TextField("Player1");

        grid.add(new Label("Host:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Port:"), 0, 1);
        grid.add(portField, 1, 1);
        grid.add(new Label("Username:"), 0, 2);
        grid.add(userField, 1, 2);

        Button connectButton = new Button("Connect & Login");
        connectButton.setStyle("-fx-background-color: #5d6cc0; -fx-text-fill: white; -fx-font-size: 14px;");
        connectButton.setOnAction(e -> {
            String host = hostField.getText();
            int port = Integer.parseInt(portField.getText());
            String user = userField.getText();

            // Connect in a separate thread to avoid freezing UI?
            // For now, simple direct call, but catch exceptions
            new Thread(() -> {
                try {
                    GameClient newClient = new GameClient(host, port, this);
                    setGameClient(newClient);
                    newClient.start();
                    newClient.login(user);
                    // Login success will trigger callback to show Lobby
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Connection failed: " + ex.getMessage()));
                    ex.printStackTrace();
                }
            }).start();
        });

        root.getChildren().addAll(title, grid, connectButton);
        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("Quarto - Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void showLobbyScene(String username) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        
        Label welcome = new Label("Welcome, " + username + "!");
        welcome.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        ListView<String> playerList = new ListView<>();
        playerList.setPrefHeight(200);

        Button queueButton = new Button("Join Queue");
        queueButton.setOnAction(e -> {
            if (client.isInQueue()) {
                client.leaveQueue();
                queueButton.setText("Join Queue");
            } else {
                client.joinQueue();
                queueButton.setText("Leave Queue (Waiting...)");
            }
        });

        Button refreshButton = new Button("Refresh Players");
        refreshButton.setOnAction(e -> client.requestPlayerList());
        
        // Initial refresh
        client.requestPlayerList();

        root.getChildren().addAll(welcome, new Label("Online Players:"), playerList, refreshButton, queueButton);
        Scene scene = new Scene(root, 400, 500);
        
        // Store reference to list to update it later
        // A hacky way: attach userData or keep a field? 
        // I'll keep it simple: requestPlayerList callback updates this scene if active.
        root.setUserData(playerList); 

        primaryStage.setTitle("Quarto - Lobby");
        primaryStage.setScene(scene);
    }

    private void showGameScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        // -- Center: Board --
        boardGrid = new GridPane();
        boardGrid.setAlignment(Pos.CENTER);
        boardGrid.setHgap(5);
        boardGrid.setVgap(5);
        boardGrid.setStyle("-fx-background-color: #ddd; -fx-padding: 10; -fx-background-radius: 5;");
        
        // Initialize 4x4 empty cells
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                Pane cell = new StackPane();
                cell.setPrefSize(120, 120);
                cell.setStyle("-fx-background-color: white; -fx-border-color: #999;");
                final int pos = i * 4 + j;
                cell.setOnMouseClicked(e -> handleBoardClick(pos));
                boardGrid.add(cell, j, i);
            }
        }
        root.setCenter(boardGrid);

        // -- Right: Controls & Status --
        VBox rightPanel = new VBox(15);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setPrefWidth(250);
        rightPanel.setStyle("-fx-border-color: #eee; -fx-border-width: 0 0 0 1;");
        rightPanel.setAlignment(Pos.TOP_CENTER);

        gameStatusLabel = new Label("Game Started");
        gameStatusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        gameStatusLabel.setWrapText(true);
        gameStatusLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Piece to Place
        VBox pieceToPlaceBox = new VBox(10);
        pieceToPlaceBox.setAlignment(Pos.CENTER);
        pieceToPlaceBox.setStyle("-fx-background-color: #e8eaf6; -fx-padding: 10; -fx-background-radius: 5;");
        pieceToPlaceBox.getChildren().add(new Label("Piece to Place:"));
        currentPiecePane = new StackPane();
        currentPiecePane.setPrefSize(60, 60);
        pieceToPlaceBox.getChildren().add(currentPiecePane);

        // Available Pieces
        VBox availableBox = new VBox(10);
        availableBox.setAlignment(Pos.CENTER);
        availableBox.getChildren().add(new Label("Available Pieces:"));
        availablePiecesPane = new FlowPane();
        availablePiecesPane.setHgap(5);
        availablePiecesPane.setVgap(5);
        availablePiecesPane.setPrefWrapLength(220);
        availableBox.getChildren().add(availablePiecesPane);
        
        rightPanel.getChildren().addAll(gameStatusLabel, pieceToPlaceBox, availableBox);
        root.setRight(rightPanel);

        // -- Bottom: Log --
        messageLog = new VBox(5);
        messageLog.setPrefHeight(100);
        ScrollPane scrollLog = new ScrollPane(messageLog);
        scrollLog.setPrefViewportHeight(100);
        scrollLog.setFitToWidth(true);
        root.setBottom(scrollLog);

        Scene scene = new Scene(root, 900, 700);
        primaryStage.setTitle("Quarto - Game");
        primaryStage.setScene(scene);
    }

    // --- Game Logic ---

    // Temp state for move construction
    private int selectedBoardPos = -1;
    private int selectedPieceId = -1;

    private void handleBoardClick(int pos) {
        if (!client.isInGame()) return;
        
        Game game = client.getLocalGame();
        if (game == null) return;

        // Validation
        if (game.getCurrentPiece() == null) {
            // It's the first move of the game (Special case) OR we are waiting for opponent
            // Actually, if it's first move, pieceToPlace is null.
            // If it's normal move, we MUST place a piece.
            // If we have no piece to place, we cannot click board.
            logMessage("No piece to place! Select a piece for opponent if start of game.");
            return;
        }

        // Check if it's our turn provided we have a piece
        // Actually, logic:
        // 1. Place Piece (if we have one)
        // 2. Select Next Piece
        // 3. Confirm
        
        // Simplified Interaction:
        // If we have a piece to place, clicking an empty spot places it there visually.
        // We store 'selectedBoardPos' = pos.
        
        if (game.getBoard().getPiece(pos) != null) {
            logMessage("Position occupied.");
            return;
        }
        
        selectedBoardPos = pos;
        renderGame(game); // Re-render to show selection (could highlight)
        logMessage("Selected position " + pos + ". Now pick a piece for opponent.");
    }
    
    private void handlePieceSelection(int pieceId) {
        if (client == null) return;
        
        // Case 1: First Move (No placement, just give piece)
        if (isFirstMove) {
             client.makeFirstMove(pieceId);
             isFirstMove = false;
             selectedPieceId = -1;
             return;
        }

        // Case 2: Normal Move (Place + Give)
        if (selectedBoardPos != -1) {
            client.makeMove(selectedBoardPos, pieceId);
            selectedBoardPos = -1;
            selectedPieceId = -1;
        } else {
             // If user clicks a piece but hasn't placed their current piece yet
             // Maybe flash a message?
             logMessage("Place your piece on the board first!");
        }
    }

    private void renderGame(Game game) {
        if (boardGrid == null) return; // Not in game scene yet

        // 1. Board
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int pos = i * 4 + j;
                Pane cell = (Pane) getCellAt(i, j);
                cell.getChildren().clear();
                
                Piece p = game.getBoard().getPiece(pos);
                
                // If we have a selected position pending (not yet sent to server), show it
                if (pos == selectedBoardPos && game.getCurrentPiece() != null) {
                    p = game.getCurrentPiece();
                    cell.setStyle("-fx-background-color: #e0f7fa; -fx-border-color: #00bcd4; -fx-border-width: 2;");
                } else {
                    cell.setStyle("-fx-background-color: white; -fx-border-color: #999;");
                }

                if (p != null) {
                    cell.getChildren().add(PieceRenderer.render(p, 60));
                }
            }
        }

        // 2. Current Piece to Place
        currentPiecePane.getChildren().clear();
        Piece current = game.getCurrentPiece();
        if (current != null) {
            currentPiecePane.getChildren().add(PieceRenderer.render(current, 50));
            // Reset border
            currentPiecePane.setStyle("-fx-border-color: transparent;");
        } else {
             // If first move, maybe show help text?
        }

        // 3. Available Pieces
        availablePiecesPane.getChildren().clear();
        for (Piece p : game.getAvailablePieces()) {
            Node pNode = PieceRenderer.render(p, 40);
            StackPane wrapper = new StackPane(pNode);
            wrapper.setStyle("-fx-padding: 5; -fx-cursor: hand;");
            
            // Highlight if selected
            if (p.getId() == selectedPieceId) {
                wrapper.setStyle("-fx-padding: 5; -fx-background-color: #ffeba0; -fx-background-radius: 5;");
            }

            wrapper.setOnMouseClicked(e -> handlePieceSelection(p.getId()));
            availablePiecesPane.getChildren().add(wrapper);
        }
        
        // 4. Status Text
        String user = client.getPlayer().getName();
        // Simple heuristic for turn: 
        // Game has currentPlayerName(), but that updates instantly on local model?
        // Let's rely on info:
        gameStatusLabel.setText("Current Turn: " + game.getCurrentPlayerName() + 
            "\n(You are " + user + ")");
    }

    private Node getCellAt(int row, int col) {
        for (Node node : boardGrid.getChildren()) {
            if (GridPane.getRowIndex(node) == row && GridPane.getColumnIndex(node) == col) {
                return node;
            }
        }
        return null;
    }

    private void logMessage(String msg) {
         Label l = new Label(msg);
         l.setWrapText(true);
         messageLog.getChildren().add(0, l); // Add to top
    }

    // --- ClientView Implementation ---

    @Override
    public void displayGame(Game game) {
        Platform.runLater(() -> {
            if (primaryStage.getScene() == null || boardGrid == null) {
                showGameScene();
            }
            renderGame(game);
        });
    }

    @Override
    public String promptUsername() {
        // Must block if called from background thread
        if (Platform.isFxApplicationThread()) {
             // Should not happen if well designed, but if so, using Dialog
             TextInputDialog dialog = new TextInputDialog();
             dialog.setTitle("Username Taken");
             dialog.setHeaderText("Username already in use");
             dialog.setContentText("Enter a new username:");
             return dialog.showAndWait().orElse("Player" + System.currentTimeMillis());
        }

        FutureTask<String> future = new FutureTask<>(() -> {
             TextInputDialog dialog = new TextInputDialog();
             dialog.setTitle("Username Taken");
             dialog.setHeaderText("Username already in use");
             dialog.setContentText("Enter a new username:");
             return dialog.showAndWait().orElse("Player" + System.currentTimeMillis());
        });
        Platform.runLater(future);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return "ErrorUser";
        }
    }

    @Override
    public Move requestMove(Game game) {
        // Not used in this async architecture?
        // The TUI returns null and handles input loop separately.
        // We will do same: return null. we handle moves via UI events calling client.makeMove()
        return null;
    }

    @Override
    public void showMessage(String message) {
        Platform.runLater(() -> {
            logMessage(message);
        });
    }

    @Override
    public void showLoggedIn(String playerName) {
        Platform.runLater(() -> {
            showLobbyScene(playerName);
        });
    }

    @Override
    public void showError(String error) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Error");
            alert.setContentText(error);
            alert.showAndWait();
        });
    }

    @Override
    public void showDisconnected() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Disconnected");
            alert.setHeaderText("Server Disconnected");
            alert.showAndWait();
            showLoginScene();
        });
    }

    @Override
    public void showUserList(String[] users) {
        Platform.runLater(() -> {
            if (primaryStage.getScene() != null && primaryStage.getScene().getRoot().getUserData() instanceof ListView) {
                ListView<String> list = (ListView<String>) primaryStage.getScene().getRoot().getUserData();
                list.getItems().clear();
                list.getItems().addAll(users);
            }
        });
    }

    @Override
    public void showGameStarted(String player1, String player2, boolean iAmFirst) {
        Platform.runLater(() -> {
            this.isFirstMove = iAmFirst;
            showGameScene();
            logMessage("Game Started: " + player1 + " vs " + player2);
            if (iAmFirst) {
                logMessage("YOU GO FIRST! Pick a piece for opponent.");
                logMessage("YOU GO FIRST! Pick a piece for opponent.");
            } else {
                 logMessage("Opponent goes first.");
            }
        });
    }

    @Override
    public void showMove(String[] moveParts) {
        Platform.runLater(() -> {
            // Log move
            if (moveParts.length >= 2) logMessage("Move received: " + String.join(" ", moveParts));
        });
    }

    @Override
    public void showGameOver(String reason, String winner) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Game Over");
            alert.setHeaderText(reason);
            alert.setContentText("Winner: " + (winner == null ? "None" : winner));
            alert.showAndWait();
            // Return to lobby?
            // For now stay on board or go back?
            // showLobbyScene(client.getPlayer().getName());
        });
    }
}
