package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VxmlToModelConverterTest {

    @Test
    void testConvertMalformedIfElseSiblings() throws VxmlParseException {
        String vxml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="route">
                <block>
                  <if cond="day == 'weekday'">
                    <goto next="#business_hours"/>
                  </if>
                  <else>
                    <goto next="#closed"/>
                  </else>
                </block>
              </form>
            </vxml>
            """;

        VxmlToModelConverter converter = new VxmlToModelConverter();
        FlowModel model = converter.convert(vxml);
        assertNotNull(model);
        assertFalse(model.getNodes().isEmpty());
    }

    @Test
    void testConvertFieldWithInvalidTypeAttributeDefaultsTo1() throws VxmlParseException {
        String vxml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="authenticate">
                <field name="account" type="boolean">
                  <prompt>Enter your PIN.</prompt>
                  <filled>
                    <goto next="#menu"/>
                  </filled>
                </field>
              </form>
            </vxml>
            """;

        VxmlToModelConverter converter = new VxmlToModelConverter();
        FlowModel model = converter.convert(vxml);
        assertNotNull(model);
        assertFalse(model.getNodes().isEmpty());
        assertEquals(1, model.getNodes().size());
        assertEquals("authenticate", model.getNodes().getFirst().getId());
    }

    @Test
    void testConvertValidIfElseifElseUnchanged() throws VxmlParseException {
        String vxml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="route">
                <block>
                  <if cond="day == 'weekday'">
                    <goto next="#business_hours"/>
                  </if>
                  <elseif cond="day == 'saturday'">
                    <goto next="#saturday"/>
                  </elseif>
                  <else>
                    <goto next="#closed"/>
                  </else>
                </block>
              </form>
            </vxml>
            """;

        VxmlToModelConverter converter = new VxmlToModelConverter();
        FlowModel model = converter.convert(vxml);
        assertNotNull(model);
        assertFalse(model.getNodes().isEmpty());
    }
}
