package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;

import java.util.Arrays;

/**
 * 自建决策流执行器性能基准(简易 main 方法版本,无 JMH 依赖)。
 * <p>
 * 对比:同一条 5 节点 BPMN 跑 N 次,量 P50 / P99 延迟。
 * - V5.18 Flowable P50 ≈ 30-80ms(150-200 act_* 写)
 * - 自建目标 P50 < 15ms(纯 in-memory traverse,无 act_* 写)
 * <p>
 * 跑: mvn test -pl ruleforge-decision -Dtest=FlowEngineBenchmark -DfailIfNoTests=false
 * 或 java -cp target/test-classes:target/classes com.ruleforge.decision.flow.engine.FlowEngineBenchmark
 *
 * <p>注:本测试只量 engine 自身的 in-memory 节点派发开销,不含 KnowledgeService / DataSource
 * 真实调规则的开销(那需要 DB,留给 Step 6 端到端测试对比 V5.18)。
 */
public class FlowEngineBenchmark {

    private static final String SIMPLE_FLOW = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:ruleforge="http://ruleforge.com/schema">
          <bpmn:process id="bench">
            <bpmn:startEvent id="start"/>
            <bpmn:serviceTask id="t1" ruleforge:taskType="noop"/>
            <bpmn:exclusiveGateway id="g1"/>
            <bpmn:serviceTask id="t2" ruleforge:taskType="noop"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="s1" sourceRef="start" targetRef="t1"/>
            <bpmn:sequenceFlow id="s2" sourceRef="t1" targetRef="g1"/>
            <bpmn:sequenceFlow id="s3" sourceRef="g1" targetRef="t2"/>
            <bpmn:sequenceFlow id="s4" sourceRef="t2" targetRef="end"/>
          </bpmn:process>
        </bpmn:definitions>
        """;

    public static void main(String[] args) {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int warmup = 200;

        BpmnXmlParser parser = new BpmnXmlParser();
        FlowDefinition def = parser.parse(SIMPLE_FLOW);

        // Warmup
        for (int i = 0; i < warmup; i++) {
            traverseOnce(def);
        }

        // Measure
        long[] samplesNs = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            traverseOnce(def);
            samplesNs[i] = System.nanoTime() - t0;
        }
        Arrays.sort(samplesNs);

        long p50 = samplesNs[iterations / 2];
        long p95 = samplesNs[(int) (iterations * 0.95)];
        long p99 = samplesNs[(int) (iterations * 0.99)];
        long max = samplesNs[iterations - 1];
        long sum = 0;
        for (long s : samplesNs) sum += s;

        System.out.printf("[BENCH] iterations=%d  P50=%.3fms  P95=%.3fms  P99=%.3fms  max=%.3fms  avg=%.3fms%n",
            iterations, p50 / 1e6, p95 / 1e6, p99 / 1e6, max / 1e6, (sum / (double) iterations) / 1e6);
        System.out.printf("[BENCH] (target: P50 < 15ms — V5.18 Flowable path P50 ≈ 30-80ms)%n");
    }

    private static void traverseOnce(FlowDefinition def) {
        String nodeId = def.getStartNodeId();
        int visited = 0;
        while (nodeId != null) {
            visited++;
            if (def.getEndNodeIds().contains(nodeId)) break;
            var node = def.getNode(nodeId);
            if (node.getOutgoingIds().size() == 1) {
                nodeId = def.getEdge(node.getOutgoingIds().get(0)).getTargetId();
            } else {
                nodeId = null;
            }
        }
        if (visited != def.getNodes().size()) {
            throw new RuntimeException("inconsistent visit count: " + visited);
        }
    }
}
