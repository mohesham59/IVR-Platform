package gov.iti.telecom;

import org.asteriskjava.fastagi.AgiChannel;

public class ScenarioEngine {

    public void execute(Scenario scenario, AgiChannel channel) throws Exception {
        String currentNodeId = scenario.getEntryNode();

        while (currentNodeId != null && !currentNodeId.equals("end")) {
            Node node = scenario.getNode(currentNodeId);
            currentNodeId = executeNode(node, channel, scenario);
        }
    }

    private String executeNode(Node node, AgiChannel channel, Scenario scenario)
            throws Exception {
        switch (node.getType()) {
            case "play":
                channel.streamFile(node.getAudio());
                return node.getNext();

            case "menu":
                String digits = channel.getData(
                        node.getPrompt(),
                        node.getTimeout(),
                        node.getMaxDigits());
                return node.getChoice(digits); // Returns next node or invalid

            case "form":
                for (Field field : node.getFields()) {
                    String input = channel.getData(
                            field.getPrompt(),
                            5000,
                            field.getLength());
                    // Store in channel variables or database
                    channel.setVariable(field.getVar(), input);
                }
                return node.getNext();

            case "transfer":
                channel.exec("Dial", node.getDestination());
                return node.getNext();

            case "database":
                // Call external API or query DB
                return node.getNext();

            default:
                return "end";
        }
    }
}