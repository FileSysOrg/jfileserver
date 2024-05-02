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
import org.filesys.server.filesys.clientapi.ApiResponse;

/**
 * Error Response Class
 *
 * <p>Contains details of an error returned by a client API call</p>
 *
 * @author gkspencer
 */
public class ErrorResponse extends ClientAPIResponse {

    @SerializedName( value = "error_msg")
    private String m_errMsg;

    @SerializedName( value = "warning")
    private boolean m_warning;

    @SerializedName( value = "path")
    private String m_path;

    /**
     * Class constructor
     *
     * @param msg String
     */
    public ErrorResponse( String msg) {
        super( ApiResponse.Error);

        m_errMsg  = msg;
        m_warning = false;
    }

    /**
     * Class constructor
     *
     * @param msg String
     * @param warning boolean
     */
    public ErrorResponse( String msg, boolean warning) {
        super( ApiResponse.Error);

        m_errMsg  = msg;
        m_warning = warning;
    }

    /**
     * Class constructor
     *
     * @param msg String
     * @param path Stirng
     * @param warning boolean
     */
    public ErrorResponse( String msg, String path, boolean warning) {
        super( ApiResponse.Error);

        m_errMsg  = msg;
        m_path    = path;
        m_warning = warning;
    }

    /**
     * Class constructor
     *
     * @param apiEx ClientAPIException
     */
    public ErrorResponse( ClientAPIException apiEx) {
        super( ApiResponse.Error);

        m_errMsg  = apiEx.getMessage();
        m_path    = apiEx.getPath();
        m_warning = apiEx.isWarning();
    }
}
