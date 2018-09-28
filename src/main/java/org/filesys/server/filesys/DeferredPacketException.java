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

package org.filesys.server.filesys;

import java.io.IOException;

/**
 * Deferred Packet Exception Class
 *
 * <p>Indicates that the processing of a file server request has been deferred whilst other processing
 * is done, and the request packet should not be released.
 *
 * @author gkspencer
 */
public class DeferredPacketException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public DeferredPacketException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s String
     */
    public DeferredPacketException(String s) {
        super(s);
    }
}
