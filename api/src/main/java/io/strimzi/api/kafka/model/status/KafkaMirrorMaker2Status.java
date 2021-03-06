/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Represents a status of the Kafka MirrorMaker 2.0 resource
 */
@Buildable(
        editableEnabled = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "conditions", "observedGeneration", "url" })
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class KafkaMirrorMaker2Status extends KafkaConnectStatus {
    private static final long serialVersionUID = 1L;

    private List<Map<String, Object>> connectors = new ArrayList<>();

    @Description("List of MirrorMaker 2.0 connector statuses, as reported by the Kafka Connect REST API.")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Map<String, Object>> getConnectors() {
        return connectors;
    }

    public void setConnectors(List<Map<String, Object>> connectors) {
        this.connectors = connectors;
    } 
}