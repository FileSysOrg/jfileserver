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
 * Client API Request Id Enum
 *
 * <p>List of basic requests that a client API may implement. The client application will probe the filesystem
 * to see what requests the implementation supports</p>
 *
 * @author gkspencer
 */
public enum ApiRequest {
    GetApiInfo,         // get API version and supported requests
    GetUrlForPath,      // return a URL for the specified path
    GetPathStatus,      // return status for each path for a particular check type
    RunAction,          // run a server side action

    Error               // error type for response only
}
