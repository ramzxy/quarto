package Client;



import Client.views.ClientView;
import ai.Strategy;

import java.io.IOException;

/**
 * GameClient specialized for AI players.
 * Overrides player creation to use ComputerPlayer.
 */
public class AIGameClient extends GameClient {
    private final Strategy strategy;

    public AIGameClient(String host, int port, ClientView view, Strategy strategy) throws IOException {
        super(host, port, view);
        this.strategy = strategy;
    }

    @Override
    public void receiveHello(String serverDescription) {
        System.out.println("Connected to server: " + serverDescription);
        
        // Use the view to get the username (which AIClientApp configured)
        String username = view.promptUsername();
        
        // Create ComputerPlayer instead of HumanPlayer
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


}
