package Client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Launcher for the JavaFX GUI.
 */
public class GuiLauncher extends Application {

    private static GuiView guiView;

    @Override
    public void start(Stage primaryStage) {
        // Create the view, which sets up the initial scene (Login)
        guiView = new GuiView(primaryStage);
        
        // Ensure we disconnect on close
        primaryStage.setOnCloseRequest(e -> {
            // We can access client through guiView if we exposed it, or just kill app
            Platform.exit();
            System.exit(0);
        });
    }

    /**
     * Launch the JavaFX application.
     * @param args command line arguments
     */
    public static void run(String[] args) {
        launch(args);
    }
}
