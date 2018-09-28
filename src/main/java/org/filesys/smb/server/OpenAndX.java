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

package org.filesys.smb.server;

/**
 * OpenAndX Flags Class
 *
 * @author gkspencer
 */
class OpenAndX {

    //	File types, for OpenAndX
    protected static final int FileTypeDisk     = 0;
    protected static final int FileTypeBytePipe = 1;
    protected static final int FileTypeMsgPipe  = 2;
    protected static final int FileTypePrinter  = 3;
    protected static final int FileTypeUnknown  = 0xFFFF;
}
