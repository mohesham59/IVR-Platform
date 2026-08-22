package gov.iti.telecom;

import java.util.HashMap;
import java.util.Map;

/**
 * VxmlSession — represents a single IVR session execution context.
 *
 * <h2>HOW IT WORKS</h2>
 * <p>Each phone call creates one VxmlSession. The session holds:</p>
 * <ul>
 *   <li>Session ID (unique identifier)</li>
 *   <li>VXML file being executed</li>
 *   <li>Current execution state (RUNNING, COMPLETED, ERROR)</li>
 *   <li>Session variables (collected form data, user inputs)</li>
 *   <li>Timing information (start time, duration)</li>
 *   <li>Error information</li>
 * </ul>
 *
 * <h2>USAGE EXAMPLE</h2>
 * <pre>{@code
 * // Create a new session
 * VxmlSession session = new VxmlSession("call-12345", "hello.vxml");
 *
 * // Set execution state
 * session.setState(VxmlSession.SessionState.RUNNING);
 *
 * // Store variables collected from user
 * session.setVariable("user_choice", "1");
 * session.setVariable("party_size", "4");
 *
 * // Retrieve variable
 * String choice = (String) session.getVariable("user_choice");
 *
 * // On completion
 * session.setState(VxmlSession.SessionState.COMPLETED);
 *
 * // Check results
 * if (session.getState() == VxmlSession.SessionState.COMPLETED) {
 *     System.out.println("Collected: " + session.getAllVariables());
 * }
 * }</pre>
 *
 * <h2>SESSION STATES</h2>
 * <ul>
 *   <li><b>RUNNING</b>: Session is actively executing VXML</li>
 *   <li><b>COMPLETED</b>: Session finished successfully</li>
 *   <li><b>ERROR</b>: Session encountered an error and stopped</li>
 *   <li><b>TIMEOUT</b>: Session timed out (no user interaction)</li>
 * </ul>
 *
 * <h2>THREAD SAFETY</h2>
 * <p>VxmlSession is designed for single-threaded access per session.
 * Multiple threads should NOT share the same session without synchronization.</p>
 *
 * @author IVR Platform Team
 * @version 1.0
 * @see VxmlScenarioEngine
 */
public class VxmlSession {

    /**
     * Enumeration of possible session states.
     */
    public enum SessionState {
        RUNNING("Running"),
        COMPLETED("Completed"),
        ERROR("Error"),
        TIMEOUT("Timeout"),
        IDLE("Idle");

        private final String displayName;

        SessionState(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Session identification
    private final String sessionId;
    private final String vxmlName;
    private final long createdAt;

    // Execution state
    private SessionState state;
    private String lastError;

    // Session data
    private final Map<String, Object> variables;
    private AnalyticsTracker tracker;

    /**
     * Creates a new VXML session.
     *
     * @param sessionId unique identifier for this session (e.g., "call-12345")
     * @param vxmlName the VXML file being executed (e.g., "hello.vxml")
     * @throws NullPointerException if sessionId or vxmlName is null
     */
    public VxmlSession(String sessionId, String vxmlName) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NullPointerException("Session ID cannot be null or empty");
        }
        if (vxmlName == null || vxmlName.isEmpty()) {
            throw new NullPointerException("VXML name cannot be null or empty");
        }

        this.sessionId = sessionId;
        this.vxmlName = vxmlName;
        this.createdAt = System.currentTimeMillis();
        this.state = SessionState.IDLE;
        this.variables = new HashMap<>();

        System.out.println("[VxmlSession] Created session: " + sessionId + " for VXML: " + vxmlName);
    }

    /**
     * Gets the unique session identifier.
     *
     * @return session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Gets the VXML file name being executed.
     *
     * @return VXML name (e.g., "hello.vxml")
     */
    public String getVxmlName() {
        return vxmlName;
    }

