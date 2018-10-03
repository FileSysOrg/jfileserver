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

package org.filesys.smb;

import java.util.BitSet;

/**
 * SMB dialect selector class.
 *
 * <p>Used to select the SMB dialects that a client can use when communicating with a remote server. The dialect list is used by
 * the client/server to agree the protocol level during the initial SMB negotiation phase.
 *
 * @author gkspencer
 */
public class DialectSelector {

    // Dialect groupings
    public enum DialectGroup{
        SMBv1,
        SMBv2,
        SMBv3
    }

    // Bit set of selected SMB dialects.
    private BitSet m_dialects;

    /**
     * Construct a new SMB dialect selector
     */
    public DialectSelector() {
        m_dialects = new BitSet(Dialect.Max);
    }

    /**
     * Copy constructor
     *
     * @param diaSel DialectSelector
     */
    public DialectSelector( DialectSelector diaSel) {
        copyFrom( diaSel);
    }

    /**
     * Add a dialect to the list of available SMB dialects.
     *
     * @param idx Index of the dialect to add to the available dialects.
     * @throws ArrayIndexOutOfBoundsException Invalid dialect index.
     */
    public void AddDialect(int idx)
            throws ArrayIndexOutOfBoundsException {
        m_dialects.set(idx);
    }

    /**
     * Clear all the dialect bits.
     */
    public void ClearAll() {
        m_dialects.clear();
    }

    /**
     * Set all available dialects
     */
    public void enableAll() {
        m_dialects.set(0, Dialect.Max);
    }

    /**
     * Enable up to a particular dialect level
     *
     * @param maxDialect int
     */
    public void enableUpTo(int maxDialect) {
        if ( maxDialect < 0 || maxDialect > Dialect.Max + 1)
            maxDialect = Dialect.UpToSMBv1;

        m_dialects.set(0, maxDialect);
    }

    /**
     * Copy the SMB dialect selector settings.
     *
     * @param dsel DialectSelector
     */
    public void copyFrom(DialectSelector dsel) {

        // Clone the current dialect selector
        m_dialects = (BitSet) dsel.getBitSet().clone();
    }

    /**
     * Determine if the specified SMB dialect is selected/available.
     *
     * @param idx Index of the dialect to test for.
     * @return true if the SMB dialect is available, else false.
     * @throws ArrayIndexOutOfBoundsException Invalid dialect index.
     */
    public boolean hasDialect(int idx)
            throws ArrayIndexOutOfBoundsException {
        return m_dialects.get(idx);
    }

    /**
     * Determine if the core SMB dialect is enabled
     *
     * @return boolean
     */
    public boolean hasCore() {
        if (hasDialect(Dialect.Core) || hasDialect(Dialect.CorePlus))
            return true;
        return false;
    }

    /**
     * Determine if the LanMan SMB dialect is enabled
     *
     * @return boolean
     */
    public boolean hasLanMan() {
        if (hasDialect(Dialect.DOSLanMan1) || hasDialect(Dialect.DOSLanMan2) ||
                hasDialect(Dialect.LanMan1) || hasDialect(Dialect.LanMan2) ||
                hasDialect(Dialect.LanMan2_1))
            return true;
        return false;
    }

    /**
     * Determine if the NT SMB dialect is enabled
     *
     * @return boolean
     */
    public boolean hasNT() {
        if (hasDialect(Dialect.NT))
            return true;
        return false;
    }

    /**
     * Check if the SMB v2 dialect is enabled
     *
     * @return boolean
     */
    public boolean hasSMB2() {
        if ( hasDialect( Dialect.SMB2_Any) || hasDialect( Dialect.SMB2_202))
            return true;
        return false;
    }

    /**
     * Check if the SMB v3 dialect is enabled
     *
     * @return boolean
     */
    public boolean hasSMB3() {
        if ( hasDialect( Dialect.SMB3_300) || hasDialect( Dialect.SMB3_302) || hasDialect( Dialect.SMB3_311))
            return true;
        return false;
    }

    /**
     * Remove an SMB dialect from the list of available dialects.
     *
     * @param idx Index of the dialect to remove.
     * @throws java.lang.ArrayIndexOutOfBoundsException Invalid dialect index.
     */
    public void RemoveDialect(int idx)
            throws ArrayIndexOutOfBoundsException {
        m_dialects.clear(idx);
    }

    /**
     * Find the highest matching index of the requested dialect selector and this dialect selector
     *
     * @param reqSelector DialectSelector
     * @return int
     */
    public final int findHighestDialect( DialectSelector reqSelector) {

        for ( int idx = Dialect.Max; idx >= 0; idx--) {
            if (  hasDialect( idx) && reqSelector.hasDialect( idx))
                return idx;
        }

        return -1;
    }

    /**
     * Check if the dialect set is empty
     *
     * @return boolean
     */
    public final boolean isEmpty() {
        return m_dialects.isEmpty();
    }

    /**
     * Return the BitSet
     *
     * @return BitSet
     */
    protected BitSet getBitSet() {
        return m_dialects;
    }

    /**
     * Mask this set of dialects with the specified set of dialects, updating this set
     *
     * @param maskSet DialectSelector
     */
    public final void maskWith( DialectSelector maskSet) {
        m_dialects.and( maskSet.getBitSet());
    }

    /**
     * Enable the specified dialect group
     *
     * @param diaGroup DialectGroup
     */
    public final void enableGroup( DialectGroup diaGroup) {

        switch ( diaGroup) {
            case SMBv1:
                m_dialects.set(Dialect.Core, Dialect.NT + 1);
                break;
            case SMBv2:
                m_dialects.set(Dialect.SMB2_202, Dialect.SMB2_Any + 1);
                break;
            case SMBv3:
                m_dialects.set(Dialect.SMB3_300, Dialect.SMB3_311 + 1);
                break;
        }
    }

    /**
     * Disable the specified dialect group
     *
     * @param diaGroup DialectGroup
     */
    public final void disableGroup( DialectGroup diaGroup) {

        switch ( diaGroup) {
            case SMBv1:
                m_dialects.set(Dialect.Core, Dialect.NT + 1, false);
                break;
            case SMBv2:
                m_dialects.set(Dialect.SMB2_202, Dialect.SMB2_Any + 1, false);
                break;
            case SMBv3:
                m_dialects.set(Dialect.SMB3_300, Dialect.SMB3_311 + 1, false);
                break;
        }
    }

    /**
     * Return the dialect selector list as a string.
     *
     * @return String
     */
    public String toString() {

        //  Create a string buffer to build the return string
        StringBuffer str = new StringBuffer();
        str.append("[");

        for (int i = 0; i < m_dialects.size(); i++) {
            if (hasDialect(i)) {
                str.append(Dialect.DialectTypeString(i));
                str.append(",");
            }
        }

        //  Trim the last comma and return the string
        if (str.length() > 1)
            str.setLength(str.length() - 1);
        str.append("]");
        return str.toString();
    }
}
