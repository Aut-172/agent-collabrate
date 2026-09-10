package com.example.agentcollab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Component;

@Component
public class ContextPlanValidator {
    private final ObjectMapper json;
    private final JsonSchema schema;

    public ContextPlanValidator(ObjectMapper json) {
        this.json = json;
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(getClass().getResourceAsStream("/schema/context-plan-v1.schema.json"));
    }

    public JsonNode validate(String content) {
        try {
            JsonNode plan = json.readTree(content);
            var errors = schema.validate(plan);
            if (!errors.isEmpty()) throw new IllegalArgumentException(errors.iterator().next().getMessage());
            return plan;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Context Plan 不是合法 JSON", ex);
        }
    }
}
