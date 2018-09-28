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
 * Find First Flags Class
 *
 * @author gkspencer
 */
class Find {

    //	Find first flags
    protected static final int CloseSearch          = 0x01;
    protected static final int CloseSearchAtEnd     = 0x02;
    protected static final int ResumeKeysRequired   = 0x04;
    protected static final int ContinuePrevious     = 0x08;
    protected static final int BackupIntent         = 0x10;
}
