package com.example.bdsqltester.scenes.user;

import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.dtos.Assignment;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class UserController {
    @FXML
    private ListView<Assignment> assignmentList;
    @FXML private TextField idField;
    @FXML private TextField nameField;
    @FXML private TextArea instructionsField;
    @FXML private TextArea answerKeyField; // Contains the answer SQL query string
    @FXML private TextArea userQueryArea;  // Contains the user's SQL query string
    @FXML private Label gradeLabel;

    Connection connection = MainDataSource.getConnection();
    private int userId;

    public UserController() throws SQLException {
        // Constructor can remain as is, or ensure connection is handled robustly.
        // If MainDataSource.getConnection() can throw SQLException,
        // it might be better to initialize connection in initialize() or where it's first needed.
        // For simplicity, assuming it's handled or connection is non-null.
    }


    public void setUserId(int id) {
        this.userId = id;
    }

    @FXML
    public void initialize() {
        if (connection == null) {
            // Attempt to establish connection if null
            try {
                connection = MainDataSource.getConnection();
                if (connection == null) {
                    showAlert("Error", "Database connection could not be established.");
                    return;
                }
            } catch (SQLException e) {
                showAlert("Error", "Database connection failed: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }
        loadAssignments();
        assignmentList.setOnMouseClicked(this::onAssignmentSelected);
    }

    private void loadAssignments() {
        ObservableList<Assignment> assignments = FXCollections.observableArrayList();
        // Ensure connection is available before proceeding
        if (connection == null) {
            showAlert("Error", "Database connection is not available for loading assignments.");
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM assignments ORDER BY id");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                assignments.add(new Assignment(rs));
            }
            assignmentList.setItems(assignments);

        } catch (SQLException e) {
            showAlert("Error", "Failed to load assignments: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void onAssignmentSelected(MouseEvent event) {
        Assignment selected = assignmentList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        idField.setText(String.valueOf(selected.id));
        nameField.setText(selected.name);
        instructionsField.setText(selected.instructions);
        answerKeyField.setText(selected.answerKey); // This sets the answer query string
        userQueryArea.clear(); // Clear previous user query for a new assignment
        gradeLabel.setText("Score: -"); // Reset grade label
        loadUserGrade((int) selected.id);
    }


    private void loadUserGrade(int assignmentId) {
        if (connection == null) {
            showAlert("Error", "Database connection is not available for loading grade.");
            gradeLabel.setText("Score: - (DB Error)");
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement("SELECT grade FROM grades WHERE assignment_id = ? AND user_id = ?")) {
            stmt.setInt(1, assignmentId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    gradeLabel.setText("Score: " + rs.getInt("grade"));
                } else {
                    gradeLabel.setText("Score: -");
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to load grade: " + e.getMessage());
            gradeLabel.setText("Score: - (Error)");
            e.printStackTrace();
        }
    }

    @FXML
    void onTestButtonClick() {
        String query = userQueryArea.getText();
        if (query.isBlank()) {
            showAlert("Test Query", "Query area is empty.");
            return;
        }
        if (connection == null) {
            showAlert("Error", "Database connection is not available for testing query.");
            return;
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            String resultString = resultSetToString(rs);
            showAlert("Query Output", resultString.isEmpty() ? "(No results)" : resultString);
        } catch (SQLException e) {
            showAlert("Query Error", e.getMessage());
        }
    }

    @FXML
    void onSubmitClick() {
        if (idField.getText().isEmpty()) {
            showAlert("Error", "Please select an assignment first.");
            return;
        }
        if (connection == null) {
            showAlert("Error", "Database connection is not available for submission.");
            return;
        }

        int assignmentId;
        try {
            assignmentId = Integer.parseInt(idField.getText());
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid assignment ID selected.");
            return;
        }

        String userQueryString = userQueryArea.getText();
        String answerQueryString = answerKeyField.getText(); // This is the SQL from the assignment's answer key

        if (userQueryString.isBlank()) {
            showAlert("Submission Error", "Your query is empty.");
            return;
        }
        if (answerQueryString.isBlank()) {
            showAlert("System Error", "The answer key for this assignment is empty. Please contact an administrator.");
            return;
        }

        int calculatedGrade = 0; // Default grade

        try {
            String userResultString;
            // Attempt to execute user's query
            try (Statement userStmt = connection.createStatement();
                 ResultSet userRs = userStmt.executeQuery(userQueryString)) {
                userResultString = resultSetToString(userRs);
            } catch (SQLException e) {
                // User's query failed to execute
                showAlert("Query Execution Error", "Your query failed: " + e.getMessage());
                // Grade remains 0, proceed to save this 0 score.
                updateOrInsertGrade(assignmentId, userId, calculatedGrade); // Save grade 0
                gradeLabel.setText("Score: " + calculatedGrade);
                showAlert("Submission Result", "Your query produced an error. Score: " + calculatedGrade);
                return; // Exit after handling user query error
            }

            // User's query was successful, now execute answer key's query
            String answerResultString;
            try (Statement answerStmt = connection.createStatement();
                 ResultSet answerRs = answerStmt.executeQuery(answerQueryString)) {
                answerResultString = resultSetToString(answerRs);
            } catch (SQLException e) {
                // This is an error with the assignment setup (answer key query is invalid)
                showAlert("System Error", "The answer key query is invalid. Please contact an administrator. Error: " + e.getMessage());
                return; // Don't proceed with grading if answer key is broken
            }

            // Both queries executed successfully. Now, determine the grade based on the new logic:
            if (userQueryString.trim().equalsIgnoreCase(answerQueryString.trim())) {
                calculatedGrade = 100; // Exact textual match of the SQL queries
            } else if (userResultString.equals(answerResultString)) {
                // SQL queries differ, but their results are identical (e.g., SELECT 999 vs SELECT 1000-1)
                calculatedGrade = 50;
            } else if (sortLines(userResultString).equals(sortLines(answerResultString))) {
                // SQL queries differ, results differ in order, but match when lines are sorted
                // This maintains the original partial credit if the above conditions aren't met.
                // You could assign a different score (e.g., 25 or 40) if you want to distinguish this tier.
                calculatedGrade = 50;
            } else {
                calculatedGrade = 0; // No match by any criteria
            }

            // Update database with the calculated grade
            updateOrInsertGrade(assignmentId, userId, calculatedGrade);

            gradeLabel.setText("Score: " + calculatedGrade);
            showAlert("Submission Result", "You received a score of: " + calculatedGrade);

        } catch (SQLException e) { // Catches SQLExceptions from updateOrInsertGrade or other DB ops
            showAlert("Database Error", "An error occurred during submission process: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Helper method to update an existing grade record or insert a new one.
     * Only updates if the new grade is higher than the previous grade.
     */
    private void updateOrInsertGrade(int assignmentId, int userId, int grade) throws SQLException {
        // Ensure connection is available
        if (connection == null) {
            throw new SQLException("Database connection is not available for updating grade.");
        }

        String selectSql = "SELECT grade FROM grades WHERE assignment_id = ? AND user_id = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(selectSql)) {
            checkStmt.setInt(1, assignmentId);
            checkStmt.setInt(2, userId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int prevGrade = rs.getInt("grade");
                    if (grade > prevGrade) {
                        String updateSql = "UPDATE grades SET grade = ? WHERE assignment_id = ? AND user_id = ?";
                        try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, grade);
                            updateStmt.setInt(2, assignmentId);
                            updateStmt.setInt(3, userId);
                            updateStmt.executeUpdate();
                        }
                    }
                    // If grade is not higher, no update is performed, previous higher score stands.
                } else {
                    String insertSql = "INSERT INTO grades (assignment_id, user_id, grade) VALUES (?, ?, ?)";
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, assignmentId);
                        insertStmt.setInt(2, userId);
                        insertStmt.setInt(3, grade);
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
    }


    private String resultSetToString(ResultSet rs) throws SQLException {
        StringBuilder sb = new StringBuilder();
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();
        boolean firstRow = true;
        while (rs.next()) {
            if (!firstRow) {
                sb.append("\n"); // Add newline before subsequent rows
            }
            for (int i = 1; i <= colCount; i++) {
                sb.append(rs.getString(i));
                if (i < colCount) {
                    sb.append("\t"); // Add tab between columns
                }
            }
            firstRow = false;
        }
        // No .trim() here to preserve trailing newlines if they are significant for comparison,
        // or add .trim() if leading/trailing whitespace on the whole result block is not desired.
        // The original had .trim(), so let's be consistent if that was the intent for the overall block.
        return sb.toString().trim(); // Trim the final string
    }

    private String sortLines(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>(List.of(input.split("\n")));
        lines.sort(String::compareTo);
        return String.join("\n", lines);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null); // No header text
        alert.setContentText(message);
        alert.showAndWait();
    }
}