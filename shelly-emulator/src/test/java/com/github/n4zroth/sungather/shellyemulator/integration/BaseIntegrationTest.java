package com.github.n4zroth.sungather.shellyemulator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.n4zroth.sungather.shellyemulator.testutil.InfluxDBTestHelper;
import com.influxdb.client.InfluxDBClient;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InfluxDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for integration tests providing common setup for TestContainers and MQTT broker.
 */
@Testcontainers
public abstract class BaseIntegrationTest {

    protected static final String INFLUXDB_IMAGE = "influxdb:2.7";
    protected static final String MOSQUITTO_IMAGE = "eclipse-mosquitto:2.0";
    protected static final String BUCKET = "test-energy";
    protected static final String ORG = "test-org";
    protected static final String ADMIN_TOKEN = "test-admin-token";

    @Container
    protected static InfluxDBContainer<?> influxDBContainer = new InfluxDBContainer<>(DockerImageName.parse(INFLUXDB_IMAGE))
            .withBucket(BUCKET)
            .withOrganization(ORG)
            .withAdminToken(ADMIN_TOKEN);

    @Container
    protected static GenericContainer<?> mosquittoContainer = new GenericContainer<>(DockerImageName.parse(MOSQUITTO_IMAGE))
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forListeningPort());

    protected InfluxDBTestHelper influxDBTestHelper;
    protected IMqttClient testPublisher;
    protected IMqttClient testSubscriber;
    protected final List<String> receivedMessages = new CopyOnWriteArrayList<>();

    @Autowired
    protected InfluxDBClient influxDBClient;

    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${mqtt.inbound-topic}")
    protected String inboundTopic;

    @Value("${mqtt.outbound-current-topic}")
    protected String outboundCurrentTopic;

    @Value("${mqtt.outbound-total-topic}")
    protected String outboundTotalTopic;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Override InfluxDB properties with TestContainer values
        registry.add("influx.url", influxDBContainer::getUrl);
        registry.add("influx.token", () -> ADMIN_TOKEN);
        registry.add("influx.bucket", () -> BUCKET);
        registry.add("influx.org", () -> ORG);

        // MQTT properties using Mosquitto TestContainer
        registry.add("mqtt.host", mosquittoContainer::getHost);
        registry.add("mqtt.port", () -> mosquittoContainer.getMappedPort(1883));
    }

    @BeforeEach
    void setUpIntegrationTest() throws MqttException {
        // Clear received messages
        receivedMessages.clear();

        // Initialize InfluxDB test helper
        influxDBTestHelper = new InfluxDBTestHelper(influxDBClient, BUCKET, ORG);

        // Get MQTT broker connection details
        String brokerUrl = "tcp://" + mosquittoContainer.getHost() + ":" + mosquittoContainer.getMappedPort(1883);

        // Create test MQTT clients
        testPublisher = new MqttClient(brokerUrl, "test-publisher");
        testPublisher.connect();

        testSubscriber = new MqttClient(brokerUrl, "test-subscriber");
        testSubscriber.connect();

        // Subscribe to outbound topics and capture messages
        testSubscriber.subscribe(outboundCurrentTopic, (topic, message) -> {
            receivedMessages.add(new String(message.getPayload()));
        });
        testSubscriber.subscribe(outboundTotalTopic, (topic, message) -> {
            receivedMessages.add(new String(message.getPayload()));
        });

        // Wait for all MQTT subscriptions to be fully established
        sleepQuietly(2000);
    }

    @AfterEach
    void tearDownIntegrationTest() {
        // Disconnect test clients
        try {
            if (testPublisher != null && testPublisher.isConnected()) {
                testPublisher.disconnect();
                testPublisher.close();
            }
            if (testSubscriber != null && testSubscriber.isConnected()) {
                testSubscriber.disconnect();
                testSubscriber.close();
            }
        } catch (MqttException e) {
            System.err.println("Error disconnecting test clients: " + e.getMessage());
        }

        // Clear InfluxDB data
        if (influxDBTestHelper != null) {
            influxDBTestHelper.clearBucket();
        }

        // Clear received messages
        receivedMessages.clear();
    }

    /**
     * Helper method to sleep without throwing checked exception.
     */
    protected void sleepQuietly(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
