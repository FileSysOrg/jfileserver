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
 * Get URL For Path Request Class
 *
 * @author gkspencer
 */
public class GetURLForPathRequest extends ClientAPIRequest {

    @SerializedName(value = "relative_path")
    private String m_relPath;

    /**
     * Default constructor
     */
    public GetURLForPathRequest() {
    }

    /**
     * Return the relative path
     *
     * @return String
     */
    public final String getRelativePath() {
        return m_relPath;
    }

    @Override
    public ApiRequest isType() {
        return ApiRequest.GetUrlForPath;
    }

    @Override
    public void fromJSON(JsonObject jsonObj) throws JsonParseException {

        // Load the relative path
        m_relPath = jsonObj.get("relative_path").getAsString();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Request=");
        str.append(isType());
        str.append(",rel_path=");
        str.append(getRelativePath());
        str.append("]");

        return str.toString();
    }
}
