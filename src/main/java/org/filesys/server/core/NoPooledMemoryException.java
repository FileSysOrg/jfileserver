/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
package org.filesys.server.core;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * No Pooled Memory Exception Class
 *
 * <p>Indicates that no buffers are available in the global memory pool or per protocol pool.
 *
 * @author gkspencer
 */
public class NoPooledMemoryException extends IOException {

    private static final long serialVersionUID = 6852939454477894406L;

    // Keep a global count of how many times this error is used
    private static AtomicLong _exceptionCount = new AtomicLong();

    /**
     * Default constructor
     */
    public NoPooledMemoryException() {
        super();

        // Increment the global count
        _exceptionCount.incrementAndGet();
    }

    /**
     * Class constructor
     *
     * @param s String
     */
    public NoPooledMemoryException(String s) {
        super(s);

        // Increment the global count
        _exceptionCount.incrementAndGet();
    }

    /**
     * Return the exception counter
     *
     * @return long
     */
    public static long getExceptionCounter() {
        return _exceptionCount.get();
    }
}
