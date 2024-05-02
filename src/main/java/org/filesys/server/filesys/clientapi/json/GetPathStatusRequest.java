/*
 * Copyright (C) 2024 GK Spencer
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
 * Get File Status API Request Class
 *
 * <p>Request file status for a list of paths, the check type indicates what check is to be done against each path, such
 * as whether the path is locked</p>
 *
 * @author gkspencer
 */
public class GetPathStatusRequest extends ClientAPIRequest {

    @SerializedName(value = "relative_paths")
    private List<String> m_relPaths;

    @SerializedName(value = "check_type")
    private String m_chkType;
    /**
     * Default constructor
     */
    public GetPathStatusRequest() {
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
     * Return the type of file detail to be checked
     *
     * @return String
     */
    public String getCheckType() { return m_chkType; }

    @Override
    public ApiRequest isType() {
        return ApiRequest.GetPathStatus;
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

        // Load the check type
        m_chkType = jsonObj.get("check_type").getAsString();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Request=");
        str.append(isType());
        str.append(",rel_paths=");
        str.append(getRelativePaths());
        str.append(",chkType=");
        str.append(getCheckType());
        str.append("]");

        return str.toString();
    }
}
