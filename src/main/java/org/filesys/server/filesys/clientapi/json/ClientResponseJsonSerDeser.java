/*
 * Copyright (C) 2023 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */
package org.filesys.server.filesys.clientapi.json;

import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Client Response JSON Serialize Deserialize Class
 *
 * <p>Deserialize/serialize API response objects to/from JSON</p>
 *
 * @author gkspencer
 */
public class ClientResponseJsonSerDeser implements JsonDeserializer<ClientAPIResponse>, JsonSerializer<ClientAPIResponse> {

    // JSON context for serialization
    private Gson m_gson;

    /**
     * Default constructor
     */
    public ClientResponseJsonSerDeser() {

        // Create the JSON serialization context
        GsonBuilder builder = new GsonBuilder();

        m_gson = builder.create();
    }

    @Override
    public ClientAPIResponse deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
        throws JsonParseException {

        // Do not need to deserialize the client API response on the server
        throw new JsonParseException( "Not implemented");
    }

    @Override
    public JsonElement serialize( ClientAPIResponse respObj, Type type, JsonSerializationContext jsonSerializationContext) {

        // Serialize the main response object
        JsonElement serialize = m_gson.toJsonTree( respObj);
        JsonObject jsonObj = (JsonObject) serialize;

        // Add the object type field
        jsonObj.addProperty("type", respObj.isType().name());

        // Return the serialized JSON object
        return serialize;
    }
}
