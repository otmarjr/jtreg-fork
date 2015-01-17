/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.javatest.Status;
import java.lang.instrument.Instrumentation;
import com.sun.tools.attach.VirtualMachine;

import static com.sun.javatest.regtest.RStatus.*;
import java.lang.management.ManagementFactory;

/**
 * This class implements the "main" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class MainAction extends Action
{
    public static final String NAME = "main";
    private static Object VirtualMachine;

    /**
     * {@inheritdoc}
     * @return "main"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /** Marker interface for test driver classes, which need to be passed a
     *  class loader to load the classes for the test.
     *  @see JUnitAction.JUnitRunner
     */
    interface TestRunner { }

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify arguments are not of length 0 and separate them into the options
     * to java, the classname, and the parameters to the named class.
     *
     * Verify that the options are valid for the "main" action.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    @Override
    public void init(String[][] opts, String[] args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        init(opts, args, reason, script, null);
    }

    /**
     * Local version of public init function.
     * Supports extra driverClass option, to interpose before main class.
     * @param driverClass actual class to invoke, with main class as first argument
     */
    void init(String[][] opts, String[] args, String reason,
                     RegressionScript script,
                     String driverClass)
        throws ParseException
    {
        super.init(opts, args, reason, script);

        if (args.length == 0)
            throw new ParseException(MAIN_NO_CLASSNAME);

        for (String[] opt : opts) {
            String optName = opt[0];
            String optValue = opt[1];

            if (optName.equals("fail")) {
                reverseStatus = parseFail(optValue);
            } else if (optName.equals("manual")) {
                manual = parseMainManual(optValue);
            } else if (optName.equals("timeout")) {
                timeout  = parseTimeout(optValue);
            } else if (optName.equals("othervm")) {
                othervm = true;
            } else if (optName.equals("native")) {
                nativeCode = true;
            } else if (optName.equals("bootclasspath")) {
                useBootClassPath = true;
                othervm = true;
            } else if (optName.equals("policy")) {
                overrideSysPolicy = true;
                policyFN = parsePolicy(optValue);
            } else if (optName.equals("java.security.policy")) {
                String name = optValue;
                if (optValue.startsWith("=")) {
                    overrideSysPolicy = true;
                    name = optValue.substring(1, optValue.length());
                }
                policyFN = parsePolicy(name);
            } else if (optName.equals("secure")) {
                secureCN = parseSecure(optValue);
            } else {
                throw new ParseException(MAIN_BAD_OPT + optName);
            }
        }

        if (manual.equals("unset")) {
            if (timeout < 0)
                timeout = script.getActionTimeout(0);
        } else {
            if (timeout >= 0)
                // can't have both timeout and manual
                throw new ParseException(PARSE_TIMEOUT_MANUAL);
            timeout = 0;
        }

        if (driverClass != null) {
            this.driverClass = driverClass;
        }

        if (script.useBootClassPath())
            useBootClassPath = othervm = true;

        // separate the arguments into the options to java, the
        // classname and the parameters to the named class
        for (int i = 0; i < args.length; i++) {
            if (testClassName == null) {
                if (args[i].startsWith("-")) {
                    testJavaArgs.add(args[i]);
                    if ((args[i].equals("-cp") || args[i].equals("-classpath"))
                        && (i+1 < args.length))
                        testJavaArgs.add(args[++i]);
                } else {
                    testClassName = args[i];
                }
            } else {
                testClassArgs.add(args[i]);
            }
        }

        if (testClassName == null)
            throw new ParseException(MAIN_NO_CLASSNAME);
        if (!othervm) {
            if (testJavaArgs.size() > 0)
                throw new ParseException(testJavaArgs + MAIN_UNEXPECT_VMOPT);
            if (policyFN != null)
                throw new ParseException(PARSE_POLICY_OTHERVM);
            if (secureCN != null)
                throw new ParseException(PARSE_SECURE_OTHERVM);
        }
    } // init()

    public List<String> getJavaArgs() {
        return testJavaArgs;
    }
    public List<String> getClassArgs() {
        return testClassArgs;
    }
    public String getClassName() {
        return testClassName;
    }

    List<String> filterJavaOpts(List<String> args) {
        return args;
    }

    @Override
    public Set<File> getSourceFiles() {
        Set<File> files = new LinkedHashSet<File>();
        if (testClassName != null) {
            String[][] buildOpts = {};
            String[]   buildArgs = {testClassName.replace(File.separatorChar, '.')};
            try {
                BuildAction ba = new BuildAction();
                ba.init(buildOpts, buildArgs, SREASON_ASSUMED_BUILD, script);
                files.addAll(ba.getSourceFiles());
            } catch (ParseException ignore) {
            }
        }
        if (policyFN != null)
            files.add(new File(policyFN));
        return files;
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Invoke the main method of the specified class, passing any arguments
     * after the class name.  A "main" action is considered to be finished when
     * the main method returns.
     *
     * A "main" action passes if the main method returns normally and does not
     * cause an exception to be thrown by the main or any subsidiary threads.
     * It fails otherwise.
     *
     * If the <em>othervm</em> option is present, this action requires that the
     * JVM support multiple processes.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        Status status;

        if (!(status = build()).isPassed())
            return status;

        if (nativeCode && script.getNativeDir() == null)
            return error(MAIN_NO_NATIVES);

        startAction();

        if (script.isCheck()) {
            status = passed(CHECK_PASS);
        } else {
            Lock lock = script.getLockIfRequired();
            if (lock != null) lock.lock();
            try {
                switch (othervm ? ExecMode.OTHERVM : script.getExecMode()) {
                    case AGENTVM:
                        status = runAgentJVM();
                        break;
                    case OTHERVM:
                        status = runOtherJVM();
                        break;
                    case SAMEVM:
                        status = runSameJVM();
                        break;
                    default:
                        throw new AssertionError();
                }
            } finally {
                if (lock != null) lock.unlock();
            }
        }

        endAction(status);
        return status;
    } // run()

    //----------internal methods------------------------------------------------

    protected Status build() throws TestRunException {
        // TAG-SPEC:  "The named <class> will be compiled on demand, just as
        // though an "@run build <class>" action had been inserted before
        // this action."
        String[][] buildOpts = {};
        String[]   buildArgs = {testClassName.replace(File.separatorChar, '.')};
        BuildAction ba = new BuildAction();
        return ba.build(buildOpts, buildArgs, SREASON_ASSUMED_BUILD, script);
    }

    private Status runOtherJVM() throws TestRunException {
        // Arguments to wrapper:
        String runClassName;
        List<String> runClassArgs;
        if (driverClass == null) {
            runClassName = testClassName;
            runClassArgs = testClassArgs;
        } else {
            runClassName = driverClass;
            runClassArgs = new ArrayList<String>();
            runClassArgs.add(script.getTestResult().getTestName());
            runClassArgs.add(testClassName);
            runClassArgs.addAll(testClassArgs);
        }

        // WRITE ARGUMENT FILE
        File mainArgFile =
            new File(script.absTestClsDir(), testClassName + RegressionScript.WRAPPEREXTN);
        mainArgFile.getParentFile().mkdirs();
        FileWriter fw;
        try {
            fw = new FileWriter(mainArgFile);
            fw.write(runClassName + "\0");
            fw.write(StringUtils.join(runClassArgs) + "\0" );
            fw.close();
        } catch (IOException e) {
            return error(MAIN_CANT_WRITE_ARGS);
        } catch (SecurityException e) {
            // shouldn't happen since JavaTestSecurityManager allows file ops
            return error(MAIN_SECMGR_FILEOPS);
        }

        // CONSTRUCT THE COMMAND LINE

        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        Map<String, String> envArgs = new LinkedHashMap<String, String>();
        envArgs.putAll(script.getEnvVars());

        // some tests are inappropriately relying on the CLASSPATH environment
        // variable being set, so force the use here.
        final boolean useCLASSPATH = true;

        SearchPath cp = new SearchPath();
        SearchPath bcp = new SearchPath();
        (useBootClassPath ? bcp : cp).append(script.getJavaTestClassPath());

        cp.append(script.getTestClassPath(useBootClassPath));
        bcp.append(script.getTestBootClassPath(useBootClassPath));

        SearchPath p = bcp.isEmpty() ? cp : bcp;
        if (script.isJUnitRequired())
            p.append(script.getJUnitJar());
        if (script.isTestNGRequired())
            p.append(script.getTestNGJar());

        if (useCLASSPATH && !cp.isEmpty()) {
            envArgs.put("CLASSPATH", cp.toString());
        }

        String javaCmd = script.getJavaProg();
        List<String> javaOpts = new ArrayList<String>();

        if ((!useCLASSPATH) && !cp.isEmpty()) {
            javaOpts.add("-classpath");
            javaOpts.add(cp.toString());
        }

        if (!bcp.isEmpty()) {
            javaOpts.add("-Xbootclasspath/a:" + bcp.toString());
        }

        javaOpts.addAll(script.getTestVMJavaOptions());

        Map<String, String> javaProps = new LinkedHashMap<String, String>();
        javaProps.putAll(script.getTestProperties());

        String newPolicyFN;
        if (policyFN != null) {
            // add permission to read JTwork/classes by adding a grant entry
            newPolicyFN = addGrantEntry(policyFN);
            javaProps.put("java.security.policy",
                          overrideSysPolicy ? "=" + newPolicyFN : newPolicyFN);
        }

        if (secureCN != null) {
            javaProps.put("java.security.manager", secureCN);
        }
        else if (policyFN != null) {
            javaProps.put("java.security.manager", "default");
        }
//      javaProps.put("java.security.debug", "all");

        javaOpts.addAll(testJavaArgs);

        String className = "com.sun.javatest.regtest.MainWrapper";
        List<String> classArgs = new ArrayList<String>();
        classArgs.add(mainArgFile.getPath());

        classArgs.addAll(runClassArgs);

        List<String> command = new ArrayList<String>();
        command.add(javaCmd);
        for (Map.Entry<String,String> e: javaProps.entrySet())
            command.add("-D" + e.getKey() + "=" + e.getValue());
        command.addAll(filterJavaOpts(javaOpts));
        command.add(className);
        command.addAll(classArgs);
        String[] cmdArgs = command.toArray(new String[command.size()]);

        // PASS TO PROCESSCOMMAND
        Status status;
        PrintWriter sysOut = section.createOutput("System.out");
        PrintWriter sysErr = section.createOutput("System.err");
        try {
            if (showMode)
                showMode(getName(), ExecMode.OTHERVM, section);
            if (showCmd)
                showCmd(getName(), cmdArgs, section);
            recorder.java(envArgs, javaCmd, javaProps, javaOpts, className, classArgs);

            // RUN THE MAIN WRAPPER CLASS
            ProcessCommand cmd = new ProcessCommand();
            cmd.setExecDir(script.absTestScratchDir());

            // Set the exit codes and their associated strings.  Note that we
            // require the use of a non-zero exit code for a passed test so
            // that we have a chance of detecting whether the test itself has
            // illegally called System.exit(0).
            cmd.setStatusForExit(Status.exitCodes[Status.PASSED], passed(EXEC_PASS));
            cmd.setStatusForExit(Status.exitCodes[Status.FAILED], failed(EXEC_FAIL));
            cmd.setDefaultStatus(failed(UNEXPECT_SYS_EXIT));

            TimeoutHandler timeoutHandler =
                TimeoutHandlerProvider.createHandler(script, section);

            status = normalize(cmd.exec(cmdArgs, envArgs, sysOut, sysErr,
                                        (long)timeout * 1000, timeoutHandler));

        } finally {
            sysOut.close();
            sysErr.close();
        }

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        return status;
    } // runOtherJVM()

    protected Status runSameJVM() throws TestRunException {
        SearchPath runClasspath;
        String runMainClass;
        List<String> runMainArgs;
        if (driverClass == null) {
            runClasspath = script.getTestClassPath();
            runMainClass = testClassName;
            runMainArgs = testClassArgs;
        } else {
            runClasspath = script.getTestClassPath();
            runMainClass = driverClass;
            runMainArgs = new ArrayList<String>();
            runMainArgs.add(script.getTestResult().getTestName());
            runMainArgs.add(testClassName);
            runMainArgs.addAll(testClassArgs);
        }

        if (showMode)
            showMode(getName(), ExecMode.SAMEVM, section);

        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        Map<String, String> javaProps = script.getTestProperties();

        String javaProg = script.getJavaProg();
        SearchPath rcp = new SearchPath(script.getJavaTestClassPath(), script.getTestJDK().getJDKClassPath());
        if (script.isJUnitRequired())
            rcp.append(script.getJUnitJar());
        if (script.isTestNGRequired())
            rcp.append(script.getTestNGJar());
        rcp.append(runClasspath);
        List<String> javaArgs = new java.util.ArrayList(Arrays.asList("-classpath", rcp.toString()));
        recorder.java(script.getEnvVars(), javaProg, javaProps, javaArgs, runMainClass, runMainArgs);

        // delegate actual work to shared method
        Status status = runClass(
                script.getTestResult().getTestName(),
                javaProps,
                runClasspath,
                runMainClass,
                runMainArgs.toArray(new String[runMainArgs.size()]),
                timeout,
                getOutputHandler(section));

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        return status;
    } // runSameJVM

    private Status runAgentJVM() throws TestRunException {
        SearchPath runClasspath;
        String runMainClass;
        List<String> runMainArgs;
        if (driverClass == null) {
            runClasspath = script.getTestClassPath();
            runMainClass = testClassName;
            runMainArgs = testClassArgs;
        } else {
            runClasspath = script.getTestClassPath();
            runMainClass = driverClass;
            runMainArgs = new ArrayList<String>();
            runMainArgs.add(script.getTestResult().getTestName());
            runMainArgs.add(testClassName);
            runMainArgs.addAll(testClassArgs);
        }

        if (showMode)
            showMode(getName(), ExecMode.AGENTVM, section);

        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        Map<String, String> javaProps = script.getTestProperties();

        JDK jdk = script.getTestJDK();
        SearchPath classpath = new SearchPath(script.getJavaTestClassPath(), jdk.getJDKClassPath());
        if (script.isJUnitRequired())
            classpath.append(script.getJUnitJar());
        if (script.isTestNGRequired())
            classpath.append(script.getTestNGJar());

        String javaProg = script.getJavaProg();
        SearchPath rcp = new SearchPath(classpath, runClasspath);
        List<String> javaArgs = Arrays.asList("-classpath", rcp.toString());
        recorder.java(script.getEnvVars(), javaProg, javaProps, javaArgs, runMainClass, runMainArgs);

        Agent agent;
        try {
            agent = script.getAgent(jdk, classpath,
                    filterJavaOpts(script.getTestVMJavaOptions()));
        } catch (IOException e) {
            return error(AGENTVM_CANT_GET_VM + ": " + e);
        }

        TimeoutHandler timeoutHandler =
                TimeoutHandlerProvider.createHandler(script, section);

        Status status;
        try {
            status = agent.doMainAction(
                    script.getTestResult().getTestName(),
                    javaProps,
                    runClasspath,
                    runMainClass,
                    runMainArgs,
                    timeout,
                    timeoutHandler,
                    section);
        } catch (Agent.Fault e) {
            if (e.getCause() instanceof IOException)
                status = error(String.format(AGENTVM_IO_EXCEPTION, e.getCause()));
            else
                status = error(String.format(AGENTVM_EXCEPTION, e.getCause()));
        }
        if (status.isError()) {
            script.closeAgent(agent);
        }

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        return status;
    } // runAgentJVM()

    static void loadAgentCurrentVM() {
        String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
        int p = nameOfRunningVM.indexOf('@');
        String pid = nameOfRunningVM.substring(0, p);

        try {
            com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach(pid);
            vm.loadAgent("C:\\aspectj1.8\\lib\\aspectjweaver.jar", "");
            vm.detach();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    static Status runClass(
            String testName,
            Map<String, String> props,
            SearchPath classpath,
            String classname,
            String[] classArgs,
            int timeout,
            OutputHandler outputHandler) {
        SaveState saved = new SaveState();

        Properties p = System.getProperties();
        for (Map.Entry<String, String> e: props.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (name.equals("test.class.path.prefix")) {
                SearchPath cp = new SearchPath(value, System.getProperty("java.class.path"));
                p.put("java.class.path", cp.toString());
            } else {
                p.put(e.getKey(), e.getValue());
            }
        }
        System.setProperties(p);

        PrintByteArrayOutputStream out = new PrintByteArrayOutputStream();
        PrintByteArrayOutputStream err = new PrintByteArrayOutputStream();

        Status status = passed(EXEC_PASS);
        try {
            Class<?> c;
            ClassLoader loader;
            if (classpath != null) {
                List<URL> urls = new ArrayList<URL>();
                for (File f: new SearchPath(classpath).split()) {
                    try {
                        urls.add(f.toURI().toURL());
                    } catch (MalformedURLException e) {
                    }
                }
                loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
                c = loader.loadClass(classname);
            } else {
                loader = null;
                c = Class.forName(classname);
            }

            
            // Select signature for main method depending on whether the class
            // implements the TestRunner marker interface.
            Class<?>[] argTypes;
            Object[] methodArgs;
            if (TestRunner.class.isAssignableFrom(c)) {
                // Marker interface found: use main(ClassLoader, String...)
                argTypes = new Class<?>[] { ClassLoader.class, String[].class };
                methodArgs = new Object[] { loader, classArgs };
            } else {
                // Normal case: marker interface not found; use standard main method
                argTypes = new Class<?>[] { String[].class };
                methodArgs = new Object[] { classArgs };
            }

            Method method = c.getMethod("main", argTypes);

            Status stat = redirectOutput(out, err);
            if (!stat.isPassed()) {
                return stat;
            }

            // RUN JAVA IN ANOTHER THREADGROUP
            //loadAgentCurrentVM();

            SameVMThreadGroup tg = new SameVMThreadGroup();
            SameVMRunnable svmt = new SameVMRunnable(method, methodArgs, err);
            Thread t = new Thread(tg, svmt, "SameVMThread");
            Alarm alarm = null;
            if (timeout > 0) {
                PrintWriter alarmOut = outputHandler.createOutput(OutputHandler.OutputKind.LOG);
                alarm = new Alarm(timeout * 1000, t, testName, alarmOut);
            }
            Throwable error = null;
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                if (t.isInterrupted() && (tg.uncaughtThrowable == null)) {
                    error = e;
                    status = error(MAIN_THREAD_INTR + e.getMessage());
                }
            } finally {
                tg.cleanup();
                if (alarm != null) {
                    alarm.cancel();
                    if (alarm.getState() != Alarm.State.WAITING && (error == null)) {
                        err.println("Test timed out. No timeout information is available in samevm mode.");
                        error = new Error("timeout");
                        status = error(MAIN_THREAD_TIMEOUT);
                    }
                }
            }

            if (((svmt.t != null) || (tg.uncaughtThrowable != null)) && (error == null)) {
                if (svmt.t == null)
                    error = tg.uncaughtThrowable;
                else
                    error = svmt.t;
                status = failed(MAIN_THREW_EXCEPT + error.toString());
            }

            if (status.getReason().contains("java.lang.SecurityException: System.exit() forbidden")) {
                status = failed(UNEXPECT_SYS_EXIT);
            } else if (!tg.cleanupOK) {
                status = error(EXEC_ERROR_CLEANUP);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace(err);
            err.println();
            err.println("JavaTest Message: main() method must be in a public class named");
            err.println("JavaTest Message: " + classname + " in file " + classname + ".java");
            err.println();
            status = error(MAIN_CANT_LOAD_TEST + e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(err);
            err.println();
            err.println("JavaTest Message: main() method must be in a public class named");
            err.println("JavaTest Message: " + classname + " in file " + classname + ".java");
            err.println();
            status = error(MAIN_CANT_FIND_MAIN);
        } finally {
            status = saved.restore(testName, status);
        }

        // Write test output
        out.close();
        outputHandler.createOutput(OutputHandler.OutputKind.STDOUT, out.getOutput());

        err.close();
        outputHandler.createOutput(OutputHandler.OutputKind.STDERR, err.getOutput());

        return status;
    }

    //----------utility methods-------------------------------------------------

    private String parseMainManual(String value) throws ParseException {
        if (value != null)
            throw new ParseException(MAIN_MANUAL_NO_VAL + value);
        else
            value = "novalue";
        return value;
    } // parseMainManual()

    private Status checkReverse(Status status, boolean reverseStatus) {
        // The standard rule is that /fail will invert Passed and Failed results
        // but will leave Error results alone.  But, for historical reasons
        // perpetuated by the Basic test program, a test calling System.exit
        // is reported with a Failed result, whereas Error would really be
        // more appropriate.  Therefore, we take care not to invert the
        // status if System.exit was called to exit the test.
        if (!status.isError()
                && !status.getReason().startsWith(UNEXPECT_SYS_EXIT)) {
            boolean ok = status.isPassed();
            int st = status.getType();
            String sr;
            if (ok && reverseStatus) {
                sr = EXEC_PASS_UNEXPECT;
                st = Status.FAILED;
            } else if (ok && !reverseStatus) {
                sr = EXEC_PASS;
            } else if (!ok && reverseStatus) {
                sr = EXEC_FAIL_EXPECT;
                st = Status.PASSED;
            } else { /* !ok && !reverseStatus */
                sr = EXEC_FAIL;
            }
            if ((st == Status.FAILED) && ! (status.getReason() == null) &&
                    !status.getReason().equals(EXEC_PASS))
                sr += ": " + status.getReason();
            status = createStatus(st, sr);
        }

        return status;
    }

    //----------internal classes------------------------------------------------

    private static class SameVMRunnable implements Runnable
    {
        public SameVMRunnable(Method m, Object[] args, PrintStream err) {
            method    = m;
            this.args = args;
            this.err  = err;
        } // SameVMRunnable()

        public void run() {
            try {
                // RUN JAVA PROGRAM
                result = method.invoke(null, args);

                System.err.println();
                System.err.println("JavaTest Message:  Test complete.");
                System.err.println();
            } catch (InvocationTargetException e) {
                // main must have thrown an exception, so the test failed
                e.getTargetException().printStackTrace(err);
                t = e.getTargetException();
                System.err.println();
                System.err.println("JavaTest Message: Test threw exception: " + t.getClass().getName());
                System.err.println("JavaTest Message: shutting down test");
                System.err.println();
            } catch (IllegalAccessException e) {
                e.printStackTrace(err);
                t = e;
                System.err.println();
                System.err.println("JavaTest Message: Verify that the class defining the test is");
                System.err.println("JavaTest Message: declared public (test invoked via reflection)");
                System.err.println();
            }
        } // run()

        //----------member variables--------------------------------------------

        public  Object result;
        private final Method method;
        private final Object[] args;
        private final PrintStream err;

        Throwable t = null;
    }

    static class SameVMThreadGroup extends ThreadGroup
    {
        SameVMThreadGroup() {
            super("SameVMThreadGroup");
        } // SameVMThreadGroup()

        @Override
        public synchronized void uncaughtException(Thread t, Throwable e) {
            if (e instanceof ThreadDeath)
                return;
            if ((uncaughtThrowable == null) && (!cleanMode)) {
                uncaughtThrowable = e;
                uncaughtThread    = t;
            }
            cleanup();
        } // uncaughtException()

        private void cleanup() {
            cleanMode = true;

            final int CLEANUP_ROUNDS = 4;
            final long MAX_CLEANUP_TIME_MILLIS = 2 * 60 * 1000;
            final long CLEANUP_MILLIS_PER_ROUND = MAX_CLEANUP_TIME_MILLIS / CLEANUP_ROUNDS;
            final long NANOS_PER_MILLI = 1000L * 1000L;

            long startCleanupTime = System.nanoTime();

            for (int i = 1; i <= CLEANUP_ROUNDS; i++) {
                long deadline = startCleanupTime + i * CLEANUP_MILLIS_PER_ROUND * NANOS_PER_MILLI;
                List<Thread> liveThreads = liveThreads();
                if (liveThreads.isEmpty()) {
                    // nothing left to cleanup
                    cleanupOK = true;
                    return;
                }

                // kick the remaining live threads
                for (Thread thread : liveThreads)
                    thread.interrupt();

                // try joining as many threads as possible before
                // the round times out
                for (Thread thread : liveThreads) {
                    long millis = (deadline - System.nanoTime()) / NANOS_PER_MILLI;
                    if (millis <= 0)
                        break;
                    try {
                        thread.join(millis);
                    } catch (InterruptedException ignore) {
                    }
                }
            }

            cleanupOK = liveThreads().isEmpty();
        } // cleanup()

        /**
         * Gets all the "interesting" threads in the thread group.
         * @see ThreadGroup#enumerate(Thread[])
         */
        private List<Thread> liveThreads() {
            for (int estSize = activeCount() + 1; ; estSize = estSize * 2) {
                Thread[] threads = new Thread[estSize];
                int num = enumerate(threads);
                if (num < threads.length) {
                    ArrayList<Thread> list = new ArrayList<Thread>(num);
                    for (int i = 0; i < num; i++) {
                        Thread t = threads[i];
                        if (t.isAlive() &&
                                t != Thread.currentThread() &&
                                ! t.isDaemon())
                            list.add(t);
                    }
                    return list;
                }
            }
        }

        //----------member variables--------------------------------------------

        private boolean cleanMode   = false;
        Throwable uncaughtThrowable = null;
        Thread    uncaughtThread    = null;
        boolean cleanupOK = false;
    }

    //----------member variables------------------------------------------------

    private final List<String>  testJavaArgs = new ArrayList<String>();
    private final List<String>  testClassArgs = new ArrayList<String>();
    private String  driverClass = null;
    private String  testClassName  = null;
    private String  policyFN = null;
    private String  secureCN = null;
    private boolean overrideSysPolicy = false;

    protected boolean reverseStatus = false;
    protected boolean useBootClassPath = false;
    protected boolean othervm = false;
    protected boolean nativeCode = false;
    private int     timeout = -1;
    private String  manual  = "unset";
}
