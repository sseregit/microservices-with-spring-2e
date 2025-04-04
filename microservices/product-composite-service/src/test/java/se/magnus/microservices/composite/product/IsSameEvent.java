package se.magnus.microservices.composite.product;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;
import se.magnus.api.event.Event;

@Slf4j
public class IsSameEvent extends TypeSafeMatcher<String> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Event expectedEvent;

    private IsSameEvent(Event expectedEvent) {
        this.expectedEvent = expectedEvent;
        mapper.registerModule(new JavaTimeModule());
    }

    public static Matcher<String> sameEventExceptCreatedAt(Event expectedEvent) {
        return new IsSameEvent(expectedEvent);
    }

    @Override
    protected boolean matchesSafely(String evetAsJson) {

        if (expectedEvent == null) {
            return false;
        }

        log.trace("Convert the following json string to a map: {}", evetAsJson);
        Map mapEvent = convertJsonStringToMap(evetAsJson);
        mapEvent.remove("eventCreatedAt");

        Map mapExpectedEvent = getMapWithoutCreatedAt(expectedEvent);

        log.trace("Got the map: {}", mapEvent);
        log.trace("Compare to the expected map: {}", mapExpectedEvent);
        return mapEvent.equals(mapExpectedEvent);
    }

    private Map getMapWithoutCreatedAt(Event event) {
        Map mapEvent = convertObjectToMap(event);
        mapEvent.remove("eventCreatedAt");
        return mapEvent;
    }

    private Map convertObjectToMap(Object object) {
        JsonNode node = mapper.convertValue(object, JsonNode.class);
        return mapper.convertValue(node, Map.class);
    }

    private Map convertJsonStringToMap(String evetAsJson) {
        try {
            return mapper.readValue(evetAsJson, new TypeReference<HashMap>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void describeTo(Description description) {

    }
}
