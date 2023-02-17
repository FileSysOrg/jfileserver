/*
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.server.config;

/**
 * Licence Configuration Section Class
 *
 * <p>Contains the licence details for an Enterprise file server setup</p>
 *
 * @author gkspencer
 */
public class LicenceConfigSection extends ConfigSection {

    // Core server configuration section name
    public static final String SectionName = "Licence";

    // Licence key
    private String m_licenceKey;

    // Product edition
    private String m_edition = "Standalone";

    // Default public key
    private byte [] m_publicKey = {
            (byte)0x52,
            (byte)0x53, (byte)0x41, (byte)0x00, (byte)0x30, (byte)0x82, (byte)0x01, (byte)0x22, (byte)0x30,
            (byte)0x0D, (byte)0x06, (byte)0x09, (byte)0x2A, (byte)0x86, (byte)0x48, (byte)0x86, (byte)0xF7,
            (byte)0x0D, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x05, (byte)0x00, (byte)0x03, (byte)0x82,
            (byte)0x01, (byte)0x0F, (byte)0x00, (byte)0x30, (byte)0x82, (byte)0x01, (byte)0x0A, (byte)0x02,
            (byte)0x82, (byte)0x01, (byte)0x01, (byte)0x00, (byte)0x9E, (byte)0x06, (byte)0xFB, (byte)0x7C,
            (byte)0x2E, (byte)0xB1, (byte)0x97, (byte)0xD3, (byte)0x7C, (byte)0x25, (byte)0xCB, (byte)0x89,
            (byte)0x47, (byte)0x78, (byte)0xD2, (byte)0x47, (byte)0xB3, (byte)0xFA, (byte)0x7E, (byte)0x79,
            (byte)0x7A, (byte)0x0A, (byte)0x5B, (byte)0x7E, (byte)0xA1, (byte)0x70, (byte)0xA2, (byte)0x4D,
            (byte)0xB0, (byte)0x13, (byte)0x16, (byte)0xD7, (byte)0x5A, (byte)0xA7, (byte)0xC3, (byte)0xC1,
            (byte)0x5A, (byte)0xE5, (byte)0x53, (byte)0x79, (byte)0xF0, (byte)0x62, (byte)0x7C, (byte)0x0D,
            (byte)0x0F, (byte)0x6A, (byte)0x6D, (byte)0xDB, (byte)0x27, (byte)0x29, (byte)0x96, (byte)0x44,
            (byte)0x66, (byte)0x42, (byte)0x9E, (byte)0xE0, (byte)0xD8, (byte)0xA0, (byte)0x12, (byte)0xEA,
            (byte)0x39, (byte)0x36, (byte)0xCD, (byte)0x4C, (byte)0x3F, (byte)0x18, (byte)0x58, (byte)0xCE,
            (byte)0xE9, (byte)0xB7, (byte)0x46, (byte)0x38, (byte)0x06, (byte)0xB1, (byte)0xE7, (byte)0x09,
            (byte)0x82, (byte)0x1A, (byte)0x65, (byte)0x71, (byte)0x2E, (byte)0x51, (byte)0x44, (byte)0x44,
            (byte)0x2F, (byte)0x43, (byte)0x04, (byte)0xE3, (byte)0xE0, (byte)0x8C, (byte)0x50, (byte)0x2A,
            (byte)0x7F, (byte)0x19, (byte)0x29, (byte)0x14, (byte)0x1A, (byte)0xDB, (byte)0x39, (byte)0x76,
            (byte)0x62, (byte)0x83, (byte)0x34, (byte)0xB4, (byte)0x4D, (byte)0xF9, (byte)0x53, (byte)0xA1,
            (byte)0x26, (byte)0xFE, (byte)0xDE, (byte)0x73, (byte)0xEC, (byte)0x94, (byte)0x2A, (byte)0x33,
            (byte)0xC2, (byte)0xFE, (byte)0x6A, (byte)0x83, (byte)0x73, (byte)0xCF, (byte)0x43, (byte)0x63,
            (byte)0xF3, (byte)0x86, (byte)0xD5, (byte)0x8D, (byte)0xA1, (byte)0x65, (byte)0xFE, (byte)0xAC,
            (byte)0xB6, (byte)0xAE, (byte)0xD0, (byte)0xB1, (byte)0x11, (byte)0x24, (byte)0x90, (byte)0x60,
            (byte)0xFA, (byte)0xAF, (byte)0x61, (byte)0x8D, (byte)0xDA, (byte)0xAA, (byte)0xD9, (byte)0xF9,
            (byte)0xD4, (byte)0xF4, (byte)0xF2, (byte)0xA3, (byte)0xB5, (byte)0x3B, (byte)0xF4, (byte)0x0C,
            (byte)0xBC, (byte)0xA1, (byte)0x0D, (byte)0x1C, (byte)0x9F, (byte)0x99, (byte)0x56, (byte)0xD7,
            (byte)0xD0, (byte)0xEA, (byte)0x88, (byte)0xFF, (byte)0x02, (byte)0xEF, (byte)0x6C, (byte)0xE5,
            (byte)0x2A, (byte)0xBC, (byte)0xB5, (byte)0xA2, (byte)0xCE, (byte)0x3A, (byte)0xD9, (byte)0x1A,
            (byte)0xFC, (byte)0x47, (byte)0xA5, (byte)0x58, (byte)0x5F, (byte)0x25, (byte)0xD1, (byte)0xB0,
            (byte)0x0E, (byte)0x54, (byte)0x5F, (byte)0x09, (byte)0x59, (byte)0x49, (byte)0x5A, (byte)0x34,
            (byte)0x22, (byte)0x6D, (byte)0x85, (byte)0x38, (byte)0x60, (byte)0x05, (byte)0x57, (byte)0x24,
            (byte)0x1F, (byte)0xA6, (byte)0x5F, (byte)0x23, (byte)0x16, (byte)0xC5, (byte)0x29, (byte)0x4D,
            (byte)0x9C, (byte)0x29, (byte)0xA6, (byte)0x98, (byte)0x4D, (byte)0x93, (byte)0x7B, (byte)0x70,
            (byte)0xCD, (byte)0x8F, (byte)0x2E, (byte)0x01, (byte)0x26, (byte)0x77, (byte)0x25, (byte)0x2A,
            (byte)0x4A, (byte)0x69, (byte)0x0E, (byte)0x8A, (byte)0x72, (byte)0xC2, (byte)0x44, (byte)0xB1,
            (byte)0x9E, (byte)0xF3, (byte)0x9E, (byte)0x6B, (byte)0x99, (byte)0x09, (byte)0x04, (byte)0x2C,
            (byte)0x9B, (byte)0xD1, (byte)0xA8, (byte)0x68, (byte)0x6E, (byte)0x71, (byte)0xEC, (byte)0xCF,
            (byte)0xEE, (byte)0xE8, (byte)0x1A, (byte)0x61, (byte)0x02, (byte)0x03, (byte)0x01, (byte)0x00,
            (byte)0x01,
    };

