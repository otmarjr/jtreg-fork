/*
 * $Id$
 *
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.javatest.cof;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Reads and stores master test list content
 */
public class MTL {

    private final File mtl;
    private HashMap<String, ArrayList<String>> table;

    MTL(File mtlFile) {
        mtl = mtlFile;
    }

    private void init() {
        if (table == null) {
            table = new HashMap<String, ArrayList<String>>();
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(mtl));
                String line;
                while ((line = r.readLine()) != null) {
                    StringTokenizer st = new StringTokenizer(line);
                    ArrayList<String> cases = new ArrayList<String>();
                    String testName = null;
                    if (st.hasMoreTokens()) {
                        testName = st.nextToken();
                    }
                    while (st.hasMoreTokens()) {
                        cases.add(st.nextToken());
                    }
                    if (testName != null) {
                        table.put(testName, cases);
                    }
                }
            } catch (IOException ex) {
                throw new IllegalArgumentException(ex);
            } finally {
                try {
                    if (r != null)
                        r.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    List<String> getTestCases(String name) {
        init();
        return table.get(name);
    }
}
