package com.nexusivr.ai.model.flow;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class FlowNodeTypeAdapter extends TypeAdapter<FlowNodeType> {
    @Override
    public void write(JsonWriter out, FlowNodeType value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value.getBuilderType());
    }

    @Override
    public FlowNodeType read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String value = in.nextString();
        return FlowNodeType.fromString(value);
    }
}
