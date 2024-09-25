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

import com.google.gson.annotations.SerializedName;
import org.filesys.server.filesys.clientapi.ApiRequest;
import org.filesys.server.filesys.clientapi.ApiResponse;

/**
 * Client API Response Class
 *
 * @author gkspencer
 */
public class ClientAPIResponse {

    @SerializedName( value = "type")
    private ApiResponse m_respType;

    /**
     * Class constructor
     *
     * @param resp ApiResponse
     */
    public ClientAPIResponse( ApiResponse resp) {
        m_respType = resp;
    }

    /**
     * Return the response type
     *
     * @return ApiResponse
     */
    public ApiResponse isType() { return m_respType; }
}