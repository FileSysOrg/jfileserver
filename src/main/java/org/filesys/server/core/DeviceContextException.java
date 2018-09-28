/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
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
package org.filesys.server.core;

/**
 * Device Context Exception Class
 * <p>
 * Thrown when a device context parameter string is invalid.
 *
 * @author gkspencer
 */
public class DeviceContextException extends Exception {

    private static final long serialVersionUID = -2282554065425234959L;

    /**
     * Class constructor
     */
    public DeviceContextException() {
        super();
    }

    /**
     * Class constructor
     *
     * @param s java.lang.String
     */
    public DeviceContextException(String s) {
        super(s);
    }

    /**
     * Class constructor
     *
     * @param s     java.lang.String
     * @param cause Throwable
     */
    public DeviceContextException(String s, Throwable cause) {
        super(s, cause);
    }
}
