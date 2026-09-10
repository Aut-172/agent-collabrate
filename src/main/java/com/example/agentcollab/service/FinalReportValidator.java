package com.example.agentcollab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Component;

@Component
public class FinalReportValidator {
    private final JsonSchema schema;

    public FinalReportValidator() {
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(getClass().getResourceAsStream("/schema/final-report-v1.schema.json"));
    }

    public void validate(JsonNode report) {
        if (report == null || report.isNull() || !schema.validate(report).isEmpty()) {
            throw new FinalReportValidationException("Final Report 不符合 JSON Schema");
        }
    }
}
