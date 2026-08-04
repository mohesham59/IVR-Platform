package com.nexusivr.ai.model.flow;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Custom Gson {@link TypeAdapter} for {@link FlowNodeType}.
 * <p>
 * Ensures string node types sent from the frontend builder (e.g. "greeting", "dtmf_menu",
 * "hours", "extension", "record", etc.) deserialize cleanly to their corresponding
 * {@link FlowNodeType} enum values instead of evaluating to null.
 */
public class FlowNodeTypeAdapter extends TypeAdapter<FlowNodeType> {

    @Override
    public void write(JsonWriter out, FlowNodeType value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.name().toLowerCase());
        }
    }

    @Override
    public FlowNodeType read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String str = in.nextString();
        FlowNodeType type = FlowNodeType.fromString(str);
        if (type == null && str != null && !str.isBlank()) {
            try {
                type = FlowNodeType.valueOf(str.toUpperCase().trim());
            } catch (Exception ignored) {}
        }
        return type;
    }
}
