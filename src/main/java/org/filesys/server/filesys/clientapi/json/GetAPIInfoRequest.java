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

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.filesys.server.filesys.clientapi.ApiRequest;

/**
 * Get API Info Client API Request Class
 *
 * @author gkspencer
 */
public class GetAPIInfoRequest extends ClientAPIRequest {

    @SerializedName(value = "client_version")
    private String m_clientVer;

    /**
     * Default constructor
     */
    public GetAPIInfoRequest() {
    }

    /**
     * Return the client version
     *
     * @return String
     */
    public final String getClientVersion() {
        return m_clientVer;
    }

    @Override
    public ApiRequest isType() {
        return ApiRequest.GetApiInfo;
    }

    @Override
    public void fromJSON(JsonObject jsonObj) throws JsonParseException {

        // Load the client version
        m_clientVer = jsonObj.get("client_version").getAsString();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Request=");
        str.append(isType());
        str.append(",client_ver=");
        str.append(getClientVersion());
        str.append("]");

        return str.toString();
    }
}
