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

import com.google.gson.annotations.SerializedName;
import org.filesys.server.filesys.clientapi.ApiResponse;

import java.util.List;

/**
 * Path List Response Class
 *
 * <p>API response that returns a list of relative paths to the client</p>
 *
 * @author gkspencer
 */
public class PathListResponse extends ClientAPIResponse {

    @SerializedName( value = "relative_paths")
    private List<String> m_pathList;

    /**
     * Class constructor
     *
     * @param pathList List&lt;String&gt;
     */
    public PathListResponse( List<String> pathList) {
        super( ApiResponse.PathList);

        m_pathList = pathList;
    }

    /**
     * Return the response details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append( "[PathList paths=");
        str.append( m_pathList);
        str.append( "]");

        return str.toString();
    }
}
