package org.kie.kogito.performance.test;

import org.apache.http.entity.ContentType;
import org.apache.jmeter.config.RandomVariableConfig;
import org.apache.jmeter.protocol.http.util.HTTPConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.postprocessors.DslJsonExtractor.JsonQueryLanguage;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static us.abstracta.jmeter.javadsl.JmeterDsl.htmlReporter;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpCache;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpCookies;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpHeaders;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.jsonExtractor;
import static us.abstracta.jmeter.javadsl.JmeterDsl.jtlWriter;
import static us.abstracta.jmeter.javadsl.JmeterDsl.testPlan;
import static us.abstracta.jmeter.javadsl.JmeterDsl.threadGroup;
import static us.abstracta.jmeter.javadsl.JmeterDsl.vars;
import static us.abstracta.jmeter.javadsl.wrapper.WrapperJmeterDsl.testElement;

public class PerformanceIT {
    private static Logger LOGGER = LoggerFactory.getLogger(PerformanceIT.class);

    private static final String DB_USER = "kuser";
    private static final String DB_NAME = "kogito";
    private static final String DB_PASS = "kpass";

    private static PostgreSQLContainer<?> postgresqlContainer;
    private static GenericContainer<?> kogioAppContainer;
    private static Network network;

    @SuppressWarnings({"resource", "deprecation"})
    @BeforeAll
    public static void init() {
        network = Network.newNetwork();

        postgresqlContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.1"))
                .withNetwork(network)
                .withNetworkAliases("database")
                .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                .withDatabaseName(DB_NAME)
                .withUsername(DB_USER)
                .withPassword(DB_PASS);
        postgresqlContainer.start();

        String reactJdbc = "postgresql://database:5432/" + DB_NAME;
        String jdbc = "jdbc:postgresql://database:5432/" + DB_NAME;

        System.out.println("docker image: " + System.getProperty("image.kogito.app"));
        kogioAppContainer = new GenericContainer<>(System.getProperty("image.kogito.app"))
                .withNetwork(network)
                .withNetworkAliases("kogitoApp")
                .dependsOn(postgresqlContainer)
                .withEnv("QUARKUS_DATASOURCE_JDBC_URL", jdbc)
                .withEnv("QUARKUS_DATASOURCE_REACTIVE_URL", reactJdbc)
                .withEnv("QUARKUS_DATASOURCE_USERNAME", DB_USER)
                .withEnv("QUARKUS_DATASOURCE_PASSWORD", DB_PASS)
                .waitingFor(Wait.forLogMessage(".*job-service-refactor-quarkus-embedded.*", 1))
                .withExposedPorts(8080);
        kogioAppContainer.start();

    }

    @AfterAll
    public static void stop() {
        kogioAppContainer.stop();
        postgresqlContainer.stop();
        network.close();

    }

