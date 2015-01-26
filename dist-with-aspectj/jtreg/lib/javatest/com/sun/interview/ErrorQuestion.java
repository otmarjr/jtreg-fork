/*
 * $Id$
 *
 * Copyright (c) 1996, 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.interview;

/**
 * A "null" question with no response. In effect, this
 * posts the text, which must simply be acknowledged.
 * ErrorQuestions have no successor.
 */
public class ErrorQuestion extends NullQuestion
{
    /**
     * Create a question with a nominated tag.
     * @param interview The interview containing this question.
     * @param tag A unique tag to identify this specific question.
     */
    public ErrorQuestion(Interview interview, String tag) {
        super(interview, tag);
    }

    public final Question getNext() {
        return null;
    }

    public boolean isValueValid() {
        return false;
    }

    public boolean isValueAlwaysValid() {
        return false;
    }

    /*
     * Returns the MIME type for the text retutned
     * by getText() method.
     * Supported types are "text/plain" and "text/html"
     */
    public String getTextMimeType() {
        return "text/plain";
    }
}
