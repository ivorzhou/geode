/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.test.golden;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.junit.Test;

import org.apache.geode.test.process.ProcessWrapper;

/**
 * Basic unit testing of the golden testing framework. This tests an example which is expected to
 * always pass.
 *
 */
public class PassJUnitTest extends GoldenTestCase implements ExecutableProcess {

  @Override
  protected GoldenComparator createGoldenComparator() {
    return new GoldenStringComparator(expectedProblemLines());
  }

  String name() {
    return getClass().getSimpleName();
  }

  /**
   * Process output has no problems and should pass
   */
  @Test
  public void testPass() throws Exception {
    final String goldenString = "Begin " + name() + ".main" + "\n" + "Press Enter to continue."
        + "\n" + "End " + name() + ".main" + "\n";

    final ProcessWrapper process = createProcessWrapper(new ProcessWrapper.Builder(), getClass());
    process.execute(createProperties());
    assertTrue(process.isAlive());

    process.waitForOutputToMatch("Begin " + name() + "\\.main");
    process.waitForOutputToMatch("Press Enter to continue\\.");
    process.sendInput();
    process.waitForOutputToMatch("End " + name() + "\\.main");
    process.waitFor();

    assertOutputMatchesGoldenFile(process, goldenString);
    assertFalse(process.isAlive());
    assertFalse(process.getStandardOutReader().isAlive());
    assertFalse(process.getStandardErrorReader().isAlive());
  }

  @Override
  public void executeInProcess() throws IOException {
    outputLine("Begin " + name() + ".main");
    outputLine("Press Enter to continue.");
    new BufferedReader(new InputStreamReader(System.in)).readLine();
    outputLine("End " + name() + ".main");
  }

  public static void main(final String[] args) throws Exception {
    new PassJUnitTest().executeInProcess();
  }
}
