package com.example.bdsqltester.scenes.admin;

import com.example.bdsqltester.datasources.GradingDataSource;
import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.dtos.Assignment;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;

public class AdminController {

    @FXML
    private TextArea answerKeyField;

    @FXML
    private ListView<Assignment> assignmentList = new ListView<>();

    @FXML
    private TextField idField;

    @FXML
    private TextArea instructionsField;

    @FXML
    private TextField nameField;

    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();

    @FXML
    void initialize() {
        // Set idField to read-only
        idField.setEditable(false);
        idField.setMouseTransparent(true);
        idField.setFocusTraversable(false);

        // Populate the ListView with assignment names
        refreshAssignmentList();

        assignmentList.setCellFactory(param -> new ListCell<Assignment>() {
            @Override
            protected void updateItem(Assignment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name);
                }
            }

            // Bind the onAssignmentSelected method to the ListView
            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (selected) {
                    onAssignmentSelected(getItem());
                }
            }
        });
    }

    void refreshAssignmentList() {
        assignments.clear();
        try (Connection c = MainDataSource.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM assignments")) {

            while (rs.next()) {
                assignments.add(new Assignment(rs));
            }
        } catch (SQLException e) {
            showErrorAlert("Database Error", "Could not refresh assignment list.", e.toString());
            e.printStackTrace();
        }
        assignmentList.setItems(assignments);

        try {
            if (!idField.getText().isEmpty()) {
                long id = Long.parseLong(idField.getText());
                for (Assignment assignment : assignments) {
                    if (assignment.id == id) {
                        assignmentList.getSelectionModel().select(assignment);
                        break;
                    }
                }
            }
        } catch (NumberFormatException e) {
            // Ignore, idField might be empty or contain invalid text temporarily
        }
    }

    void onAssignmentSelected(Assignment assignment) {
        if (assignment == null) return;
        idField.setText(String.valueOf(assignment.id));
        nameField.setText(assignment.name);
        instructionsField.setText(assignment.instructions);
        answerKeyField.setText(assignment.answerKey);
    }

    @FXML
    void onNewAssignmentClick(ActionEvent event) {
        assignmentList.getSelectionModel().clearSelection();
        idField.clear();
        nameField.clear();
        instructionsField.clear();
        answerKeyField.clear();
        nameField.requestFocus(); // Set focus to name field for new assignment
    }

    @FXML
    void onSaveClick(ActionEvent event) {
        String name = nameField.getText();
        String instructions = instructionsField.getText();
        String answerKey = answerKeyField.getText();

        if (name == null || name.trim().isEmpty()) {
            showErrorAlert("Validation Error", "Name cannot be empty.", "Please provide a name for the assignment.");
            return;
        }

        Connection conn = null;
        try {
            conn = MainDataSource.getConnection();
            if (idField.getText().isEmpty()) {
                // Insert new assignment
                String insertQuery = "INSERT INTO assignments (name, instructions, answer_key) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, name);
                    stmt.setString(2, instructions);
                    stmt.setString(3, answerKey);
                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            idField.setText(String.valueOf(rs.getLong(1)));
                        }
                    }
                }
            } else {
                // Update existing assignment
                String updateQuery = "UPDATE assignments SET name = ?, instructions = ?, answer_key = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setString(1, name);
                    stmt.setString(2, instructions);
                    stmt.setString(3, answerKey);
                    stmt.setLong(4, Long.parseLong(idField.getText()));
                    stmt.executeUpdate();
                }
            }
            refreshAssignmentList(); // Refresh to show changes and re-select
            showInfoAlert("Success", "Assignment Saved", "The assignment has been successfully saved.");

        } catch (SQLException e) {
            showErrorAlert("Database Error", "Failed to save assignment.", e.toString());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            showErrorAlert("Input Error", "Invalid ID.", "The assignment ID is not a valid number.");
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @FXML
    void onShowGradesClick(ActionEvent event) {
        if (idField.getText().isEmpty()) {
            showErrorAlert("Error", "No Assignment Selected", "Please select an assignment to view grades.");
            return;
        }

        long assignmentId;
        try {
            assignmentId = Long.parseLong(idField.getText());
        } catch (NumberFormatException e) {
            showErrorAlert("Error", "Invalid Assignment ID", "The selected assignment ID is not valid.");
            return;
        }


        TableView<ArrayList<String>> tableView = new TableView<>();
        ObservableList<ArrayList<String>> data = FXCollections.observableArrayList();

        String sql = "SELECT u.username, g.grade FROM grades g JOIN users u ON g.user_id = u.id WHERE g.assignment_id = ?";

        try (Connection conn = MainDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, assignmentId);
            ResultSet rs = stmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                final int columnIndex = i - 1;
                String headerText = metaData.getColumnLabel(i);
                TableColumn<ArrayList<String>, String> column = new TableColumn<>(headerText);
                column.setCellValueFactory(cellData -> {
                    ArrayList<String> rowData = cellData.getValue();
                    return new SimpleStringProperty(rowData != null && columnIndex < rowData.size() ? rowData.get(columnIndex) : "");
                });
                column.setPrefWidth(150);
                tableView.getColumns().add(column);
            }

            while (rs.next()) {
                ArrayList<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getString(i) != null ? rs.getString(i) : "");
                }
                data.add(row);
            }

            if (data.isEmpty()) {
                showInfoAlert("No Grades", null, "There are no grades submitted for this assignment yet.");
                return;
            }

            tableView.setItems(data);
            StackPane root = new StackPane(tableView);
            Scene scene = new Scene(root, 400, 300);
            Stage stage = new Stage();
            stage.setTitle("Grades for Assignment: " + nameField.getText() + " (ID: " + assignmentId + ")");
            stage.setScene(scene);
            stage.show();

        } catch (SQLException e) {
            e.printStackTrace();
            showErrorAlert("Database Error", "Could not retrieve grades.", e.getMessage());
        }
    }


    @FXML
    void onTestButtonClick(ActionEvent event) {
        String query = answerKeyField.getText();
        if (query == null || query.trim().isEmpty()) {
            showInfoAlert("No Query", null, "The answer key field is empty. Please enter a SQL query to test.");
            return;
        }

        Stage stage = new Stage();
        stage.setTitle("Query Results");
        TableView<ArrayList<String>> tableView = new TableView<>();
        ObservableList<ArrayList<String>> data = FXCollections.observableArrayList();

        try (Connection conn = GradingDataSource.getConnection(); // Assuming GradingDataSource is for testing queries
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            if (columnCount == 0 && !rs.next()){ // Check if the query might be an UPDATE/INSERT/DELETE
                // Or if it's a SELECT that genuinely returns no columns (though rare)
                if (stmt.getUpdateCount() != -1) { // DML command
                    showInfoAlert("Query Executed", "DML Command Result", "The command executed successfully. Rows affected: " + stmt.getUpdateCount());
                } else { // SELECT that returned no columns or no rows
                    showInfoAlert("Query Results", null, "The query executed successfully but returned no data or columns.");
                }
                return;
            }


            for (int i = 1; i <= columnCount; i++) {
                final int columnIndex = i - 1;
                String headerText = metaData.getColumnLabel(i);
                TableColumn<ArrayList<String>, String> column = new TableColumn<>(headerText);
                column.setCellValueFactory(cellData -> {
                    ArrayList<String> rowData = cellData.getValue();
                    return new SimpleStringProperty(rowData != null && columnIndex < rowData.size() ? rowData.get(columnIndex) : "");
                });
                column.setPrefWidth(120);
                tableView.getColumns().add(column);
            }

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                ArrayList<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getString(i) != null ? rs.getString(i) : "");
                }
                data.add(row);
            }

            if (!hasRows && columnCount > 0) { // Has columns but no data rows
                showInfoAlert("Query Results", null, "The query executed successfully and returned columns, but no data rows.");
                // Still show the table with headers
            } else if (!hasRows && columnCount == 0) { // Should have been caught above, but as a fallback
                showInfoAlert("Query Results", null, "The query executed successfully but returned no data or columns.");
                return;
            }


            tableView.setItems(data);
            StackPane root = new StackPane(tableView);
            Scene scene = new Scene(root, 800, 600);
            stage.setScene(scene);
            stage.show();

        } catch (SQLException e) {
            e.printStackTrace();
            showErrorAlert("Database Error", "Failed to execute query or retrieve results.", "SQL Error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert("Error", "An unexpected error occurred.", e.getMessage());
        }
    }


    @FXML
    void onDeleteAssignmentClick(ActionEvent event) {
        if (idField.getText().isEmpty()) {
            showErrorAlert("Error", "No Assignment Selected", "Please select an assignment to delete.");
            return;
        }

        long assignmentId;
        try {
            assignmentId = Long.parseLong(idField.getText());
        } catch (NumberFormatException e) {
            showErrorAlert("Error", "Invalid Assignment ID", "The assignment ID is not valid for deletion.");
            return;
        }

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Confirm Deletion");
        confirmationAlert.setHeaderText("Delete Assignment: " + nameField.getText() + "?");
        confirmationAlert.setContentText("This action will also delete all associated grades and cannot be undone. Are you sure?");

        confirmationAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Connection conn = null;
                try {
                    conn = MainDataSource.getConnection();
                    conn.setAutoCommit(false); // Start transaction

                    // 1. Delete associated grades first (assuming 'grades' table and 'assignment_id' foreign key)
                    //    Your onShowGradesClick confirms these table/column names.
                    String deleteGradesQuery = "DELETE FROM grades WHERE assignment_id = ?";
                    try (PreparedStatement stmtGrades = conn.prepareStatement(deleteGradesQuery)) {
                        stmtGrades.setLong(1, assignmentId);
                        stmtGrades.executeUpdate();
                        // You could log stmtGrades.getUpdateCount() to see how many grades were deleted.
                    }

                    // 2. Delete the assignment itself
                    String deleteAssignmentQuery = "DELETE FROM assignments WHERE id = ?";
                    try (PreparedStatement stmtAssignment = conn.prepareStatement(deleteAssignmentQuery)) {
                        stmtAssignment.setLong(1, assignmentId);
                        int rowsAffected = stmtAssignment.executeUpdate();

                        if (rowsAffected > 0) {
                            conn.commit(); // Commit transaction if assignment deletion was successful

                            refreshAssignmentList();
                            idField.clear();
                            nameField.clear();
                            instructionsField.clear();
                            answerKeyField.clear();
                            showInfoAlert("Success", "Assignment Deleted", "The assignment and its associated grades have been successfully deleted.");
                        } else {
                            conn.rollback(); // Rollback if assignment was not found (should not happen if selected)
                            showErrorAlert("Deletion Failed", "Assignment Not Found", "The selected assignment could not be found in the database for deletion. It might have been deleted by another process.");
                            refreshAssignmentList(); // Refresh in case it was deleted elsewhere
                        }
                    }
                } catch (SQLException e) {
                    if (conn != null) {
                        try {
                            conn.rollback(); // Rollback on any SQL error
                        } catch (SQLException ex) {
                            System.err.println("Error during transaction rollback: " + ex.getMessage());
                            ex.printStackTrace(); // Log rollback error
                        }
                    }
                    e.printStackTrace();
                    showErrorAlert("Database Error", "Failed to delete assignment.",
                            "An SQL error occurred: " + e.getMessage() +
                                    "\nThe transaction was rolled back.");
                } finally {
                    if (conn != null) {
                        try {
                            conn.setAutoCommit(true); // Reset auto-commit behavior (important if connection is pooled)
                            conn.close();
                        } catch (SQLException e) {
                            e.printStackTrace(); // Log closing error
                        }
                    }
                }
            }
        });
    }

    // Helper methods for alerts
    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}