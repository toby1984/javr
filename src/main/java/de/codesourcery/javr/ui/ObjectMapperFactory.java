package de.codesourcery.javr.ui;

import java.awt.Color;
import java.io.IOException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ObjectMapperFactory
{
    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();

        // force jackson to only use fields for serialization (default is getter methods)
        final VisibilityChecker<?> checker = OBJECT_MAPPER.getSerializationConfig().getDefaultVisibilityChecker()
            .withFieldVisibility( JsonAutoDetect.Visibility.ANY)
            .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
            .withIsGetterVisibility( JsonAutoDetect.Visibility.NONE)
            .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
            .withCreatorVisibility(JsonAutoDetect.Visibility.NONE);
        OBJECT_MAPPER.setVisibility(checker);

        // sort map entries by key so we can do a simple String#equals()
        // to check two JSON strings for equality
        OBJECT_MAPPER.enable( SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        // serialize enums using toString() instead of name() or ordinal()
        OBJECT_MAPPER.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        OBJECT_MAPPER.enable( DeserializationFeature.READ_ENUMS_USING_TO_STRING);

        // enable serialization of timezone information for dates and intervals/durations
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OBJECT_MAPPER.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);
        OBJECT_MAPPER.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

        // enable serialization support for JDK8 types like Optional,ZonedDateTime etc...
        OBJECT_MAPPER.registerModule(new JavaTimeModule());

        // register custom module
        final SimpleModule module = new SimpleModule();
        module.addDeserializer(Color.class, new ColorDeserializer());
        module.addSerializer(Color.class, new ColorSerializer());
        OBJECT_MAPPER.registerModule(module);

    }
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    private static final class ColorSerializer extends StdSerializer<Color>
    {
        public ColorSerializer() {
            this(null);
        }

        public ColorSerializer(Class<Color> t) {
            super(t);
        }

        @Override
        public void serialize(Color color, JsonGenerator jgen, SerializerProvider serializerProvider) throws IOException
        {
            jgen.writeStartObject();
            jgen.writeNumberField("r", color.getRed());
            jgen.writeNumberField("g", color.getGreen());
            jgen.writeNumberField("b", color.getBlue());
            jgen.writeNumberField("a", color.getAlpha());
            jgen.writeEndObject();
        }
    }

    private static final class ColorDeserializer extends StdDeserializer<Color>
    {

        public ColorDeserializer() {
            this(null);
        }

        public ColorDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public Color deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException
        {
            final JsonNode node = jp.getCodec().readTree(jp);
            final int r = (Integer) node.get("r").numberValue();
            final int g = (Integer) node.get("g").numberValue();
            final int b = (Integer) node.get("b").numberValue();
            final int a = (Integer) node.get("a").numberValue();
            return new Color( r, g, b, a );
        }
    }
}