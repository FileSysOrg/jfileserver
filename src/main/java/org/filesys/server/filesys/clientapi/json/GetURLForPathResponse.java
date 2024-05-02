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

import java.util.EnumSet;

/**
 * Get URL For Path Response Class
 *
 * <p>Contains the response for a get URL for path request</p>
 *
 * @author gkspencer
 */
public class GetURLForPathResponse extends ClientAPIResponse {

    @SerializedName( value = "url")
    private String m_url;

    @SerializedName( value = "alt_url")
    private String m_altUrl;

    /**
     * Class constructor
     *
     * @param url String
     */
    public GetURLForPathResponse(String url) {
        super( ApiResponse.GetUrlForPath);

        m_url = url;
    }

    /**
     * Class constructor
     *
     * @param url String
     * @param altUrl String
     */
    public GetURLForPathResponse(String url, String altUrl) {
        super( ApiResponse.GetUrlForPath);

        m_url = url;
        m_altUrl = altUrl;
    }
}
