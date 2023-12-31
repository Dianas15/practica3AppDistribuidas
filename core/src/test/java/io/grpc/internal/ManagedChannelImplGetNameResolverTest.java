/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import io.grpc.ChannelLogger;
import io.grpc.NameResolver;
import io.grpc.NameResolver.ServiceConfigParser;
import io.grpc.ProxyDetector;
import io.grpc.SynchronizationContext;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ManagedChannelImpl#getNameResolver(
 * String, String,NameResolver.Factory, NameResolver.Args)}. */
@RunWith(JUnit4.class)
public class ManagedChannelImplGetNameResolverTest {
  private static final NameResolver.Args NAMERESOLVER_ARGS = NameResolver.Args.newBuilder()
      .setDefaultPort(447)
      .setProxyDetector(mock(ProxyDetector.class))
      .setSynchronizationContext(new SynchronizationContext(mock(UncaughtExceptionHandler.class)))
      .setServiceConfigParser(mock(ServiceConfigParser.class))
      .setChannelLogger(mock(ChannelLogger.class))
      .setScheduledExecutorService(new FakeClock().getScheduledExecutorService())
      .build();

  @Test
  public void invalidUriTarget() {
    testInvalidTarget("defaultscheme:///[invalid]");
  }

  @Test
  public void validTargetWithInvalidDnsName() throws Exception {
    testValidTarget("[valid]", "defaultscheme:///%5Bvalid%5D",
        new URI("defaultscheme", "", "/[valid]", null));
  }

  @Test
  public void validAuthorityTarget() throws Exception {
    testValidTarget("foo.googleapis.com:8080", "defaultscheme:///foo.googleapis.com:8080",
        new URI("defaultscheme", "", "/foo.googleapis.com:8080", null));
  }

  @Test
  public void validAuthorityTarget_overrideAuthority() throws Exception {
    String target = "foo.googleapis.com:8080";
    String overrideAuthority = "override.authority";
    URI expectedUri = new URI("defaultscheme", "", "/foo.googleapis.com:8080", null);
    NameResolver.Factory nameResolverFactory = new FakeNameResolverFactory(expectedUri.getScheme());
    NameResolver nameResolver = ManagedChannelImpl.getNameResolver(
        target, overrideAuthority, nameResolverFactory, NAMERESOLVER_ARGS);
    assertThat(nameResolver.getServiceAuthority()).isEqualTo(overrideAuthority);
  }

  @Test
  public void validUriTarget() throws Exception {
    testValidTarget("scheme:///foo.googleapis.com:8080", "scheme:///foo.googleapis.com:8080",
        new URI("scheme", "", "/foo.googleapis.com:8080", null));
  }

  @Test
  public void validIpv4AuthorityTarget() throws Exception {
    testValidTarget("127.0.0.1:1234", "defaultscheme:///127.0.0.1:1234",
        new URI("defaultscheme", "", "/127.0.0.1:1234", null));
  }

  @Test
  public void validIpv4UriTarget() throws Exception {
    testValidTarget("dns:///127.0.0.1:1234", "dns:///127.0.0.1:1234",
        new URI("dns", "", "/127.0.0.1:1234", null));
  }

  @Test
  public void validIpv6AuthorityTarget() throws Exception {
    testValidTarget("[::1]:1234", "defaultscheme:///%5B::1%5D:1234",
        new URI("defaultscheme", "", "/[::1]:1234", null));
  }

  @Test
  public void invalidIpv6UriTarget() throws Exception {
    testInvalidTarget("dns:///[::1]:1234");
  }

  @Test
  public void validIpv6UriTarget() throws Exception {
    testValidTarget("dns:///%5B::1%5D:1234", "dns:///%5B::1%5D:1234",
        new URI("dns", "", "/[::1]:1234", null));
  }

  @Test
  public void validTargetStartingWithSlash() throws Exception {
    testValidTarget("/target", "defaultscheme:////target",
        new URI("defaultscheme", "", "//target", null));
  }

  @Test
  public void validTargetNoResovler() {
    NameResolver.Factory nameResolverFactory = new NameResolver.Factory() {
      @Override
      public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        return null;
      }

      @Override
      public String getDefaultScheme() {
        return "defaultscheme";
      }
    };
    try {
      ManagedChannelImpl.getNameResolver(
          "foo.googleapis.com:8080", null, nameResolverFactory, NAMERESOLVER_ARGS);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  private void testValidTarget(String target, String expectedUriString, URI expectedUri) {
    NameResolver.Factory nameResolverFactory = new FakeNameResolverFactory(expectedUri.getScheme());
    FakeNameResolver nameResolver
        = (FakeNameResolver) ((RetryingNameResolver) ManagedChannelImpl.getNameResolver(
            target, null, nameResolverFactory, NAMERESOLVER_ARGS)).getRetriedNameResolver();
    assertNotNull(nameResolver);
    assertEquals(expectedUri, nameResolver.uri);
    assertEquals(expectedUriString, nameResolver.uri.toString());
  }

  private void testInvalidTarget(String target) {
    NameResolver.Factory nameResolverFactory = new FakeNameResolverFactory("dns");

    try {
      FakeNameResolver nameResolver = (FakeNameResolver) ManagedChannelImpl.getNameResolver(
          target, null, nameResolverFactory, NAMERESOLVER_ARGS);
      fail("Should have failed, but got resolver with " + nameResolver.uri);
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  private static class FakeNameResolverFactory extends NameResolver.Factory {
    final String expectedScheme;

    FakeNameResolverFactory(String expectedScheme) {
      this.expectedScheme = expectedScheme;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
      if (expectedScheme.equals(targetUri.getScheme())) {
        return new FakeNameResolver(targetUri);
      }
      return null;
    }

    @Override
    public String getDefaultScheme() {
      return expectedScheme;
    }
  }

  private static class FakeNameResolver extends NameResolver {
    final URI uri;

    FakeNameResolver(URI uri) {
      this.uri = uri;
    }

    @Override public String getServiceAuthority() {
      return uri.getAuthority();
    }

    @Override public void start(final Listener2 listener) {}

    @Override public void shutdown() {}
  }
}
