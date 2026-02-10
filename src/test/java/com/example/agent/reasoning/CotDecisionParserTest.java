package com.example.agent.reasoning;

import com.example.agent.reasoning.common.JsonPayloadNormalizer;
import com.example.agent.reasoning.common.ReasoningParseSupport;
import com.example.agent.reasoning.cot.CotDecisionParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CotDecisionParserTest {

    @Test
    void parseShouldSupportCodeFenceJson() {
        CotDecisionParser parser = new CotDecisionParser(
                new ObjectMapper(),
                new JsonPayloadNormalizer(),
                new ReasoningParseSupport()
        );
        String content = "```json\n{\"stepSummary\":\"next\",\"shouldContinue\":false,\"finalAnswer\":\"done\",\"confidence\":0.8}\n```";

        CotDecisionParser.CotDecision decision = parser.parse(content);
        assertTrue(decision.valid());
        assertFalse(decision.shouldContinue());
        assertEquals("done", decision.finalAnswer());
    }

    @Test
    void parseShouldReturnInvalidForMalformedJson() {
        CotDecisionParser parser = new CotDecisionParser(
                new ObjectMapper(),
                new JsonPayloadNormalizer(),
                new ReasoningParseSupport()
        );
        CotDecisionParser.CotDecision decision = parser.parse("not-json");

        assertFalse(decision.valid());
        assertEquals("invalid_response", decision.stopReason());
    }
}
