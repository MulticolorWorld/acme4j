/*
 * acme4j - Java ACME client
 *
 * Copyright (C) 2016 Richard "Shred" Körber
 *   http://acme4j.shredzone.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package org.shredzone.acme4j.util;

import java.io.UnsupportedEncodingException;
import java.net.IDN;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jose4j.base64url.Base64Url;
import org.jose4j.jwk.EllipticCurveJsonWebKey;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.shredzone.acme4j.exception.AcmeProtocolException;

/**
 * Contains utility methods that are frequently used for the ACME protocol.
 * <p>
 * This class is internal. You may use it in your own code, but be warned that methods may
 * change their signature or disappear without prior announcement.
 */
public final class AcmeUtils {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final Pattern DATE_PATTERN = Pattern.compile(
                    "^(\\d{4})-(\\d{2})-(\\d{2})T"
                  + "(\\d{2}):(\\d{2}):(\\d{2})"
                  + "(?:\\.(\\d{1,3})\\d*)?"
                  + "(Z|[+-]\\d{2}:?\\d{2})$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TZ_PATTERN = Pattern.compile(
                "([+-])(\\d{2}):?(\\d{2})$");

    private AcmeUtils() {
        // Utility class without constructor
    }

    /**
     * Computes a SHA-256 hash of the given string.
     *
     * @param z
     *            String to hash
     * @return Hash
     */
    public static byte[] sha256hash(String z) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(z.getBytes("UTF-8"));
            return md.digest();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new AcmeProtocolException("Could not compute hash", ex);
        }
    }

    /**
     * Hex encodes the given byte array.
     *
     * @param data
     *            byte array to hex encode
     * @return Hex encoded string of the data (with lower case characters)
     */
    public static String hexEncode(byte[] data) {
        char[] result = new char[data.length * 2];
        for (int ix = 0; ix < data.length; ix++) {
            int val = data[ix] & 0xFF;
            result[ix * 2] = HEX[val >>> 4];
            result[ix * 2 + 1] = HEX[val & 0x0F];
        }
        return new String(result);
    }

    /**
     * Base64 encodes the given byte array, using URL style encoding.
     *
     * @param data
     *            byte array to base64 encode
     * @return base64 encoded string
     */
    public static String base64UrlEncode(byte[] data) {
        return Base64Url.encode(data);
    }

    /**
     * Asserts that the given value is not {@code null}. Otherwise a
     * {@link NullPointerException} is thrown.
     *
     * @param value
     *            Value to test
     * @param name
     *            Name of the parameter
     */
    public static void assertNotNull(Object value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
    }

    /**
     * ASCII encodes a domain name.
     * <p>
     * The conversion is done as described in
     * <a href="http://www.ietf.org/rfc/rfc3490.txt">RFC 3490</a>. Additionally, all
     * leading and trailing white spaces are trimmed, and the result is lowercased.
     * <p>
     * It is safe to pass in ACE encoded domains, they will be returned unchanged.
     *
     * @param domain
     *            Domain name to encode
     * @return Encoded domain name, white space trimmed and lower cased. {@code null} if
     *         {@code null} was passed in.
     */
    public static String toAce(String domain) {
        if (domain == null) {
            return null;
        }
        return IDN.toASCII(domain.trim()).toLowerCase();
    }

    /**
     * Analyzes the key used in the {@link JsonWebKey}, and returns the key algorithm
     * identifier for {@link JsonWebSignature}.
     *
     * @param jwk
     *            {@link JsonWebKey} to analyze
     * @return algorithm identifier
     * @throws IllegalArgumentException
     *             there is no corresponding algorithm identifier for the key
     */
    public static String keyAlgorithm(JsonWebKey jwk) {
        if (jwk instanceof EllipticCurveJsonWebKey) {
            EllipticCurveJsonWebKey ecjwk = (EllipticCurveJsonWebKey) jwk;

            switch (ecjwk.getCurveName()) {
                case "P-256":
                    return AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256;

                case "P-384":
                    return AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384;

                case "P-521":
                    return AlgorithmIdentifiers.ECDSA_USING_P521_CURVE_AND_SHA512;

                default:
                    throw new IllegalArgumentException("Unknown EC name "
                        + ecjwk.getCurveName());
            }

        } else if (jwk instanceof RsaJsonWebKey) {
            return AlgorithmIdentifiers.RSA_USING_SHA256;

        } else {
            throw new IllegalArgumentException("Unknown algorithm " + jwk.getAlgorithm());
        }
    }

    /**
     * Parses a RFC 3339 formatted date.
     *
     * @param str
     *            Date string
     * @return {@link Date} that was parsed
     * @throws IllegalArgumentException
     *             if the date string was not RFC 3339 formatted
     * @see <a href="https://www.ietf.org/rfc/rfc3339.txt">RFC 3339</a>
     */
    public static Date parseTimestamp(String str) {
        Matcher m = DATE_PATTERN.matcher(str);
        if (!m.matches()) {
            throw new IllegalArgumentException("Illegal date: " + str);
        }

        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        int dom = Integer.parseInt(m.group(3));
        int hour = Integer.parseInt(m.group(4));
        int minute = Integer.parseInt(m.group(5));
        int second = Integer.parseInt(m.group(6));

        StringBuilder msStr = new StringBuilder();
        if (m.group(7) != null) {
            msStr.append(m.group(7));
        }
        while (msStr.length() < 3) {
            msStr.append('0');
        }
        int ms = Integer.parseInt(msStr.toString());

        String tz = m.group(8);
        if ("Z".equalsIgnoreCase(tz)) {
            tz = "GMT";
        } else {
            tz = TZ_PATTERN.matcher(tz).replaceAll("GMT$1$2:$3");
        }

        Calendar cal = GregorianCalendar.getInstance(TimeZone.getTimeZone(tz));
        cal.clear();
        cal.set(year, month - 1, dom, hour, minute, second);
        cal.set(Calendar.MILLISECOND, ms);
        return cal.getTime();
    }

}
