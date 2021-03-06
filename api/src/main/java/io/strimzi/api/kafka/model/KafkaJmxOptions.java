/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Buildable(
        editableEnabled = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
public class KafkaJmxOptions implements UnknownPropertyPreserving, Serializable {
    private static final long serialVersionUID = 1L;
    private KafkaJmxAuthentication authentication;
    private Map<String, Object> additionalProperties = new HashMap<>(0);

    @Description("Authentication configuration for connecting to the Kafka JMX port")
    @JsonProperty("authentication")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public KafkaJmxAuthentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(KafkaJmxAuthentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @Override
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }
}
