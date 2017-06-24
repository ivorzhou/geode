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
package org.apache.geode.cache.lucene;

import static org.apache.geode.cache.lucene.test.LuceneTestUtilities.INDEX_NAME;
import static org.apache.geode.cache.lucene.test.LuceneTestUtilities.REGION_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.lucene.internal.cli.LuceneCliStrings;
import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.management.internal.cli.result.ErrorResultData;
import org.apache.geode.management.internal.cli.result.ResultBuilder;
import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.security.SimpleTestSecurityManager;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule;
import org.apache.geode.test.dunit.rules.ServerStarterRule;
import org.apache.geode.test.junit.categories.IntegrationTest;
import org.apache.geode.test.junit.categories.SecurityTest;

@Category({IntegrationTest.class, SecurityTest.class})
@RunWith(JUnitParamsRunner.class)
public class LuceneCommandsSecurityTest {

  @ClassRule
  public static ServerStarterRule server = new ServerStarterRule()
      .withSecurityManager(SimpleTestSecurityManager.class).withJMXManager().withAutoStart();

  @ClassRule
  public static GfshShellConnectionRule gfshShell = new GfshShellConnectionRule();

  @After
  public void after() throws Exception {
    gfshShell.disconnect();
  }


  protected UserNameAndExpectedResponse[] getCreateIndexUserNameAndExpectedResponses() {
    return new UserNameAndExpectedResponse[] {
        new UserNameAndExpectedResponse("noPermissions", true),
        new UserNameAndExpectedResponse("clusterManageQuery,clusterWriteDisk", false)};
  }

  @Test
  @Parameters(method = "getCreateIndexUserNameAndExpectedResponses")
  public void verifyCreateIndexPermissions(UserNameAndExpectedResponse user) throws Exception {
    // Connect gfsh
    this.gfshShell.secureConnectAndVerify(this.server.getJmxPort(),
        GfshShellConnectionRule.PortType.jmxManger, user.getUserName(), user.getUserName());

    // Attempt to create lucene index
    CommandResult result = this.gfshShell.executeCommand(getCreateIndexCommand());

    // Verify result
    verifyResult(user, result);
  }

  protected UserNameAndExpectedResponse[] getSearchIndexUserNameAndExpectedResponses() {
    return new UserNameAndExpectedResponse[] {
        new UserNameAndExpectedResponse("noPermissions", true),
        new UserNameAndExpectedResponse("dataRead", false)};
  }

  @Test
  @Parameters(method = "getSearchIndexUserNameAndExpectedResponses")
  public void verifySearchIndexPermissions(UserNameAndExpectedResponse user) throws Exception {
    // Create index and region
    // createIndexAndRegion();

    // Connect gfsh
    this.gfshShell.secureConnectAndVerify(this.server.getJmxPort(),
        GfshShellConnectionRule.PortType.jmxManger, user.getUserName(), user.getUserName());

    // Attempt to search lucene index
    CommandResult result = this.gfshShell.executeCommand(getSearchIndexCommand());

    // Verify result
    verifyResult(user, result);
  }

  protected UserNameAndExpectedResponse[] getListIndexesUserNameAndExpectedResponses() {
    return new UserNameAndExpectedResponse[] {
        new UserNameAndExpectedResponse("noPermissions", true),
        new UserNameAndExpectedResponse("clusterReadQuery", false)};
  }

  @Test
  @Parameters(method = "getListIndexesUserNameAndExpectedResponses")
  public void verifyListIndexesPermissions(UserNameAndExpectedResponse user) throws Exception {
    // Create index and region
    // createIndexAndRegion();

    // Connect gfsh
    this.gfshShell.secureConnectAndVerify(this.server.getJmxPort(),
        GfshShellConnectionRule.PortType.jmxManger, user.getUserName(), user.getUserName());

    // Attempt to search lucene index
    CommandResult result = this.gfshShell.executeCommand(getListIndexesCommand());

    // Verify result
    verifyResult(user, result);
  }

  protected UserNameAndExpectedResponse[] getDescribeIndexUserNameAndExpectedResponses() {
    return new UserNameAndExpectedResponse[] {
        new UserNameAndExpectedResponse("noPermissions", true),
        new UserNameAndExpectedResponse("clusterReadQuery", false)};
  }

  @Test
  @Parameters(method = "getDescribeIndexUserNameAndExpectedResponses")
  public void verifyDescribeIndexPermissions(UserNameAndExpectedResponse user) throws Exception {
    // Create index and region
    // createIndexAndRegion();

    // Connect gfsh
    this.gfshShell.secureConnectAndVerify(this.server.getJmxPort(),
        GfshShellConnectionRule.PortType.jmxManger, user.getUserName(), user.getUserName());

    // Attempt to search lucene index
    CommandResult result = this.gfshShell.executeCommand(getDescribeIndexCommand());

    // Verify result
    verifyResult(user, result);
  }