    @Test
    public void testHiringProcess() throws IOException {
        TestPlanStats stats = testPlan(
                vars().set("host", "localhost").set("port", String.valueOf(kogioAppContainer.getFirstMappedPort())).set("user", "jdoe"))
                .tearDownOnlyAfterMainThreadsDone()
                .children(
                        httpCache()
                                .disable(),
                        httpCookies()
                                .disable(),
                        threadGroup("Hiring Thread Group", 30, Duration.ofMinutes(120))
                                .children(
                                        testElement("Email Random Variable", new RandomVariableConfig())
                                                .prop("variableName", "email")
                                                .prop("outputFormat", "Email00000")
                                                .prop("minimumValue", "1")
                                                .prop("maximumValue", "99999")
                                                .prop("randomSeed", "4")
                                                .prop("perThread", true),
                                        testElement("Last Name Random Variable", new RandomVariableConfig())
                                                .prop("variableName", "lastName")
                                                .prop("outputFormat", "LastName00000")
                                                .prop("minimumValue", "1")
                                                .prop("maximumValue", "99999")
                                                .prop("randomSeed", "1")
                                                .prop("perThread", true),
                                        testElement("Name Random Variable", new RandomVariableConfig())
                                                .prop("variableName", "name")
                                                .prop("outputFormat", "Name00000")
                                                .prop("minimumValue", "1")
                                                .prop("maximumValue", "99999")
                                                .prop("perThread", true),
                                        testElement("Experience random Variable", new RandomVariableConfig())
                                                .prop("maximumValue", "30")
                                                .prop("minimumValue", "5")
                                                .prop("perThread", true)
                                                .prop("variableName", "experience"),
                                        httpHeaders()
                                                .contentType(ContentType.APPLICATION_JSON),
                                        httpSampler("POST Hiring Process", "http://${host}:${port}/hiring")
                                                .method(HTTPConstants.POST)
                                                .body("{\r\n"
                                                        + "   \"candidateData\": {\r\n"
                                                        + "     \"name\": \"${name}\",\r\n"
                                                        + "     \"lastName\": \"${lastName}\",\r\n"
                                                        + "     \"email\": \"${email}\",\r\n"
                                                        + "     \"experience\": ${experience},\r\n"
                                                        + "     \"skills\": [\r\n"
                                                        + "       \"programming\"\r\n"
                                                        + "     ]\r\n"
                                                        + "   }\r\n"
                                                        + " }")
                                                .children(
                                                        jsonExtractor("processInstanceId", "$.id")
                                                                .queryLanguage(JsonQueryLanguage.JSON_PATH)),
                                        httpSampler("GET Tasks ", "http://${host}:${port}/hiring/${processInstanceId}/tasks?user=${user}")
                                                .children(
                                                        jsonExtractor("taskInstanceId", "$.[*].id")
                                                                .queryLanguage(JsonQueryLanguage.JSON_PATH)),
                                        httpSampler("Get Input HTTP Request", "http://${host}:${port}/hiring/${processInstanceId}/HRInterview/${taskInstanceId}?user=${user}")
                                                .children(
                                                        jsonExtractor("offerTask", "$.parameters.offer")
                                                                .queryLanguage(JsonQueryLanguage.JSON_PATH)),
                                        httpSampler("HRInterview HTTP Request", "http://${host}:${port}/hiring/${processInstanceId}/HRInterview/${taskInstanceId}?user=${user}&phase=complete")
                                                .method(HTTPConstants.POST)
                                                .body("{\r\n"
                                                        + "  \"offer\" : ${offerTask},\r\n"
                                                        + "  \"approve\": true\r\n"
                                                        + "}"),
                                        httpSampler("GET Tasks ", "http://${host}:${port}/hiring/${processInstanceId}/tasks?user=${user}")
                                                .children(
                                                        jsonExtractor("userTaskInstanceId", "$.[*].id")
                                                                .queryLanguage(JsonQueryLanguage.JSON_PATH)),
                                        httpSampler("ITInterview HTTP Request",
                                                "http://${host}:${port}/hiring/${processInstanceId}/ITInterview/${userTaskInstanceId}?user=${user}&phase=complete")
                                                .method(HTTPConstants.POST)
                                                .body("{\r\n"
                                                        + "  \"offer\" : ${offerTask},\r\n"
                                                        + "  \"approve\": true\r\n"
                                                        + "}"),
                                        httpSampler("GraphQL HTTP Request", "http://${host}:${port}/data-audit/")
                                                .method(HTTPConstants.POST)
                                                .body("{\r\n"
                                                        + "\t\"query\" : \"{ GetAllProcessInstancesState { eventId, eventDate, processType, processId, processVersion, parentProcessInstanceId, rootProcessId, rootProcessInstanceId, processInstanceId, businessKey, eventType, outcome, state, slaDueDate, roles } }\"\r\n"
                                                        + "}\r\n"
                                                        + "")),
                        htmlReporter("target/reports").timeGraphsGranularity(Duration.ofMinutes(1)),
                        jtlWriter("target", "outcome.csv"))
                .run();
        assertThat(stats.overall().errorsCount()).isEqualTo(0);
    }

}
