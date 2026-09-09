package com.example.agentcollab.service;

import com.example.agentcollab.dto.ProjectDtos.CapabilityProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class ProfileJsonMapper {
    private final ObjectMapper objectMapper;
    private final CapabilityProfileValidator validator;

    public ProfileJsonMapper(ObjectMapper objectMapper, CapabilityProfileValidator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public ObjectNode toJson(CapabilityProfileRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("summary", request.summary());
        node.set("responsibilities", objectMapper.valueToTree(request.responsibilities() == null ? java.util.List.of() : request.responsibilities()));
        node.set("skills", objectMapper.valueToTree(request.skills() == null ? java.util.List.of() : request.skills()));
        node.set("experience", objectMapper.valueToTree(request.experience() == null ? java.util.List.of() : request.experience()));
        node.set("preferredTaskTypes", objectMapper.valueToTree(request.preferredTaskTypes() == null ? java.util.List.of() : request.preferredTaskTypes()));
        node.set("limitations", objectMapper.valueToTree(request.limitations() == null ? java.util.List.of() : request.limitations()));
        node.put("availability", request.availability());
        node.put("weeklyCapacityPoints", request.weeklyCapacityPoints());
        if (request.notes() != null) node.put("notes", request.notes());
        validator.validate(node);
        return node;
    }
}