  protected UserNameAndExpectedResponse[] getDestroyIndexUserNameAndExpectedResponses() {
    return new UserNameAndExpectedResponse[] {
        new UserNameAndExpectedResponse("noPermissions", true),
        new UserNameAndExpectedResponse("clusterManageQuery", false)};
  }

  @Test
  @Parameters(method = "getDestroyIndexUserNameAndExpectedResponses")
  public void verifyDestroyIndexPermissions(UserNameAndExpectedResponse user) throws Exception {
    // Create index and region
    // createIndexAndRegion();

    // Connect gfsh
    this.gfshShell.secureConnectAndVerify(this.server.getJmxPort(),
        GfshShellConnectionRule.PortType.jmxManger, user.getUserName(), user.getUserName());

    // Attempt to search lucene index
    CommandResult result = this.gfshShell.executeCommand(getDestroyIndexCommand());

    // Verify result
    verifyResult(user, result);
  }

  private void createIndexAndRegion() throws Exception {
    // Connect gfsh to locator with permissions necessary to create an index and region
    this.gfshShell.secureConnectAndVerify(this.server.getJmxPort(),
        GfshShellConnectionRule.PortType.jmxManger, "cluster,data", "cluster,data");

    // Create lucene index
    this.gfshShell.executeAndVerifyCommand(getCreateIndexCommand());

    // Create region
    this.gfshShell.executeAndVerifyCommand(getCreateRegionCommand());

    // Disconnect gfsh
    this.gfshShell.disconnect();
  }

  private void verifyResult(UserNameAndExpectedResponse user, CommandResult result) {
    if (user.getExpectAuthorizationError()) {
      assertTrue(result.getResultData() instanceof ErrorResultData);
      assertEquals(ResultBuilder.ERRORCODE_UNAUTHORIZED,
          ((ErrorResultData) result.getResultData()).getErrorCode());
    } else {
      assertEquals(Result.Status.OK, result.getStatus());
    }
  }

  private String getCreateIndexCommand() {
    CommandStringBuilder csb = new CommandStringBuilder(LuceneCliStrings.LUCENE_CREATE_INDEX);
    csb.addOption(LuceneCliStrings.LUCENE__INDEX_NAME, INDEX_NAME);
    csb.addOption(LuceneCliStrings.LUCENE__REGION_PATH, REGION_NAME);
    csb.addOption(LuceneCliStrings.LUCENE_CREATE_INDEX__FIELD, "field1");
    return csb.toString();
  }

  private String getCreateRegionCommand() {
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.CREATE_REGION);
    csb.addOption(CliStrings.CREATE_REGION__REGION, REGION_NAME);
    csb.addOption(CliStrings.CREATE_REGION__REGIONSHORTCUT,
        RegionShortcut.PARTITION_REDUNDANT.name());
    return csb.toString();
  }

  private String getSearchIndexCommand() {
    CommandStringBuilder csb = new CommandStringBuilder(LuceneCliStrings.LUCENE_SEARCH_INDEX);
    csb.addOption(LuceneCliStrings.LUCENE__INDEX_NAME, INDEX_NAME);
    csb.addOption(LuceneCliStrings.LUCENE__REGION_PATH, REGION_NAME);
    csb.addOption(LuceneCliStrings.LUCENE_SEARCH_INDEX__QUERY_STRING, "field1:value1");
    csb.addOption(LuceneCliStrings.LUCENE_SEARCH_INDEX__DEFAULT_FIELD, "field1");
    return csb.toString();
  }

  private String getListIndexesCommand() {
    CommandStringBuilder csb = new CommandStringBuilder(LuceneCliStrings.LUCENE_LIST_INDEX);
    return csb.toString();
  }

  private String getDescribeIndexCommand() {
    CommandStringBuilder csb = new CommandStringBuilder(LuceneCliStrings.LUCENE_DESCRIBE_INDEX);
    csb.addOption(LuceneCliStrings.LUCENE__INDEX_NAME, INDEX_NAME);
    csb.addOption(LuceneCliStrings.LUCENE__REGION_PATH, REGION_NAME);
    return csb.toString();
  }

  private String getDestroyIndexCommand() {
    CommandStringBuilder csb = new CommandStringBuilder(LuceneCliStrings.LUCENE_DESTROY_INDEX);
    csb.addOption(LuceneCliStrings.LUCENE__INDEX_NAME, INDEX_NAME);
    csb.addOption(LuceneCliStrings.LUCENE__REGION_PATH, REGION_NAME);
    return csb.toString();
  }

  public static class UserNameAndExpectedResponse implements Serializable {

    private final String userName;

    private final boolean expectAuthorizationError;

    public UserNameAndExpectedResponse(String userName, boolean expectAuthorizationError) {
      this.userName = userName;
      this.expectAuthorizationError = expectAuthorizationError;
    }

    public String getUserName() {
      return this.userName;
    }

    public boolean getExpectAuthorizationError() {
      return this.expectAuthorizationError;
    }
  }
}