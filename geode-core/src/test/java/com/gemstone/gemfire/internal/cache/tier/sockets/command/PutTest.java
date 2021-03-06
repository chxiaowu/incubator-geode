/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gemstone.gemfire.internal.cache.tier.sockets.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.gemstone.gemfire.CancelCriterion;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.operations.PutOperationContext;
import com.gemstone.gemfire.internal.cache.EntryEventImpl;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.tier.CachedRegionHelper;
import com.gemstone.gemfire.internal.cache.tier.sockets.CacheServerStats;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientProxyMembershipID;
import com.gemstone.gemfire.internal.cache.tier.sockets.Message;
import com.gemstone.gemfire.internal.cache.tier.sockets.Part;
import com.gemstone.gemfire.internal.cache.tier.sockets.ServerConnection;
import com.gemstone.gemfire.internal.security.AuthorizeRequest;
import com.gemstone.gemfire.internal.security.SecurityService;
import com.gemstone.gemfire.security.NotAuthorizedException;
import com.gemstone.gemfire.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class PutTest {

  private static final String REGION_NAME = "region1";
  private static final String KEY = "key1";
  private static final Object CALLBACK_ARG = "arg";
  private static final byte[] EVENT = new byte[8];
  private static final byte[] VALUE = new byte[8];
  private static final byte[] OK_BYTES = new byte[]{0};

  @Mock
  private SecurityService securityService;
  @Mock
  private Message message;
  @Mock
  private ServerConnection serverConnection;
  @Mock
  private AuthorizeRequest authzRequest;
  @Mock
  private Cache cache;
  @Mock
  private LocalRegion localRegion;
  @Mock
  private Part regionNamePart;
  @Mock
  private Part keyPart;
  @Mock
  private Part valuePart;
  @Mock
  private Part eventPart;
  @Mock
  private Part callbackArgsPart;
  @Mock
  private PutOperationContext putOperationContext;
  @Mock
  private Message errorResponseMessage;
  @Mock
  private Message replyMessage;

  @InjectMocks
  private Put put;

  @Before
  public void setUp() throws Exception {
    this.put = new Put();
    MockitoAnnotations.initMocks(this);

    when(this.authzRequest.putAuthorize(eq(REGION_NAME), eq(KEY), eq(VALUE), eq(true), eq(CALLBACK_ARG))).thenReturn(this.putOperationContext);

    when(this.putOperationContext.getCallbackArg()).thenReturn(CALLBACK_ARG);
    when(this.putOperationContext.getSerializedValue()).thenReturn(VALUE);
    when(this.putOperationContext.isObject()).thenReturn(true);

    when(this.cache.getRegion(isA(String.class))).thenReturn(this.localRegion);
    when(this.cache.getCancelCriterion()).thenReturn(mock(CancelCriterion.class));

    when(this.callbackArgsPart.getObject()).thenReturn(CALLBACK_ARG);

    when(this.eventPart.getSerializedForm()).thenReturn(this.EVENT);

    when(this.valuePart.getSerializedForm()).thenReturn(VALUE);
    when(this.valuePart.isObject()).thenReturn(true);
    when(this.keyPart.getStringOrObject()).thenReturn(KEY);

    when(this.message.getNumberOfParts()).thenReturn(5);
    when(this.message.getPart(eq(0))).thenReturn(this.regionNamePart);
    when(this.message.getPart(eq(1))).thenReturn(this.keyPart);
    when(this.message.getPart(eq(2))).thenReturn(this.valuePart);
    when(this.message.getPart(eq(3))).thenReturn(this.eventPart);
    when(this.message.getPart(eq(4))).thenReturn(this.callbackArgsPart);

    when(this.regionNamePart.getString()).thenReturn(REGION_NAME);

    when(this.serverConnection.getCache()).thenReturn(this.cache);
    when(this.serverConnection.getCacheServerStats()).thenReturn(mock(CacheServerStats.class));
    when(this.serverConnection.getAuthzRequest()).thenReturn(this.authzRequest);
    when(this.serverConnection.getCachedRegionHelper()).thenReturn(mock(CachedRegionHelper.class));
    when(this.serverConnection.getReplyMessage()).thenReturn(this.replyMessage);
    when(this.serverConnection.getErrorResponseMessage()).thenReturn(this.errorResponseMessage);

    when(this.localRegion.basicBridgePut(eq(KEY), eq(VALUE), eq(null), eq(true), eq(CALLBACK_ARG), any(ClientProxyMembershipID.class), eq(true), any(EntryEventImpl.class))).thenReturn(true);
  }

  @Test
  public void noSecurityShouldSucceed() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(false);

    this.put.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.replyMessage).send(this.serverConnection);
  }

  @Test
  public void integratedSecurityShouldSucceedIfAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(true);

    this.put.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.securityService).authorizeRegionWrite(eq(REGION_NAME), eq(KEY));
    verify(this.replyMessage).send(this.serverConnection);
  }

  @Test
  public void integratedSecurityShouldThrowIfNotAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(true);
    doThrow(new NotAuthorizedException("")).when(this.securityService).authorizeRegionWrite(eq(REGION_NAME), eq(KEY));

    this.put.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.securityService).authorizeRegionWrite(eq(REGION_NAME), eq(KEY));
    verify(this.errorResponseMessage).send(this.serverConnection);
  }

  @Test
  public void oldSecurityShouldSucceedIfAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(false);

    this.put.cmdExecute(this.message, this.serverConnection, 0);

    ArgumentCaptor<byte[]> argument = ArgumentCaptor.forClass(byte[].class);
    verify(this.replyMessage).addBytesPart(argument.capture());

    assertThat(argument.getValue()).isEqualTo(OK_BYTES);

    verify(this.authzRequest).putAuthorize(eq(REGION_NAME), eq(KEY), eq(VALUE), eq(true), eq(CALLBACK_ARG));
    verify(this.replyMessage).send(this.serverConnection);
  }

  @Test
  public void oldSecurityShouldFailIfNotAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(false);
    doThrow(new NotAuthorizedException("")).when(this.authzRequest).putAuthorize(eq(REGION_NAME), eq(KEY), eq(VALUE), eq(true), eq(CALLBACK_ARG));

    this.put.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.authzRequest).putAuthorize(eq(REGION_NAME), eq(KEY), eq(VALUE), eq(true), eq(CALLBACK_ARG));

    ArgumentCaptor<NotAuthorizedException> argument = ArgumentCaptor.forClass(NotAuthorizedException.class);
    verify(this.errorResponseMessage).addObjPart(argument.capture());

    assertThat(argument.getValue()).isExactlyInstanceOf(NotAuthorizedException.class);
    verify(this.errorResponseMessage).send(this.serverConnection);
  }

}
