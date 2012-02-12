/*
 * @(#)ParseException.java  1.1  2011-08-25
 *
 * Copyright (c) 1999-2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.media;

/**
Exception thrown by IFFParse.

@author  Werner Randelshofer, Hausmatt 10, CH-6405 Immensee, Switzerland
@version  1.1 2011-08-25 Adds constructor with cause.
 * <br>1.0  1999-10-19
 */
public class ParseException extends Exception {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
