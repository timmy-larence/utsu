package com.utsusynth.utsu;

import java.io.File;
import java.io.InputStream;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.utsusynth.utsu.controller.UtsuController;
import com.utsusynth.utsu.model.ModelModule;
import com.utsusynth.utsu.view.ViewModule;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * UTAU-ish Thingy with Some Updates (UTSU)
 */
public class UtsuApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Warn users if they're using the wrong working directory.
        if (!new File("./assets").exists()) {
            System.err.println("UTSU assets directory could not be found in the current working directory.");
            System.err.println("These assets are required for UTSU to work properly.");
            System.err.println("The current working directory is: " + System.getProperty("user.dir"));
            System.err.println("If this is not your UTSU install directory, please move there.");
            System.err.println("Otherwise your installation is broken. In that case, please reinstall UTSU.");
            primaryStage.show();
            primaryStage.close();
            return;
        }

        // Set up Guice.
        Injector injector = Guice.createInjector(new UtsuModule(), new ModelModule(), new ViewModule());
        FXMLLoader loader = injector.getInstance(FXMLLoader.class);

        // Construct scene.
        InputStream fxml = getClass().getResourceAsStream("/fxml/UtsuScene.fxml");
        BorderPane pane = loader.load(fxml);
        Scene scene = new Scene(pane);
        scene.getStylesheets().add("/css/piano_roll.css");

        // Set the stage.
        primaryStage.setScene(scene);
        primaryStage.setTitle("Utsu");
        primaryStage.show();

        UtsuController controller = (UtsuController) loader.getController();

        // Set up an event that runs every time a non-text-input key is pressed.
        primaryStage.addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (!(keyEvent.getTarget() instanceof TextInputControl)
                    || new KeyCodeCombination(KeyCode.TAB).match(keyEvent)) {
                if (controller.onKeyPressed(keyEvent)) {
                    keyEvent.consume();
                }
            }
        });

        // Set up an event that runs when the program is closed.
        primaryStage.setOnCloseRequest(windowEvent -> {
            if (!controller.onCloseWindow()) {
                windowEvent.consume();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
