package com.github.n4zroth.sungather.shellyemulator.logic;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.n4zroth.sungather.shellyemulator.config.MqttConfig;
import com.github.n4zroth.sungather.shellyemulator.model.ShellyMessageCurrent;
import com.github.n4zroth.sungather.shellyemulator.model.ShellyMessageTotal;
import com.github.n4zroth.sungather.shellyemulator.model.SungatherMeasurement;
import com.github.n4zroth.sungather.shellyemulator.model.SungatherMessage;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.context.annotation.Scope;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Scope("singleton")
@RequiredArgsConstructor
@Slf4j
public class MqttUpdater implements SungatherMessageListener {

    private final InfluxDBClient influxDBClient;
    private final MqttConfig mqttConfig;
    private final ObjectMapper objectMapper;
    private final IMqttClient mqttClient;

    private SungatherMeasurement lastMeasurement = null;
    private SungatherMessage lastMessage = null;

    public void handleMessage(final SungatherMessage sungatherMessage) {
        log.debug("{} handling message", this.getClass().getName());
        if (lastMeasurement == null) {
            lastMeasurement = fetchLastMeasurement();
        }

        if (lastMessage == null) {
            if (lastMeasurement.time().isBefore(Instant.now().truncatedTo(ChronoUnit.DAYS))) {
                // Last measurement is older than today, just add the daily consumption
                lastMeasurement = new SungatherMeasurement(Instant.now(),
                        lastMeasurement.totalPower() + sungatherMessage.getDailyConsumption());
            }
            // We assume the application was just restarted, don't do anything
        } else {
            final long difference = sungatherMessage.getDailyConsumption() - lastMessage.getDailyConsumption();

            if (difference < 0) {
                // We should have a new day as the daily consumption is reset
                lastMeasurement = new SungatherMeasurement(Instant.now(),
                        lastMeasurement.totalPower() + sungatherMessage.getDailyConsumption());
            } else {
                // Just a new message from the same day
                lastMeasurement = new SungatherMeasurement(Instant.now(), lastMeasurement.totalPower() + difference);
            }
        }
        lastMessage = sungatherMessage;

        try {
            sendMessage(objectMapper.writeValueAsString(new ShellyMessageCurrent(lastMessage.currentLoad())),
                    mqttConfig.getOutboundCurrentTopic());
            sendMessage(objectMapper.writeValueAsString(new ShellyMessageTotal(lastMeasurement.totalPower())),
                    mqttConfig.getOutboundTotalTopic());
        } catch (final MqttException | JsonProcessingException e) {
            log.error("Error while trying to send updates", e);
        }
    }

    private void sendMessage(final String message, final String topic) throws MqttException {
        log.debug("Sending '{}' to topic '{}'", message, topic);
        final MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setPayload(message.getBytes());
        mqttMessage.setQos(1);
        mqttClient.publish(topic, mqttMessage);
    }

    private SungatherMeasurement fetchLastMeasurement() {
        final String flux = """
                from(bucket: "energy")
                  |> range(start: -1y)
                  |> filter(fn: (r) => r["_field"] == "total_act" and r["device"] == "total")
                  |> last()
                """;

        final List<FluxTable> result = influxDBClient.getQueryApi().query(flux);

        if (result.isEmpty()) {
            log.info("This seems to be the first run, no result found in the last year");
            // This should only happen when this is run for the first time or when there's been no entries in the 
            // past year
            return new SungatherMeasurement(Instant.EPOCH, 0.0);
        }

        if (result.size() != 1) {
            throw new IncorrectResultSizeDataAccessException("Received more than one result for a query using last()",
                    1, result.size());
        }

        final List<FluxRecord> records = result.get(0).getRecords();
        if (records.size() != 1) {
            throw new IncorrectResultSizeDataAccessException("Received something other than one single record",
                    1, records.size());
        }

        return new SungatherMeasurement(records.get(0).getTime(), (Double) records.get(0).getValue());
    }
}
