/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.utils;

import com.jayway.jsonpath.JsonPath;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.strimzi.api.kafka.model.ContainerEnvVar;
import io.strimzi.api.kafka.model.ContainerEnvVarBuilder;
import io.strimzi.systemtest.Constants;
import io.strimzi.systemtest.Environment;
import io.strimzi.test.TestUtils;
import io.strimzi.test.timemeasuring.Operation;
import io.strimzi.test.timemeasuring.TimeMeasuringSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.strimzi.test.k8s.KubeClusterResource.cmdKubeClient;
import static io.strimzi.test.k8s.KubeClusterResource.kubeClient;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class StUtils {

    private static final Logger LOGGER = LogManager.getLogger(StUtils.class);

    private static final Pattern KAFKA_COMPONENT_PATTERN = Pattern.compile("([^-|^_]*?)(?<kafka>[-|_]kafka[-|_])(?<version>.*)$");

    private static final Pattern IMAGE_PATTERN_FULL_PATH = Pattern.compile("^(?<registry>[^/]*)/(?<org>[^/]*)/(?<image>[^:]*):(?<tag>.*)$");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("^(?<org>[^/]*)/(?<image>[^:]*):(?<tag>.*)$");

    private static final Pattern VERSION_IMAGE_PATTERN = Pattern.compile("(?<version>[0-9.]+)=(?<image>[^\\s]*)");

    private static TimeMeasuringSystem timeMeasuringSystem = TimeMeasuringSystem.getInstance();

    private StUtils() { }

    public static void waitForReconciliation(String testClass, String testName, String namespace) {
        LOGGER.info("Waiting for reconciliation");
        String reconciliation = timeMeasuringSystem.startOperation(Operation.NEXT_RECONCILIATION);
        TestUtils.waitFor("Wait till another rolling update starts", Constants.CO_OPERATION_TIMEOUT_POLL, Constants.RECONCILIATION_INTERVAL + 30000,
            () -> !cmdKubeClient().searchInLog("deploy", "strimzi-cluster-operator",
                timeMeasuringSystem.getCurrentDuration(testClass, testName, reconciliation),
                "'Triggering periodic reconciliation for namespace " + namespace + "'").isEmpty());
        timeMeasuringSystem.stopOperation(reconciliation);
    }

    public static void waitForRollingUpdateTimeout(String testClass, String testName, String logPattern, String operationID) {
        TestUtils.waitFor("Wait till rolling update timeout", Constants.CO_OPERATION_TIMEOUT_POLL, Constants.CO_OPERATION_TIMEOUT_WAIT,
            () -> !cmdKubeClient().searchInLog("deploy", "strimzi-cluster-operator", timeMeasuringSystem.getCurrentDuration(testClass, testName, operationID), logPattern).isEmpty());
    }

    /**
     * Method for check if test is allowed on current Kubernetes version
     * @param desiredKubernetesVersion kubernetes version which test needs
     * @return true if test is allowed, false if not
     */
    public static boolean isAllowedOnCurrentK8sVersion(String desiredKubernetesVersion) {
        if (desiredKubernetesVersion.equals("latest")) {
            return true;
        }
        return Double.parseDouble(kubeClient().clusterKubernetesVersion()) < Double.parseDouble(desiredKubernetesVersion);
    }

    /**
     * The method to configure docker image to use proper docker registry, docker org and docker tag.
     * @param image Image that needs to be changed
     * @return Updated docker image with a proper registry, org, tag
     */
    public static String changeOrgAndTag(String image) {
        Matcher m = IMAGE_PATTERN_FULL_PATH.matcher(image);
        if (m.find()) {
            String registry = setImageProperties(m.group("registry"), Environment.STRIMZI_REGISTRY, Environment.STRIMZI_REGISTRY_DEFAULT);
            String org = setImageProperties(m.group("org"), Environment.STRIMZI_ORG, Environment.STRIMZI_ORG_DEFAULT);

            return registry + "/" + org + "/" + m.group("image") + ":" + buildTag(m.group("tag"));
        }
        m = IMAGE_PATTERN.matcher(image);
        if (m.find()) {
            String org = setImageProperties(m.group("org"), Environment.STRIMZI_ORG, Environment.STRIMZI_ORG_DEFAULT);

            return Environment.STRIMZI_REGISTRY + "/" + org + "/" + m.group("image") + ":"  + buildTag(m.group("tag"));
        }
        return image;
    }

    public static String changeOrgAndTagInImageMap(String imageMap) {
        Matcher m = VERSION_IMAGE_PATTERN.matcher(imageMap);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group("version") + "=" + changeOrgAndTag(m.group("image")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String setImageProperties(String current, String envVar, String defaultEnvVar) {
        if (!envVar.equals(defaultEnvVar) && !current.equals(envVar)) {
            return envVar;
        }
        return current;
    }

    private static String buildTag(String currentTag) {
        if (!currentTag.equals(Environment.STRIMZI_TAG) && !Environment.STRIMZI_TAG_DEFAULT.equals(Environment.STRIMZI_TAG)) {
            Matcher t = KAFKA_COMPONENT_PATTERN.matcher(currentTag);
            if (t.find()) {
                currentTag = Environment.STRIMZI_TAG + t.group("kafka") + t.group("version");
            } else {
                currentTag = Environment.STRIMZI_TAG;
            }
        }
        return currentTag;
    }

    public static List<ContainerEnvVar> createContainerEnvVarsFromMap(Map<String, String> envVars) {
        List<ContainerEnvVar> testEnvs = new ArrayList<>();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            testEnvs.add(new ContainerEnvVarBuilder()
                .withName(entry.getKey())
                .withValue(entry.getValue()).build());
        }
        return testEnvs;
    }

    public static void checkCologForUsedVariable(String varName) {
        LOGGER.info("Check if ClusterOperator logs already defined variable occurrence");
        String coLog = kubeClient().logs(kubeClient().listPodNames("name", "strimzi-cluster-operator").get(0));
        assertThat(coLog.contains("User defined container template environment variable " + varName + " is already in use and will be ignored"), is(true));
        LOGGER.info("ClusterOperator logs contains proper warning");
    }

    /**
     * Translate key/value pairs formatted like properties into a Map
     * @param keyValuePairs Pairs in key=value format; pairs are separated by newlines
     * @return THe map of key/values
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadProperties(String keyValuePairs) {
        try {
            Properties actual = new Properties();
            actual.load(new StringReader(keyValuePairs));
            return (Map) actual;
        } catch (IOException e) {
            throw new AssertionError("Invalid Properties definition", e);
        }
    }

    /**
     * Get a Map of properties from an environment variable in json.
     * @param containerIndex name of the container
     * @param json The json from which to extract properties
     * @param envVar The environment variable name
     * @return The properties which the variable contains
     */
    public static Map<String, Object> getPropertiesFromJson(int containerIndex, String json, String envVar) {
        List<String> array = JsonPath.parse(json).read(globalVariableJsonPathBuilder(containerIndex, envVar));
        return StUtils.loadProperties(array.get(0));
    }

    /**
     * Get a jsonPath which can be used to extract envariable variables from a spec
     * @param containerIndex index of the container
     * @param envVar The environment variable name
     * @return The json path
     */
    public static String globalVariableJsonPathBuilder(int containerIndex, String envVar) {
        return "$.spec.containers[" + containerIndex + "].env[?(@.name=='" + envVar + "')].value";
    }

    public static Properties stringToProperties(String str) {
        Properties result = new Properties();
        List<String> list = getLinesWithoutCommentsAndEmptyLines(str);
        for (String line: list) {
            String[] split = line.split("=");
            result.put(split[0], split.length == 1 ? "" : split[1]);
        }
        return result;
    }

    public static Properties configMap2Properties(ConfigMap cm) {
        return stringToProperties(cm.getData().get("server.config"));
    }

    public static List<String> getLinesWithoutCommentsAndEmptyLines(String config) {
        List<String> allLines = Arrays.asList(config.split("\\r?\\n"));
        List<String> validLines = new ArrayList<>();

        for (String line : allLines)    {
            if (!line.replace(" ", "").startsWith("#") && !line.isEmpty())   {
                validLines.add(line.replace(" ", ""));
            }
        }
        return validLines;
    }

    public static JsonArray expectedServiceDiscoveryInfo(int port, String protocol, String auth) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("port", port);
        jsonObject.put("tls", port == 9093);
        jsonObject.put("protocol", protocol);
        jsonObject.put("auth", auth);

        JsonArray jsonArray = new JsonArray();
        jsonArray.add(jsonObject);

        return jsonArray;
    }

    public static JsonArray expectedServiceDiscoveryInfo(String plainAuth, String tlsAuth) {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(expectedServiceDiscoveryInfo(9092, "kafka", plainAuth).getValue(0));
        jsonArray.add(expectedServiceDiscoveryInfo(9093, "kafka", tlsAuth).getValue(0));
        return jsonArray;
    }

    public static boolean checkLogForJSONFormat(Map<String, String> pods, String containerName) {
        boolean isJSON = false;

        for (String podName : pods.keySet()) {
            String logs = kubeClient().logs(podName, containerName).replaceFirst("([^{]+)", "");
            try {
                new JsonObject(logs);
                LOGGER.info("JSON format logging successfully set for {} - {}", podName, containerName);
                isJSON = true;
            } catch (Exception e) {
                LOGGER.info("Failed to set JSON format logging for {} - {}", podName, containerName);
                isJSON = false;
                break;
            }
        }
        return isJSON;
    }
}