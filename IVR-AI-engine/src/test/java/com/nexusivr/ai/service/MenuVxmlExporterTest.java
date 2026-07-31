package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Menu VXML Exporter Tests")
class MenuVxmlExporterTest {

    @Test
    @DisplayName("Should export MENU node with exactly 1 prompt, N non-duplicate choices, and no narration in choice body")
    void testExportMenuNodeCleanVxml() {
        FlowModel model = new FlowModel();
        model.setName("Billing Menu Export Test");

        FlowNode menuNode = new FlowNode("billing", FlowNodeType.MENU, "Billing Menu");
        String narration = "Billing support. Press 1 to report an outage, Press 2 for technical support, or Press 3 to change your plan.";
        menuNode.setPrompt(new FlowPrompt(narration));

        FlowMenu menu = new FlowMenu();
        menu.addChoice(new FlowChoice("key1", "Billing support. Press 1 to report an outage, Press 2 for technical support, or Press 3 to change your plan.", "outage"));
        menu.getChoices().get(0).setDtmf("1");

        menu.addChoice(new FlowChoice("key1", "Option key1", "outage"));
        menu.getChoices().get(1).setAccept("digits 1");

        menu.addChoice(new FlowChoice("key2", "Option key2", "tech_support"));
        menu.getChoices().get(2).setAccept("digits 2");

        menu.addChoice(new FlowChoice("key3", "Option key3", "plan_change"));
        menu.getChoices().get(3).setAccept("digits 3");

        menuNode.setMenu(menu);
        model.addNode(menuNode);

        FlowNode outageNode = new FlowNode("outage", FlowNodeType.PROMPT, "Outage Info");
        FlowNode techNode = new FlowNode("tech_support", FlowNodeType.PROMPT, "Tech Support");
        FlowNode planNode = new FlowNode("plan_change", FlowNodeType.PROMPT, "Plan Change");
        model.addNode(outageNode);
        model.addNode(techNode);
        model.addNode(planNode);

        ModelToVxmlExporter exporter = new ModelToVxmlExporter();
        String vxml = exporter.export(model);

        assertNotNull(vxml);

        // (a) Exactly one <prompt> inside the <menu> block
        int promptCount = countMatches(vxml, "<prompt>");
        // Total prompts in document: 1 for menu + 3 for prompt nodes = 4
        // Inside menu block specifically: exactly 1
        String menuBlock = extractSection(vxml, "<menu>", "</menu>");
        assertNotNull(menuBlock, "<menu> block must exist");
        assertEquals(1, countMatches(menuBlock, "<prompt>"), "Menu block must contain exactly one <prompt>");
        assertTrue(menuBlock.contains("<prompt>" + narration + "</prompt>"), "Menu prompt must contain full narration text");

        // (b) Exactly 3 unique choices (N=3) inside the <menu> block
        int choiceCount = countMatches(menuBlock, "<choice");
        assertEquals(3, choiceCount, "Menu block must contain exactly N=3 <choice> elements");

        // (c) No two choices share the same dtmf/digit value
        Matcher matcher = Pattern.compile("dtmf=\"([^\"]+)\"").matcher(menuBlock);
        Set<String> dtmfs = new HashSet<>();
        while (matcher.find()) {
            String dtmf = matcher.group(1);
            assertFalse(dtmfs.contains(dtmf), "Duplicate DTMF digit found in menu export: " + dtmf);
            dtmfs.add(dtmf);
        }
        assertEquals(3, dtmfs.size(), "Must have 3 distinct DTMF digits (1, 2, 3)");

        // (d) No <choice> contains the full menu narration text or placeholder 'Option keyN'
        Matcher choiceBodyMatcher = Pattern.compile("<choice[^>]*>(.*?)</choice>", Pattern.DOTALL).matcher(menuBlock);
        while (choiceBodyMatcher.find()) {
            String body = choiceBodyMatcher.group(1);
            assertFalse(body.contains("Billing support"), "<choice> element must not contain full menu narration text");
        }
        assertFalse(menuBlock.contains("Option key"), "<choice> element must not contain 'Option keyN' placeholder text");
        assertFalse(menuBlock.contains("accept="), "Must not output redundant accept= attribute when dtmf= is present");

        // Choices should be self-closing <choice dtmf="N" next="#target"/>
        assertTrue(menuBlock.contains("<choice dtmf=\"1\" next=\"#outage\"/>"));
        assertTrue(menuBlock.contains("<choice dtmf=\"2\" next=\"#tech_support\"/>"));
        assertTrue(menuBlock.contains("<choice dtmf=\"3\" next=\"#plan_change\"/>"));
    }

    private int countMatches(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private String extractSection(String text, String startTag, String endTag) {
        int start = text.indexOf(startTag);
        int end = text.indexOf(endTag);
        if (start != -1 && end != -1) {
            return text.substring(start, end + endTag.length());
        }
        return null;
    }
}
