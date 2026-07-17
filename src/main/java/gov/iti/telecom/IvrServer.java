package gov.iti.telecom;

import org.asteriskjava.fastagi.*;

public class IvrServer implements AgiScript {

    private ScenarioEngine engine;

    @Override
    public void service(AgiRequest request, AgiChannel channel) throws AgiException {
        // 1. Get business ID from channel variable (set in dialplan)
        String businessId = request.getParameter("business_id");
        String scenarioId = request.getParameter("scenario_id");

        // 2. Load scenario from database/cache
        Scenario scenario = engine.loadScenario(businessId, scenarioId);

        // 3. Execute scenario starting from entry node
        engine.execute(scenario, channel);
    }

    public static void main(String[] args) throws Exception {
        DefaultAgiServer server = new DefaultAgiServer();
        server.setPort(4573); // Default FastAGI port
        server.setMappingStrategy(new MappingStrategy() {
            public AgiScript determineScript(AgiRequest request, AgiChannel channel) {
                return new IvrServer();
            }
        });
        server.startup();
    }
}