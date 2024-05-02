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

package org.filesys.server.filesys.clientapi;

/**
 * Client API Response Id Enum
 *
 * <p>List of responses that a client API may return.</p>
 *
 * @author gkspencer
 */
public enum ApiResponse {
    GetApiInfo,         // return server API version and supported requests
    CheckOutFile,       // checked a file out
    CheckInFile,        // checked a file in
    GetUrlForPath,      // return a URL for the specified path
    GetPathStatus,      // return path status info
    PathList,           // success response with a list of relative paths

    Error,              // error message
    Success             // when no values need to be returned
}
