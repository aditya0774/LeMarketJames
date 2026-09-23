package com.lemarketjames.common;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.engine.TestExecutionResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Prints each test's name, runtime and result to the console during {@code mvn test}, with color and borders. */
public class ConsoleTestExecutionListener implements TestExecutionListener {

    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BORDER = "=".repeat(60);
    private static final String TEST_BORDER = "-".repeat(60);

    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        System.out.println(CYAN + BORDER + RESET);
        System.out.println(CYAN + "  STARTING TEST SUITE" + RESET);
        System.out.println(CYAN + BORDER + RESET);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            startTimes.put(testIdentifier.getUniqueId(), System.currentTimeMillis());
            System.out.println(CYAN + TEST_BORDER + RESET);
            System.out.println(CYAN + "STARTED  " + testIdentifier.getDisplayName() + RESET);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (testIdentifier.isTest()) {
            Long startTime = startTimes.remove(testIdentifier.getUniqueId());
            long runtimeMs = startTime == null ? -1 : System.currentTimeMillis() - startTime;
            String color = switch (result.getStatus()) {
                case SUCCESSFUL -> GREEN;
                case FAILED -> RED;
                default -> YELLOW;
            };
            System.out.println(color + result.getStatus() + "  " + testIdentifier.getDisplayName()
                    + "  (" + runtimeMs + "ms)" + RESET);
            System.out.println(CYAN + TEST_BORDER + RESET);
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.out.println(CYAN + BORDER + RESET);
        System.out.println(CYAN + "  TEST SUITE FINISHED" + RESET);
        System.out.println(CYAN + BORDER + RESET);
    }
}

