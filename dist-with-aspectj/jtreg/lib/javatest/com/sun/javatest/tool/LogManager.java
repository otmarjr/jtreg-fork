/*
 * $Id$
 *
 * Copyright (c) 2004, 2009, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.javatest.tool;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ListIterator;

import com.sun.javatest.util.HelpTree;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * A command manager to handle the command line options for
 * controlling logging messages generated by the harness.
 */
public class LogManager extends CommandManager
{
    public HelpTree.Node getHelp() {
        HelpTree.Node[] cmdNodes = {
            getCommandHelp(LogCommand.getName()),
            VerboseCommand.getHelp()
        };
        return new HelpTree.Node(i18n, "logm.help", cmdNodes);
    }

    private HelpTree.Node getCommandHelp(String name) {
        return new HelpTree.Node(i18n, "logm.help." + name);
    }

    public boolean parseCommand(String cmd, ListIterator argIter, CommandContext ctx)
        throws Command.Fault
    {
        if (isPrefixMatch(cmd, VerboseCommand.getName())) {
            ctx.addCommand(new VerboseCommand(cmd));
            return true;
        }

        if (isMatch(cmd, LogCommand.getName())) {
            ctx.addCommand(new LogCommand(argIter));
            return true;
        }

        return false;
    }

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(LogManager.class);

    //--------------------------------------------------------------------------


    static class LogCommand
        extends Command
    {
        static String getName() {
            return "log";
        }

        LogCommand(ListIterator argIter) throws Fault {
            super(getName());

            if (!argIter.hasNext())
                throw new Fault(i18n, "logm.log.missingArg");

            file = new File(nextArg(argIter));
        }

        public void run(CommandContext ctx) throws Fault {
            PrintWriter newLog;

            try {
                FileWriter w = new FileWriter(file);
                newLog = new PrintWriter(new BufferedWriter(w));
            }
            catch (IOException e) {
                throw new Fault(i18n, "logm.log.cantOpenFile", e);
            }

            PrintWriter oldLog = ctx.getLogWriter();
            oldLog.close();
            ctx.setLogWriter(newLog);
        }

        private File file;
    }
}
