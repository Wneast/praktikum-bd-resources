package com.example.bdsqltester.scenes;

import com.example.bdsqltester.HelloApplication;
import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.scenes.user.UserController;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.Parent;

import java.io.IOException;
import java.sql.*;

public class LoginController {

    @FXML
    private TextField passwordField;

    @FXML
    private ChoiceBox<String> selectRole;

    @FXML
    private TextField usernameField;

    private int getUserIdByUsername(String username) throws SQLException {
        try (Connection c = MainDataSource.getConnection()) {
            PreparedStatement stmt = c.prepareStatement("SELECT id FROM users WHERE username = ?");
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return -1;
    }


    boolean verifyCredentials(String username, String password, String role) throws SQLException {
        // Call the database to verify the credentials
        // This is insecure as this stores the password in plain text.
        // In a real application, you should hash the password and store it securely.

        // Get a connection to the database
        try (Connection c = MainDataSource.getConnection()) {
            // Create a prepared statement to prevent SQL injection
            PreparedStatement stmt = c.prepareStatement("SELECT * FROM users WHERE username = ? AND role = ?");
            stmt.setString(1, username);
            stmt.setString(2, role.toLowerCase());

            // Execute the query
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // User found, check the password
                String dbPassword = rs.getString("password");

                if (dbPassword.equals(password)) {
                    return true; // Credentials are valid
                }
            }
        }

        // If we reach here, the credentials are invalid
        return false;
    }

    @FXML
    void initialize() {
        selectRole.getItems().addAll("Admin", "User");
        selectRole.setValue("User");
    }

    @FXML
    void onLoginClick(ActionEvent event) {
        // Get the username and password from the text fields
        String username = usernameField.getText();
        String password = passwordField.getText();
        String role = selectRole.getValue();

        // Verify the credentials
        try {
            if (verifyCredentials(username, password, role)) {
                HelloApplication app = HelloApplication.getApplicationInstance();

                // Load the correct view based on the role
                if (role.equals("Admin")) {
                    // Load the admin view
                    app.getPrimaryStage().setTitle("Admin View");

                    // Load fxml and set the scene
                    FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("admin-view.fxml"));
                    Scene scene = new Scene(loader.load());
                    app.getPrimaryStage().setScene(scene);
                } else {
                    int userId = getUserIdByUsername(username);
                    if (userId == -1) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Login Error");
                        alert.setHeaderText("User Not Found");
                        alert.setContentText("Cannot find user ID in database.");
                        alert.showAndWait();
                        return;
                    }

                    // Load the user view
                    app.getPrimaryStage().setTitle("User View");

                    try {
                        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("user-view.fxml"));
                        Parent root = loader.load();

                        UserController userController = loader.getController();
                        userController.setUserId(userId);

                        Scene scene = new Scene(root);
                        app.getPrimaryStage().setScene(scene);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }



                }

            } else {
                // Show an error message
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Login Failed");
                alert.setHeaderText("Invalid Credentials");
                alert.setContentText("Please check your username and password.");
                alert.showAndWait();
            }
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Database Connection Failed");
            alert.setContentText("Could not connect to the database. Please try again later.");
            alert.showAndWait();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}