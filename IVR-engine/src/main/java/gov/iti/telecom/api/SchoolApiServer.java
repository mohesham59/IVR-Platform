package gov.iti.telecom.api;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class SchoolApiServer {

    // IMPORTANT: Update these credentials if your local Postgres setup is different
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "password"; // Use your local password here

    public static void main(String[] args) throws Exception {
        // Initialize simple HTTP server on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/api/grades", new GradesHandler());
        server.createContext("/api/enroll", new EnrollHandler());
        
        server.setExecutor(null); // creates a default executor
        System.out.println("[SchoolApiServer] Starting API server on port 8080...");
        server.start();
    }

    // Helper for DB connections
    private static Connection getConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // Helper to parse query parameters (e.g., ?student_id=1234&sport=Soccer)
    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    // Handler for GET /api/grades
    static class GradesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
            String studentId = params.get("student_id");
            JsonObject responseJson = new JsonObject();

            if (studentId == null || studentId.isEmpty()) {
                responseJson.addProperty("error", "student_id is required");
                sendResponse(t, 400, responseJson.toString());
                return;
            }

            try (Connection conn = getConnection()) {
                // First check if student exists
                PreparedStatement checkStmt = conn.prepareStatement("SELECT name FROM students WHERE id = ?");
                checkStmt.setString(1, studentId);
                ResultSet rsCheck = checkStmt.executeQuery();
                
                if (!rsCheck.next()) {
                    responseJson.addProperty("status", "Student not found.");
                    sendResponse(t, 200, responseJson.toString());
                    return;
                }
                
                String studentName = rsCheck.getString("name");

                // Get grades
                PreparedStatement stmt = conn.prepareStatement("SELECT course_name, grade FROM grades WHERE student_id = ?");
                stmt.setString(1, studentId);
                ResultSet rs = stmt.executeQuery();
                
                StringBuilder gradesText = new StringBuilder();
                while (rs.next()) {
                    gradesText.append(rs.getString("course_name")).append(": ").append(rs.getString("grade")).append(", ");
                }
                
                if (gradesText.length() > 0) {
                    responseJson.addProperty("status", "Hello " + studentName + ". Your grades are: " + gradesText.toString());
                } else {
                    responseJson.addProperty("status", "Hello " + studentName + ". You have no grades recorded.");
                }

            } catch (Exception e) {
                e.printStackTrace();
                responseJson.addProperty("status", "Database error occurred.");
            }

            sendResponse(t, 200, responseJson.toString());
        }
    }

    // Handler for GET /api/enroll (Using GET to simplify IVR API tag, though POST is better practice)
    static class EnrollHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
            String studentId = params.get("student_id");
            String sport = params.get("sport");
            JsonObject responseJson = new JsonObject();

            if (studentId == null || sport == null) {
                responseJson.addProperty("error", "student_id and sport are required");
                sendResponse(t, 400, responseJson.toString());
                return;
            }

            try (Connection conn = getConnection()) {
                PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM students WHERE id = ?");
                checkStmt.setString(1, studentId);
                if (!checkStmt.executeQuery().next()) {
                    responseJson.addProperty("status", "Student not found.");
                    sendResponse(t, 200, responseJson.toString());
                    return;
                }

                PreparedStatement checkEnrollment = conn.prepareStatement("SELECT id FROM summer_camp WHERE student_id = ? AND sport_name = ?");
                checkEnrollment.setString(1, studentId);
                checkEnrollment.setString(2, sport);
                if (checkEnrollment.executeQuery().next()) {
                    responseJson.addProperty("status", "You are already enrolled in " + sport + ".");
                    sendResponse(t, 200, responseJson.toString());
                    return;
                }

                PreparedStatement stmt = conn.prepareStatement("INSERT INTO summer_camp (student_id, sport_name) VALUES (?, ?)");
                stmt.setString(1, studentId);
                stmt.setString(2, sport);
                stmt.executeUpdate();
                
                responseJson.addProperty("status", "You have successfully enrolled in " + sport + ".");
            } catch (Exception e) {
                e.printStackTrace();
                responseJson.addProperty("status", "Failed to enroll due to a database error.");
            }

            sendResponse(t, 200, responseJson.toString());
        }
    }

    private static void sendResponse(HttpExchange t, int statusCode, String response) throws IOException {
        t.getResponseHeaders().set("Content-Type", "application/json");
        t.sendResponseHeaders(statusCode, response.getBytes().length);
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
