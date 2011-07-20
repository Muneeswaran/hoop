/*
 * Copyright (c) 2011, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.lib.servlet;

import com.cloudera.circus.test.XTest;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;


@Test(singleThreaded = true)
public class TestHostnameFilter extends XTest {

  @Test
  public void hostname() throws Exception {
    ServletRequest request = Mockito.mock(ServletRequest.class);
    Mockito.when(request.getRemoteAddr()).thenReturn("localhost");

    ServletResponse response = Mockito.mock(ServletResponse.class);

    final AtomicBoolean invoked = new AtomicBoolean();

    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse)
        throws IOException, ServletException {
        Assert.assertEquals(HostnameFilter.get(), "localhost");
        invoked.set(true);
      }
    };

    Filter filter = new HostnameFilter();
    filter.init(null);
    Assert.assertNull(HostnameFilter.get());
    filter.doFilter(request, response, chain);
    Assert.assertTrue(invoked.get());
    Assert.assertNull(HostnameFilter.get());
    filter.destroy();
  }

}
