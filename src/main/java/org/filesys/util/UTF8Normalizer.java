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

package org.filesys.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * UTF-8 Normalize Class
 *
 * @author gkspencer
 */
public class UTF8Normalizer {

    // Normalizer method type
    public enum NormalizerType {
        Unknown, Java5, Java6or7, IBMICU
    };

    // Type of normalizer method
    private NormalizerType m_type = NormalizerType.Unknown;

    // Normalizer method
    private Method m_method;

    // Normalizer form parameter field for Java 6 call
    private Field m_field;

    /**
     * Default constructor
     */
    public UTF8Normalizer() {
        initNormalizer();

        if (isType() == NormalizerType.Unknown)
            throw new RuntimeException("UTf8Normalizer failed to initialize");
    }

    /**
     * Normalize a UTF-8 string
     *
     * @param utf8str String
     * @return String
     */
    public final String normalize(String utf8str) {

        // Determine the method to be called
        String normStr = null;

        try {
            switch (isType()) {

                // IBM ICU library
                case IBMICU:

                    // Call the compose(String, boolean) method
                    normStr = (String) m_method.invoke(null, utf8str, false);
                    break;

                // Java5
                case Java5:

                    // Call the compose(String, boolean, int) method
                    normStr = (String) m_method.invoke(null, utf8str, false, 0);
                    break;

                // Java6 or 7
                case Java6or7:

                    // Call the normalize(CharSequence, Normalizer.Form) method
                    normStr = (String) m_method.invoke(null, utf8str, m_field.get(null));
                    break;

                // Not initialized
                case Unknown:
                    throw new RuntimeException("Normalizer is not initialized");
            }
        }
        catch (InvocationTargetException ex) {
        }
        catch (IllegalAccessException ex) {
        }

        // Return the normalized string
        return normStr;
    }

    /**
     * Return the normalizer type
     *
     * @return Normalizer.Type
     */
    public final NormalizerType isType() {
        return m_type;
    }

    /**
     * Initialize the normalizer
     */
    private final void initNormalizer() {

        // Check if the IBM ICU library is available
        try {

            // Load the IBM ICU class
            Class<?> icuClass = Class.forName("com.ibm.icu.text.Normalizer");

            // Find the compose method
            Class<?>[] paramTypes = new Class<?>[2];
            paramTypes[0] = String.class;
            paramTypes[1] = boolean.class;

            m_method = icuClass.getMethod("compose", paramTypes);

            // Check if the method is valid
            if (m_method != null) {
                m_type = NormalizerType.IBMICU;
                return;
            }
        }
        catch (ClassNotFoundException ex) {
        }
        catch (NoSuchMethodException ex) {
        }

        // Check the Java version and use the appropriate method
        String javaVer = System.getProperty("java.specification.version");

        if (javaVer.equals("1.5")) {

            try {

                // Load the sun.text.Normalizer class
                Class<?> sunClass = Class.forName("sun.text.Normalizer");

                // Find the compose method
                Class<?>[] paramTypes = new Class<?>[3];
                paramTypes[0] = String.class;
                paramTypes[1] = boolean.class;
                paramTypes[2] = int.class;

                m_method = sunClass.getMethod("compose", paramTypes);

                // Check if the method is valid
                if (m_method != null) {
                    m_type = NormalizerType.Java5;
                    return;
                }
            }
            catch (ClassNotFoundException ex) {
            }
            catch (NoSuchMethodException ex) {
            }
        }
        else if (javaVer.equals("1.6") || javaVer.equals("1.7") || javaVer.equals("1.8")) {

            try {

                // Load the java.text.Normalizer class
                Class<?> java6Class = Class.forName("java.text.Normalizer");

                // Load the Normalizer.Form class, used as a parameter
                Class<?> normFormClass = Class.forName("java.text.Normalizer$Form");

                // Get the required Normalizer.Form value
                m_field = normFormClass.getField("NFC");

                // Find the compose method
                Class<?>[] paramTypes = new Class<?>[2];
                paramTypes[0] = CharSequence.class;
                paramTypes[1] = normFormClass;

                m_method = java6Class.getMethod("normalize", paramTypes);

                // Check if the method is valid
                if (m_method != null) {
                    m_type = NormalizerType.Java6or7;
                    return;
                }
            }
            catch (ClassNotFoundException ex) {
            }
            catch (NoSuchMethodException ex) {
            }
            catch (NoSuchFieldException ex) {
            }
        }
    }
}
