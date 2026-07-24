package gov.iti.telecom;

import gov.iti.telecom.vxml.*;
import junit.framework.TestCase;

public class VxmlParserTest extends TestCase {

    public void testVxmlParsingFromXmlString() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">\n" +
                "  <form id=\"welcome\">\n" +
                "    <block>\n" +
                "      <prompt>Welcome to VXML Test</prompt>\n" +
                "      <goto next=\"#main_menu\"/>\n" +
                "    </block>\n" +
                "  </form>\n" +
                "  <menu id=\"main_menu\">\n" +
                "    <prompt>Press 1 for sales</prompt>\n" +
                "    <choice dtmf=\"1\" next=\"#sales_form\">Sales</choice>\n" +
                "  </menu>\n" +
                "</vxml>";

        VxmlDocument doc = VxmlParser.parse(xml);
        assertNotNull(doc);
        assertEquals("2.1", doc.getVersion());
        assertEquals(2, doc.getDialogs().size());

        VxmlDialog firstDialog = doc.getDialogs().get(0);
        assertTrue(firstDialog instanceof VxmlForm);
        VxmlForm form = (VxmlForm) firstDialog;
        assertEquals("welcome", form.getId());
        assertEquals("Welcome to VXML Test", form.getPrompt());
        assertEquals("main_menu", form.getNextTarget());

        VxmlDialog secondDialog = doc.getDialogs().get(1);
        assertTrue(secondDialog instanceof VxmlMenu);
        VxmlMenu menu = (VxmlMenu) secondDialog;
        assertEquals("main_menu", menu.getId());
        assertEquals(1, menu.getChoices().size());
        assertEquals("1", menu.getChoices().get(0).getDtmf());
        assertEquals("sales_form", menu.getChoices().get(0).getNext());
    }
}
