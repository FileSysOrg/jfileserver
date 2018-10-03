/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.server;

/**
 * Server Versions Class
 *
 * <p>Holds the version strings for various server implementations.
 *
 * @author gkspencer
 */
public class Version {

    // Top level version
    public static String ReleaseVersion         = "1.0.0";

    // Server version strings
    public static String SMBServerVersion       = ReleaseVersion;
    public static String NetBIOSServerVersion   = ReleaseVersion;

    public static String NFSServerVersion       = ReleaseVersion;
    public static String MountServerVersion     = ReleaseVersion;
    public static String PortMapServerVersion   = ReleaseVersion;

    public static String FTPServerVersion       = ReleaseVersion;
}