    // Licence announcement has been output
    private boolean m_licAnnounce;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public LicenceConfigSection(ServerConfiguration config) {
        super( SectionName, config);
    }

    /**
     * Check if the licence key is valid
     *
     * @return boolean
     */
    public final boolean hasLicenceKey() {
        return m_licenceKey != null && m_licenceKey.length() > 0;
    }

    /**
     * Return the licence key
     *
     * @return String
     */
    public final String getLicenceKey() {
        return m_licenceKey;
    }

    /**
     * Get the product edition
     *
     * @return String
     */
    public final String getProductEdition() { return m_edition; }

    /**
     * Get the public key
     *
     * @return byte[]
     */
    public final byte[] getPublicKey() { return m_publicKey; }

    /**
     * Check if the licence announcement has been output during startup
     *
     * @return boolean
     */
    public final boolean doneAnnouncement() { return m_licAnnounce; }

    /**
     * Set the licence key
     *
     * @param licKey String
     */
    public final void setLicenceKey(String licKey) {
        m_licenceKey = licKey;

        // Remove any embedded carriage returns
        m_licenceKey = m_licenceKey.replace( "\n", "");
        m_licenceKey = m_licenceKey.replace( "\r", "");
    }

    /**
     * Set the product edition
     *
     * @param edition String
     */
    public final void setProductEdition(String edition) { m_edition = edition; }

    /**
     * Set the public key
     *
     * @param pubKey byte[]
     */
    public final void setPublicKey(byte[] pubKey) { m_publicKey = pubKey; }

    /**
     * Set the licence announcement has been output flag
     */
    public final void setAnnouncementDone() { m_licAnnounce = true; }
}
