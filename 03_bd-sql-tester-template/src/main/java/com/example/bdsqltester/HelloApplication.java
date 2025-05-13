package com.example.bdsqltester;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class
HelloApplication extends Application {


    private static HelloApplication applicationInstance;
    private Stage primaryStage;
    public static HelloApplication getApplicationInstance () { return applicationInstance; }
    public Stage getPrimaryStage () { return primaryStage; }

    @Override
    public void start(Stage stage) throws IOException {
        HelloApplication.applicationInstance = this;

        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        primaryStage = stage;

        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}