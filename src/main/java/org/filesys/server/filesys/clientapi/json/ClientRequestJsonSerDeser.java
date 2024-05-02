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
import org.filesys.server.filesys.clientapi.ApiRequest;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Client API JSON Serialize Deserialize Class
 *
 * <p>Deserialize JSON requests into request objects, and serialize response objects into JSON</p>
 *
 * @author gkspencer
 */
public class ClientRequestJsonSerDeser implements JsonDeserializer<ClientAPIRequest>, JsonSerializer<ClientAPIRequest> {

    // JSON context for serialization
    private Gson m_gson;

    // Mapping of client API request type id names to class
    private Map<ApiRequest, Class<?>> m_requestMap;

    /**
     * Default constructor
     */
    public ClientRequestJsonSerDeser() {

        // Create the JSON serialization context
        GsonBuilder builder = new GsonBuilder();

        m_gson = builder.create();

        // Sync message type name to class mappings
        m_requestMap = new HashMap<>( 16);

        // Client API requests
        m_requestMap.put( ApiRequest.GetApiInfo, GetAPIInfoRequest.class);
        m_requestMap.put( ApiRequest.CheckOutFile, CheckOutFileRequest.class);
        m_requestMap.put( ApiRequest.CheckInFile, CheckInFileRequest.class);
        m_requestMap.put( ApiRequest.GetUrlForPath, GetURLForPathRequest.class);
        m_requestMap.put( ApiRequest.GetPathStatus, GetPathStatusRequest.class);
        m_requestMap.put( ApiRequest.CancelCheckOut, CancelCheckOutFileRequest.class);
    }

    @Override
    public ClientAPIRequest deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
        throws JsonParseException {

        JsonObject jsonObject = jsonElement.getAsJsonObject();

        JsonElement typeElem = jsonObject.get("type");
        String jsonType = typeElem.getAsString();

        // Get the client API request class
        Class<?> reqClass = null;

        try {
            ApiRequest reqType = ApiRequest.valueOf(jsonType);
            reqClass = m_requestMap.get(reqType);
        }
        catch ( IllegalArgumentException ex) {
            throw new JsonParseException("No mapping for type " + jsonType);
        }

        // Create the client API request object
        ClientAPIRequest reqObj = null;

        try {
            reqObj = ( ClientAPIRequest) reqClass.getDeclaredConstructor().newInstance();
        }
        catch ( Exception ex) {
            throw new JsonParseException( "Error creating class for type " + jsonType, ex);
        }

        // Load the request values from the JSON message
        reqObj.fromJSON( jsonObject);
        return reqObj;
    }

    @Override
    public JsonElement serialize( ClientAPIRequest reqObj, Type type, JsonSerializationContext jsonSerializationContext) {

        // Serialize the main request object
        JsonElement serialize = m_gson.toJsonTree( reqObj);
        JsonObject jsonObj = (JsonObject) serialize;

        // Add the object type field
        jsonObj.addProperty("type", reqObj.isType().name());

        // Return the serialized JSON object
        return serialize;
    }
}
