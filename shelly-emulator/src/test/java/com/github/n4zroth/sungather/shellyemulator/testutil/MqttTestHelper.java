package com.github.n4zroth.sungather.shellyemulator.testutil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Helper class for managing an embedded Moquette MQTT broker for testing.
 */
public class MqttTestHelper {

    private static final String BROKER_HOST = "localhost";
    private static final int BROKER_PORT = 1883;

    private final Server mqttBroker;
    private final List<String> receivedMessages = new CopyOnWriteArrayList<>();

    public MqttTestHelper() {
        this.mqttBroker = new Server();
    }

    public Server getMqttBroker() {
        return mqttBroker;
    }

    /**
     * Starts the embedded MQTT broker.
     */
    public void startBroker() throws IOException {
        IConfig config = new MemoryConfig(new java.util.Properties());
        config.setProperty("host", BROKER_HOST);
        config.setProperty("port", String.valueOf(BROKER_PORT));
        config.setProperty("allow_anonymous", "true");
        mqttBroker.startServer(config);
        System.out.println("Moquette MQTT broker started on " + BROKER_HOST + ":" + BROKER_PORT);
    }

    /**
     * Stops the embedded MQTT broker.
     */
    public void stopBroker() {
        mqttBroker.stopServer();
        System.out.println("Moquette MQTT broker stopped");
    }

    /**
     * Creates and connects an MQTT client for testing.
     */
    public IMqttClient createConnectedClient(String clientId) throws MqttException {
        MqttClient client = new MqttClient(getBrokerUrl(), clientId);
        client.connect();
        System.out.println("Test MQTT client '" + clientId + "' connected");
        return client;
    }

    /**
     * Subscribes to a topic and captures all received messages.
     */
    public IMqttClient createSubscriber(String clientId, String topic) throws MqttException {
        IMqttClient client = createConnectedClient(clientId);
        client.subscribe(topic, (t, message) -> {
            String payload = new String(message.getPayload());
            receivedMessages.add(payload);
        });
        return client;
    }

    /**
     * Publishes a message to the specified topic.
     */
    public void publishMessage(IMqttClient client, String topic, String payload) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        client.publish(topic, message);
    }

    /**
     * Returns the broker URL for client connections.
     */
    public String getBrokerUrl() {
        return String.format("tcp://%s:%d", BROKER_HOST, BROKER_PORT);
    }

    /**
     * Gets the list of received messages.
     */
    public List<String> getReceivedMessages() {
        return receivedMessages;
    }

    /**
     * Clears all received messages.
     */
    public void clearReceivedMessages() {
        receivedMessages.clear();
    }

    /**
     * Disconnects and closes an MQTT client.
     */
    public void disconnectClient(IMqttClient client) {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
                System.out.println("Test MQTT client disconnected");
            } catch (MqttException e) {
                System.err.println("Error disconnecting test client: " + e.getMessage());
            }
        }
    }
}
