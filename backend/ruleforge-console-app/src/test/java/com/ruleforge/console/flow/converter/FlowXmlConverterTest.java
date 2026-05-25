package com.ruleforge.console.flow.converter;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FlowXmlConverter - 旧流程 XML 转 BPMN 2.0")
class FlowXmlConverterTest {

    private final FlowXmlConverter converter = new FlowXmlConverter();

    private String wrapInFlow(String... nodeElements) {
        StringBuilder sb = new StringBuilder();
        sb.append("<rule-flow id=\"test-flow\">");
        for (String node : nodeElements) {
            sb.append(node);
        }
        sb.append("</rule-flow>");
        return sb.toString();
    }

    private Document convertAndParse(String oldXml) throws Exception {
        String bpmn = converter.convertToBpmn(oldXml);
        return DocumentHelper.parseText(bpmn);
    }

    private Element getProcessElement(Document doc) {
        return (Element) doc.getRootElement().elements().stream()
            .filter(e -> ((Element) e).getQualifiedName().equals("bpmn:process"))
            .findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Element> getProcessChildren(Document doc, String localName) {
        Element process = getProcessElement(doc);
        return process.elements().stream()
            .filter(e -> ((Element) e).getQualifiedName().equals(localName))
            .map(e -> (Element) e)
            .toList();
    }

    private Element findChildByName(Document doc, String elementQName, String nameAttr) {
        return getProcessChildren(doc, elementQName).stream()
            .filter(el -> nameAttr.equals(el.attributeValue("name")))
            .findFirst().orElse(null);
    }

    private String convertAndGetRaw(String oldXml) throws Exception {
        return converter.convertToBpmn(oldXml);
    }

    // dom4j attributeValue("ruleforge:xxx") doesn't match namespaced attributes
    // because QName resolution differs between creation and lookup.
    // Use qualified-name string matching instead.
    private String getAttrByQName(Element el, String qualifiedName) {
        for (Object obj : el.attributes()) {
            Attribute attr = (Attribute) obj;
            if (qualifiedName.equals(attr.getQualifiedName())) {
                return attr.getValue();
            }
        }
        return null;
    }

    @Nested
    @DisplayName("转换包含所有节点类型的流程")
    class ConvertAllNodeTypes {

        @Test
        @DisplayName("start 节点映射为 startEvent")
        void shouldMapStartNodeToStartEvent() throws Exception {
            String xml = wrapInFlow("<start name=\"Start1\" x=\"10\" y=\"20\" width=\"30\" height=\"40\"/>");
            Document result = convertAndParse(xml);

            Element start = findChildByName(result, "bpmn:startEvent", "Start1");
            assertThat(start).isNotNull();
            assertThat(start.attributeValue("id")).isEqualTo("Start1");
        }

        @Test
        @DisplayName("end 节点映射为 endEvent")
        void shouldMapEndNodeToEndEvent() throws Exception {
            String xml = wrapInFlow("<end name=\"End1\"/>");
            Document result = convertAndParse(xml);

            Element end = findChildByName(result, "bpmn:endEvent", "End1");
            assertThat(end).isNotNull();
        }

        @Test
        @DisplayName("rule 节点映射为 serviceTask 并保留扩展属性")
        void shouldMapRuleNodeToServiceTaskWithExtensions() throws Exception {
            String xml = wrapInFlow(
                "<rule name=\"Rule1\" file=\"rules/rule.xml\" version=\"1.0\" packageName=\"demo\"/>");
            Document result = convertAndParse(xml);

            Element task = findChildByName(result, "bpmn:serviceTask", "Rule1");
            assertThat(task).isNotNull();
            assertThat(getAttrByQName(task, "ruleforge:taskType")).isEqualTo("rule");
            assertThat(getAttrByQName(task, "ruleforge:file")).isEqualTo("rules/rule.xml");
            assertThat(getAttrByQName(task, "ruleforge:version")).isEqualTo("1.0");
            assertThat(getAttrByQName(task, "ruleforge:project")).isEqualTo("demo");
        }

        @Test
        @DisplayName("action 节点映射为 serviceTask 并保留 bean 属性")
        void shouldMapActionNodeToServiceTaskWithBean() throws Exception {
            String xml = wrapInFlow("<action name=\"Act1\" action-bean=\"myBean\"/>");
            Document result = convertAndParse(xml);

            Element task = findChildByName(result, "bpmn:serviceTask", "Act1");
            assertThat(task).isNotNull();
            assertThat(getAttrByQName(task, "ruleforge:taskType")).isEqualTo("action");
            assertThat(getAttrByQName(task, "ruleforge:bean")).isEqualTo("myBean");
        }

        @Test
        @DisplayName("script 节点映射为 scriptTask 并保留脚本内容")
        void shouldMapScriptNodeToScriptTaskWithContent() throws Exception {
            String xml = wrapInFlow("<script name=\"Script1\">println 'hello'</script>");
            Document result = convertAndParse(xml);

            Element task = findChildByName(result, "bpmn:scriptTask", "Script1");
            assertThat(task).isNotNull();
            Element scriptEl = (Element) task.elements().stream()
                .filter(e -> ((Element) e).getQualifiedName().equals("bpmn:script"))
                .findFirst().orElse(null);
            assertThat(scriptEl).isNotNull();
            assertThat(scriptEl.getTextTrim()).isEqualTo("println 'hello'");
        }

        @Test
        @DisplayName("decision 节点 Percent 映射为 exclusiveGateway with percent")
        void shouldMapDecisionNodeToExclusiveGateway() throws Exception {
            String xml = wrapInFlow("<decision name=\"Dec1\" decision-type=\"Percent\"/>");
            Document result = convertAndParse(xml);

            Element gw = findChildByName(result, "bpmn:exclusiveGateway", "Dec1");
            assertThat(gw).isNotNull();
            assertThat(getAttrByQName(gw, "ruleforge:decisionType")).isEqualTo("percent");
        }

        @Test
        @DisplayName("decision 节点 Criteria 类型映射为 condition")
        void shouldMapDecisionCriteriaToCondition() throws Exception {
            String xml = wrapInFlow("<decision name=\"Dec2\" decision-type=\"Criteria\"/>");
            Document result = convertAndParse(xml);

            Element gw = findChildByName(result, "bpmn:exclusiveGateway", "Dec2");
            assertThat(gw).isNotNull();
            assertThat(getAttrByQName(gw, "ruleforge:decisionType")).isEqualTo("condition");
        }

        @Test
        @DisplayName("fork 节点映射为 parallelGateway")
        void shouldMapForkNodeToParallelGateway() throws Exception {
            String xml = wrapInFlow("<fork name=\"Fork1\"/>");
            Document result = convertAndParse(xml);

            Element gw = findChildByName(result, "bpmn:parallelGateway", "Fork1");
            assertThat(gw).isNotNull();
        }

        @Test
        @DisplayName("join 节点映射为 parallelGateway")
        void shouldMapJoinNodeToParallelGateway() throws Exception {
            String xml = wrapInFlow("<join name=\"Join1\"/>");
            Document result = convertAndParse(xml);

            Element gw = findChildByName(result, "bpmn:parallelGateway", "Join1");
            assertThat(gw).isNotNull();
        }

        @Test
        @DisplayName("rule-package 节点映射为 serviceTask 并保留包信息")
        void shouldMapRulePackageNodeToServiceTaskWithPackageInfo() throws Exception {
            String xml = wrapInFlow("<rule-package name=\"Pkg1\" package-id=\"pkg1\" project=\"demo\"/>");
            Document result = convertAndParse(xml);

            Element task = findChildByName(result, "bpmn:serviceTask", "Pkg1");
            assertThat(task).isNotNull();
            assertThat(getAttrByQName(task, "ruleforge:taskType")).isEqualTo("package");
            assertThat(getAttrByQName(task, "ruleforge:packageId")).isEqualTo("pkg1");
            assertThat(getAttrByQName(task, "ruleforge:project")).isEqualTo("demo");
        }
    }

    @Nested
    @DisplayName("连线转换")
    class ConnectionConversion {

        @Test
        @DisplayName("连线映射为 sequenceFlow")
        void shouldMapConnectionToSequenceFlow() throws Exception {
            String xml = wrapInFlow(
                "<start name=\"Start1\"> <connection to=\"End1\"/> </start>",
                "<end name=\"End1\"/>"
            );
            Document result = convertAndParse(xml);

            List<Element> flows = getProcessChildren(result, "bpmn:sequenceFlow");
            boolean hasFlow = flows.stream().anyMatch(f ->
                "Start1".equals(f.attributeValue("sourceRef")) &&
                "End1".equals(f.attributeValue("targetRef"))
            );
            assertThat(hasFlow).isTrue();

            Element start = findChildByName(result, "bpmn:startEvent", "Start1");
            boolean hasOutgoing = start.elements().stream()
                .anyMatch(e -> ((Element) e).getQualifiedName().equals("bpmn:outgoing"));
            assertThat(hasOutgoing).isTrue();
        }

        @Test
        @DisplayName("连线 percent 属性映射为扩展属性")
        void shouldMapPercentToExtensionAttribute() throws Exception {
            String xml = wrapInFlow(
                "<decision name=\"Dec1\">" +
                "  <connection to=\"A\" percent=\"30\"/>" +
                "  <connection to=\"B\" percent=\"70\"/>" +
                "</decision>"
            );
            Document result = convertAndParse(xml);

            List<Element> flows = getProcessChildren(result, "bpmn:sequenceFlow");
            boolean found30 = flows.stream()
                .anyMatch(f -> "30".equals(getAttrByQName(f, "ruleforge:percent")));
            assertThat(found30).isTrue();
        }
    }

    @Nested
    @DisplayName("事件 Bean 属性")
    class EventBeanAttribute {

        @Test
        @DisplayName("event-bean 属性应保留为扩展属性")
        void shouldPreserveEventBeanAttribute() throws Exception {
            String xml = wrapInFlow("<start name=\"Start1\" event-bean=\"myEventBean\"/>");
            Document result = convertAndParse(xml);

            Element start = findChildByName(result, "bpmn:startEvent", "Start1");
            assertThat(getAttrByQName(start, "ruleforge:eventBean")).isEqualTo("myEventBean");
        }
    }

    @Nested
    @DisplayName("输入校验")
    class InputValidation {

        @Test
        @DisplayName("非 rule-flow XML 应抛出异常")
        void shouldThrowForNonRuleFlowXml() {
            assertThatThrownBy(() -> converter.convertToBpmn("<something-else/>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a valid rule-flow XML document");
        }

        @Test
        @DisplayName("flow id 应从根元素属性读取")
        void shouldReadFlowIdFromRootAttribute() throws Exception {
            String xml = wrapInFlow("<start name=\"S1\"/>");
            Document result = convertAndParse(xml);

            Element process = getProcessElement(result);
            assertThat(process.attributeValue("id")).isEqualTo("test-flow");
        }
    }

    @Nested
    @DisplayName("坐标和图形信息")
    class DiagramInfo {

        @Test
        @DisplayName("节点坐标映射为 BPMN 图形 Bounds")
        void shouldMapCoordinatesToBounds() throws Exception {
            String xml = wrapInFlow("<start name=\"S1\" x=\"50\" y=\"100\" width=\"36\" height=\"36\"/>");
            Document result = convertAndParse(xml);

            // Walk the tree to find BPMNPlane instead of using XPath (avoids jaxen dependency)
            Element definitions = result.getRootElement();
            Element diagram = (Element) definitions.elements().stream()
                .filter(e -> ((Element) e).getQualifiedName().equals("bpmndi:BPMNDiagram"))
                .findFirst().orElse(null);
            assertThat(diagram).isNotNull();
            Element plane = (Element) diagram.elements().stream()
                .filter(e -> ((Element) e).getQualifiedName().equals("bpmndi:BPMNPlane"))
                .findFirst().orElse(null);
            assertThat(plane).isNotNull();

            boolean foundBounds = false;
            for (Object obj : plane.elements()) {
                Element shape = (Element) obj;
                if ("S1_di".equals(shape.attributeValue("id"))) {
                    Element bounds = (Element) shape.elements().stream()
                        .filter(e -> ((Element) e).getQualifiedName().equals("dc:Bounds"))
                        .findFirst().orElse(null);
                    assertThat(bounds).isNotNull();
                    assertThat(bounds.attributeValue("x")).isEqualTo("50");
                    assertThat(bounds.attributeValue("y")).isEqualTo("100");
                    assertThat(bounds.attributeValue("width")).isEqualTo("36");
                    assertThat(bounds.attributeValue("height")).isEqualTo("36");
                    foundBounds = true;
                }
            }
            assertThat(foundBounds).isTrue();
        }

        @Test
        @DisplayName("BPMN XML 包含正确的命名空间声明")
        void shouldContainCorrectNamespaces() throws Exception {
            String xml = wrapInFlow("<start name=\"S1\"/>");
            String result = convertAndGetRaw(xml);

            assertThat(result).contains("xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"");
            assertThat(result).contains("xmlns:ruleforge=\"http://ruleforge.com/schema\"");
            assertThat(result).contains("xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"");
        }
    }
}