    /**
     * Gets the current session state.
     *
     * @return current SessionState
     */
    public SessionState getState() {
        return state;
    }

    /**
     * Sets the current session state.
     *
     * @param state the new SessionState
     */
    public void setState(SessionState state) {
        if (state == null) {
            throw new NullPointerException("Session state cannot be null");
        }
        System.out.println("[VxmlSession] " + sessionId + " state changed to: " + state.getDisplayName());
        this.state = state;
    }

    /**
     * Gets the last error message (if any).
     *
     * @return error message, or null if no error
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Sets the last error message.
     *
     * @param error error description
     */
    public void setLastError(String error) {
        this.lastError = error;
        System.err.println("[VxmlSession] " + sessionId + " error: " + error);
    }

    /**
     * Stores a variable in the session context.
     *
     * <p>Variables are collected from user interactions (DTMF input, form submissions, etc.).</p>
     *
     * @param key variable name
     * @param value variable value (can be String, Integer, Boolean, etc.)
     */
    public void setVariable(String key, Object value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Variable key cannot be null or empty");
        }
        variables.put(key, value);
        System.out.println("[VxmlSession] " + sessionId + " set variable: " + key + " = " + value);
    }

    /**
     * Retrieves a variable from the session context.
     *
     * @param key variable name
     * @return the variable value, or null if not set
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * Gets a variable as a String.
     *
     * @param key variable name
     * @param defaultValue default value if not set
     * @return variable value as String, or defaultValue if not found
     */
    public String getVariableAsString(String key, String defaultValue) {
        Object value = variables.get(key);
        return (value != null) ? value.toString() : defaultValue;
    }

    /**
     * Gets a variable as an Integer.
     *
     * @param key variable name
     * @param defaultValue default value if not set or not an integer
     * @return variable value as Integer, or defaultValue if not found/invalid
     */
    public int getVariableAsInt(String key, int defaultValue) {
        Object value = variables.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Checks if a variable exists.
     *
     * @param key variable name
     * @return true if variable is set, false otherwise
     */
    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    /**
     * Gets all variables as a map.
     *
     * @return immutable copy of the variables map
     */
    public Map<String, Object> getAllVariables() {
        return new HashMap<>(variables);
    }

    /**
     * Clears all variables.
     */
    public void clearVariables() {
        variables.clear();
        System.out.println("[VxmlSession] " + sessionId + " cleared all variables");
    }

    public AnalyticsTracker getTracker() {
        return tracker;
    }

    public void setTracker(AnalyticsTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Gets the session duration in milliseconds.
     *
     * @return elapsed time since session creation
     */
    public long getDurationMillis() {
        return System.currentTimeMillis() - createdAt;
    }

    /**
     * Gets the session duration in seconds.
     *
     * @return elapsed time since session creation (rounded down)
     */
    public long getDurationSeconds() {
        return getDurationMillis() / 1000;
    }

    /**
     * Gets the session creation timestamp.
     *
     * @return milliseconds since epoch
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Checks if the session is still active.
     *
     * @return true if state is RUNNING
     */
    public boolean isActive() {
        return state == SessionState.RUNNING;
    }

    /**
     * Checks if the session completed successfully.
     *
     * @return true if state is COMPLETED
     */
    public boolean isCompleted() {
        return state == SessionState.COMPLETED;
    }

    /**
     * Checks if the session encountered an error.
     *
     * @return true if state is ERROR or TIMEOUT
     */
    public boolean hasError() {
        return state == SessionState.ERROR || state == SessionState.TIMEOUT;
    }

    @Override
    public String toString() {
        return "VxmlSession{" +
                "sessionId='" + sessionId + '\'' +
                ", vxmlName='" + vxmlName + '\'' +
                ", state=" + state.getDisplayName() +
                ", durationSecs=" + getDurationSeconds() +
                ", variableCount=" + variables.size() +
                ", hasError=" + hasError() +
                '}';
    }
}
