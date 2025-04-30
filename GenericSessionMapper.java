package com.example.session.mapper;

import java.time.Instant;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.session.MapSession;

public class GenericSessionMapper implements BiFunction<String, Map<String, Object>, MapSession> {

    private static final String CREATION_TIME_KEY = "creationTime";
    private static final String ATTRIBUTE_PREFIX = "sessionAttr:";

    @Override
    public MapSession apply(String sessionId, Map<String, Object> attributes) {
        MapSession session = new MapSession(sessionId);
        Object creationTimeObj = attributes.get(CREATION_TIME_KEY);

        if (creationTimeObj instanceof Long creationTime) {
            session.setCreationTime(Instant.ofEpochMilli(creationTime));
        } else {
            session.setCreationTime(Instant.now());
        }

        attributes.forEach((key, value) -> {
            if (key.startsWith(ATTRIBUTE_PREFIX)) {
                session.setAttribute(key.substring(ATTRIBUTE_PREFIX.length()), value);
            }
        });

        return session;
    }

}
