package com.example.agentcollab.service;

import com.example.agentcollab.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CapabilityProfileValidator {
    private final JsonSchema schema;

    public CapabilityProfileValidator() {
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(getClass().getResourceAsStream("/schema/capability-profile-v1.schema.json"));
    }

    public void validate(JsonNode profile) {
        if (!schema.validate(profile).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CAPABILITY_PROFILE", "能力画像不符合 Schema");
        }
    }
}
