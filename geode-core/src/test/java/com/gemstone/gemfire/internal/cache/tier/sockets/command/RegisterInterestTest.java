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
import com.gemstone.gemfire.cache.InterestResultPolicy;
import com.gemstone.gemfire.cache.operations.GetOperationContext;
import com.gemstone.gemfire.cache.operations.RegisterInterestOperationContext;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.tier.CachedRegionHelper;
import com.gemstone.gemfire.internal.cache.tier.sockets.AcceptorImpl;
import com.gemstone.gemfire.internal.cache.tier.sockets.CacheClientNotifier;
import com.gemstone.gemfire.internal.cache.tier.sockets.CacheClientProxy;
import com.gemstone.gemfire.internal.cache.tier.sockets.CacheServerStats;
import com.gemstone.gemfire.internal.cache.tier.sockets.ChunkedMessage;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientProxyMembershipID;
import com.gemstone.gemfire.internal.cache.tier.sockets.Message;
import com.gemstone.gemfire.internal.cache.tier.sockets.ObjectPartList;
import com.gemstone.gemfire.internal.cache.tier.sockets.Part;
import com.gemstone.gemfire.internal.cache.tier.sockets.ServerConnection;
import com.gemstone.gemfire.internal.security.AuthorizeRequest;
import com.gemstone.gemfire.internal.security.SecurityService;
import com.gemstone.gemfire.security.NotAuthorizedException;
import com.gemstone.gemfire.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class RegisterInterestTest {

  private static final String REGION_NAME = "region1";
  private static final String KEY = "key1";
  private static final byte[] DURABLE = new byte[8];


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
  private Part regionNamePart;
  @Mock
  private Part interestTypePart;
  @Mock
  private Part durablePart;
  @Mock
  private Part keyPart;
  @Mock
  private Part notifyPart;
  @Mock
  private ChunkedMessage chunkedResponseMessage;

  @InjectMocks
  private RegisterInterest registerInterest;

  @Before
  public void setUp() throws Exception {
    this.registerInterest = new RegisterInterest();
    MockitoAnnotations.initMocks(this);


    when(this.authzRequest.registerInterestAuthorize(eq(REGION_NAME), eq(KEY), anyInt(), any())).thenReturn(mock(RegisterInterestOperationContext.class));

    when(this.cache.getRegion(isA(String.class))).thenReturn(mock(LocalRegion.class));
    when(this.cache.getCancelCriterion()).thenReturn(mock(CancelCriterion.class));

    when(this.durablePart.getObject()).thenReturn(DURABLE);

    when(this.interestTypePart.getInt()).thenReturn(0);

    when(this.keyPart.getStringOrObject()).thenReturn(KEY);

    when(this.message.getNumberOfParts()).thenReturn(6);
    when(this.message.getPart(eq(0))).thenReturn(this.regionNamePart);
    when(this.message.getPart(eq(1))).thenReturn(this.interestTypePart);
    when(this.message.getPart(eq(2))).thenReturn(mock(Part.class));
    when(this.message.getPart(eq(3))).thenReturn(this.durablePart);
    when(this.message.getPart(eq(4))).thenReturn(this.keyPart);
    when(this.message.getPart(eq(5))).thenReturn(this.notifyPart);

    when(this.notifyPart.getObject()).thenReturn(DURABLE);

    when(this.regionNamePart.getString()).thenReturn(REGION_NAME);

    when(this.serverConnection.getCache()).thenReturn(this.cache);
    when(this.serverConnection.getAuthzRequest()).thenReturn(this.authzRequest);
    when(this.serverConnection.getCachedRegionHelper()).thenReturn(mock(CachedRegionHelper.class));
    when(this.serverConnection.getChunkedResponseMessage()).thenReturn(this.chunkedResponseMessage);
  }

  @Test
  public void noSecurityShouldSucceed() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(false);

    this.registerInterest.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.chunkedResponseMessage).sendChunk(this.serverConnection);
  }

  @Test
  public void integratedSecurityShouldSucceedIfAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(true);

    this.registerInterest.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.securityService).authorizeRegionRead(eq(REGION_NAME), eq(KEY));
    verify(this.chunkedResponseMessage).sendChunk(this.serverConnection);
  }

  @Test
  public void integratedSecurityShouldThrowIfNotAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(true);
    doThrow(new NotAuthorizedException("")).when(this.securityService).authorizeRegionRead(eq(REGION_NAME), eq(KEY));

    this.registerInterest.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.securityService).authorizeRegionRead(eq(REGION_NAME), eq(KEY));
    verify(this.chunkedResponseMessage).sendChunk(this.serverConnection);
  }

  @Test
  public void oldSecurityShouldSucceedIfAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(false);

    this.registerInterest.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.authzRequest).registerInterestAuthorize(eq(REGION_NAME), eq(KEY), anyInt(), any());
    verify(this.chunkedResponseMessage).sendChunk(this.serverConnection);
  }

  @Test
  public void oldSecurityShouldFailIfNotAuthorized() throws Exception {
    when(this.securityService.isClientSecurityRequired()).thenReturn(true);
    when(this.securityService.isIntegratedSecurity()).thenReturn(false);

    doThrow(new NotAuthorizedException("")).when(this.authzRequest).registerInterestAuthorize(eq(REGION_NAME), eq(KEY), anyInt(), any());

    this.registerInterest.cmdExecute(this.message, this.serverConnection, 0);

    verify(this.authzRequest).registerInterestAuthorize(eq(REGION_NAME), eq(KEY), anyInt(), any());

    ArgumentCaptor<NotAuthorizedException> argument = ArgumentCaptor.forClass(NotAuthorizedException.class);
    verify(this.chunkedResponseMessage).addObjPart(argument.capture());

    assertThat(argument.getValue()).isExactlyInstanceOf(NotAuthorizedException.class);
    verify(this.chunkedResponseMessage).sendChunk(this.serverConnection);
  }

}
