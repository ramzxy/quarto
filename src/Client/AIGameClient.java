package Client;

import Client.views.ClientView;
import Game.AbstractPlayer;
import ai.Strategy;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * GameClient specialized for AI players.
 * Overrides player creation to use ComputerPlayer.
 */
public class AIGameClient extends GameClient {
    private final Strategy strategy;
    private static final String STATS_FILE = "ai_stats.txt";
    private String currentOpponentName = null;

    public AIGameClient(String host, int port, ClientView view, Strategy strategy) throws IOException {
        super(host, port, view);
        this.strategy = strategy;
    }

    @Override
    public void receiveHello(String serverDescription) {
        System.out.println("Connected to server: " + serverDescription);
        String username = view.promptUsername();
        player = new ComputerPlayer(username, strategy);
        connection.sendLogin(username);
    }

    @Override
    public void receiveAlreadyLoggedIn() {
        view.showError("Username '" + player.getName() + "' is already in use.");
        String oldName = player.getName();
        String newName = oldName + "_" + (int)(Math.random() * 1000);
        view.showMessage("Retrying with username: " + newName);
        player = new ComputerPlayer(newName, strategy);
        connection.sendLogin(newName);
    }

    @Override
    public void receiveNewGame(AbstractPlayer player1, AbstractPlayer player2) {
        String myName = player.getName();
        currentOpponentName = player1.getName().equals(myName) ? player2.getName() : player1.getName();
        super.receiveNewGame(player1, player2);
    }

    @Override
    public void receiveGameOver(String reason, String winner) {
        if (currentOpponentName != null) {
            String result;
            if ("DRAW".equals(reason)) {
                result = "DRAW";
            } else if (player.getName().equals(winner)) {
                result = "WIN";
            } else {
                result = "LOSS";
            }
            logResult(currentOpponentName, result);
            currentOpponentName = null;
        }
        super.receiveGameOver(reason, winner);
    }

    private void logResult(String opponent, String result) {
        Map<String, int[]> stats = loadStats();
        int[] record = stats.getOrDefault(opponent, new int[]{0, 0, 0});
        
        switch (result) {
            case "WIN": record[0]++; break;
            case "LOSS": record[1]++; break;
            case "DRAW": record[2]++; break;
        }
        stats.put(opponent, record);
        saveStats(stats);
        
        System.out.println("[STATS] vs " + opponent + ": " + record[0] + "W-" + record[1] + "L-" + record[2] + "D");
    }

    private Map<String, int[]> loadStats() {
        Map<String, int[]> stats = new HashMap<>();
        File file = new File(STATS_FILE);
        if (!file.exists()) return stats;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    stats.put(parts[0], new int[]{
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                    });
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading stats: " + e.getMessage());
        }
        return stats;
    }

    private void saveStats(Map<String, int[]> stats) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(STATS_FILE))) {
            for (Map.Entry<String, int[]> entry : stats.entrySet()) {
                int[] r = entry.getValue();
                writer.println(entry.getKey() + "," + r[0] + "," + r[1] + "," + r[2]);
            }
        } catch (IOException e) {
            System.err.println("Error saving stats: " + e.getMessage());
        }
    }
}
