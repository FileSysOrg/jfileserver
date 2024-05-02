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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.filesys.server.filesys.clientapi.ApiRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Check In File API Request Class
 *
 * @author gkspencer
 */
public class CheckInFileRequest extends ClientAPIRequest {

    @SerializedName(value = "relative_paths")
    private List<String> m_relPaths;

    @SerializedName(value = "comment")
    private String m_comment;

    /**
     * Default constructor
     */
    public CheckInFileRequest() {
    }

    /**
     * Return the relative path list
     *
     * @return List&lt;String&gt;
     */
    public final List<String> getRelativePaths() {
        return m_relPaths;
    }

    /**
     * Return the check in comment
     *
     * @return String
     */
    public String getComment() { return m_comment; }

    @Override
    public ApiRequest isType() {
        return ApiRequest.CheckInFile;
    }

    @Override
    public void fromJSON(JsonObject jsonObj) throws JsonParseException {

        // Load the relative paths list
        JsonArray elems = jsonObj.get("relative_paths").getAsJsonArray();

        // Convert the paths to a list
        m_relPaths = new ArrayList<>(elems.size());

        for (JsonElement curElem : elems) {
            m_relPaths.add(curElem.getAsString());
        }

        // Load the check in comment
        m_comment = jsonObj.get("comment").getAsString();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Request=");
        str.append(isType());
        str.append(",rel_paths=");
        str.append(getRelativePaths());
        str.append(",comment=");
        str.append(getComment());
        str.append("]");

        return str.toString();
    }
}
