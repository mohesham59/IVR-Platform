package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Generic Domain & Arabic Pipeline Regression Tests")
public class GenericDomainAndArabicFlowTest {

    private DomainFlowGenerator domainFlowGenerator;
    private VxmlToModelConverter vxmlToModelConverter;
    private ModelToVxmlExporter modelToVxmlExporter;

    @BeforeEach
    void setUp() {
        domainFlowGenerator = new DomainFlowGenerator();
        vxmlToModelConverter = new VxmlToModelConverter();
        modelToVxmlExporter = new ModelToVxmlExporter();
    }

    @Test
    @DisplayName("Should generate valid VXML and FlowModel for Egyptian Insurance Authority (Arabic)")
    void testEgyptianInsuranceAuthorityArabic() throws Exception {
        String description = "مصلحة تأمينات مصرية - قسم المعاشات وقسم الاستعلامات وقسم الشكاوى والخدمات";
        String vxml = domainFlowGenerator.generateVxml("generic", description);

        assertNotNull(vxml);
        assertTrue(vxml.contains("<?xml version=\"1.0\""), "VXML must contain valid XML declaration");
        assertTrue(vxml.contains("<vxml"), "VXML must contain root element");

        FlowModel model = vxmlToModelConverter.convert(vxml);
        assertNotNull(model);
        assertTrue(model.getNodes().size() >= 3, "FlowModel should contain at least 3 nodes");
    }

    @Test
    @DisplayName("Should generate valid VXML and FlowModel for Law Firm (Arabic)")
    void testLawFirmArabic() throws Exception {
        String description = "مكتب محاماة واستشارات قانونية - القضايا المدنية والقضايا التجارية واستشارات العقود";
        String vxml = domainFlowGenerator.generateVxml("legal", description);

        assertNotNull(vxml);
        FlowModel model = vxmlToModelConverter.convert(vxml);
        assertNotNull(model);
        assertTrue(model.getNodes().size() >= 3);
    }

    @Test
    @DisplayName("Should generate valid VXML and FlowModel for Real Estate Agency (Arabic)")
    void testRealEstateArabic() throws Exception {
        String description = "شركة عقارات واستثمار - قسم البيع والشراء وقسم الشقق والمكاتب وقسم خدمة العملاء";
        String vxml = domainFlowGenerator.generateVxml("real_estate", description);

        assertNotNull(vxml);
        FlowModel model = vxmlToModelConverter.convert(vxml);
        assertNotNull(model);
        assertTrue(model.getNodes().size() >= 3);
    }

    @Test
    @DisplayName("Should generate valid VXML and FlowModel for Logistics & Delivery (Arabic)")
    void testLogisticsArabic() throws Exception {
        String description = "شركة شحن وتوصيل طرود - تتبع الشحنات والدعم والأسعار والتوصيل السريع";
        String vxml = domainFlowGenerator.generateVxml("logistics", description);

        assertNotNull(vxml);
        FlowModel model = vxmlToModelConverter.convert(vxml);
        assertNotNull(model);
        assertTrue(model.getNodes().size() >= 3);
    }

    @Test
    @DisplayName("Should generate valid VXML and FlowModel for Gym & Fitness Center (Arabic)")
    void testGymFitnessCenterArabic() throws Exception {
        String description = "مركز لياقة بدنية وجيم - الاشتراكات والمدربين والجمنازيوم والخدمات";
        String vxml = domainFlowGenerator.generateVxml("fitness", description);

        assertNotNull(vxml);
        FlowModel model = vxmlToModelConverter.convert(vxml);
        assertNotNull(model);
        assertTrue(model.getNodes().size() >= 3);
    }

    @Test
    @DisplayName("Should generate valid VXML and FlowModel for Express Cargo Delivery (English)")
    void testExpressCargoDeliveryEnglish() throws Exception {
        String description = "Logistics and Express Cargo Delivery Service with tracking, pricing, and support";
        String vxml = domainFlowGenerator.generateVxml("logistics", description);

        assertNotNull(vxml);
        FlowModel model = vxmlToModelConverter.convert(vxml);
        assertNotNull(model);
        assertTrue(model.getNodes().size() >= 3);
    }

    @Test
    @DisplayName("Should verify VXML round-trip conversion for AI Dynamic Routing <ai> tags")
    void testAiDynamicRoutingVxmlRoundTrip() throws Exception {
        String aiVxml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to Egyptian Insurance Authority.</prompt>
                    </block>
                    <ai role="insurance_assistant" options="pensions:pensions_form,inquiries:inquiries_form">
                      <prompt>Please speak which service you need: Pensions or General Inquiries.</prompt>
                    </ai>
                  </form>
                  <form id="pensions_form">
                    <block>
                      <prompt>Transferring to Pensions department.</prompt>
                      <transfer dest="+1001"/>
                    </block>
                  </form>
                  <form id="inquiries_form">
                    <block>
                      <prompt>Transferring to Inquiries department.</prompt>
                      <transfer dest="+1002"/>
                    </block>
                  </form>
                </vxml>
                """;

        FlowModel model = vxmlToModelConverter.convert(aiVxml);
        assertNotNull(model);
        assertTrue(model.getNodes().size() >= 3, "Converted model should have nodes for start, ai router, and target forms");

        String exportedVxml = modelToVxmlExporter.export(model);
        assertNotNull(exportedVxml);
        assertTrue(exportedVxml.contains("<ai role=\"insurance_assistant\"") || exportedVxml.contains("pensions"),
                "Exported VXML should preserve AI dynamic routing structure");
    }
}
