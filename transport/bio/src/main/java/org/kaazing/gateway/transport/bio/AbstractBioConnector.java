/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.transport.bio;

import static java.lang.String.format;
import static org.kaazing.gateway.transport.BridgeSession.LOCAL_ADDRESS;
import static org.kaazing.gateway.transport.BridgeSession.REMOTE_ADDRESS;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionInitializer;
import org.kaazing.gateway.resource.address.ResourceAddress;
import org.kaazing.gateway.resource.address.ResourceAddressFactory;
import org.kaazing.gateway.transport.BridgeConnectHandler;
import org.kaazing.gateway.transport.BridgeConnector;
import org.kaazing.gateway.transport.BridgeServiceFactory;
import org.kaazing.gateway.transport.ExceptionLoggingFilter;
import org.kaazing.gateway.transport.NamedPipeAddress;
import org.kaazing.gateway.transport.ObjectLoggingFilter;
import org.kaazing.gateway.transport.SocketAddressFactory;
import org.slf4j.Logger;

public abstract class AbstractBioConnector<T extends SocketAddress> implements BridgeConnector {
    private static final String FAULT_LOGGING_FILTER = "#fault";
    private static final String TRACE_LOGGING_FILTER = "#logging";

    private IoConnector connector;
    private SocketAddressFactory<T> socketAddressFactory;
    private BridgeServiceFactory bridgeServiceFactory;
    private ResourceAddressFactory resourceAddressFactory;
    protected final Logger logger;

    private final AtomicBoolean started;

    protected AbstractBioConnector(Logger logger) {
        started = new AtomicBoolean(false);
        this.logger = logger;
    }

    protected final void init() {
        connector = initConnector();
        connector.setHandler(new BridgeConnectHandler() {
            @Override
            public void sessionCreated(IoSession session) throws Exception {
                if (logger.isTraceEnabled()) {
                    session.getFilterChain().addLast(getTransportName() + TRACE_LOGGING_FILTER,
                            new ObjectLoggingFilter(logger, getTransportName() + "#%s"));
                } else if (logger.isDebugEnabled()) {
                    session.getFilterChain().addLast(getTransportName() + FAULT_LOGGING_FILTER,
                            new ExceptionLoggingFilter(logger, getTransportName() + "#%s"));
                }

                super.sessionCreated(session);
            }
        });

        socketAddressFactory = initSocketAddressFactory();
        bridgeServiceFactory = initBridgeServiceFactory();
        resourceAddressFactory = initResourceAddressFactory();
    }

    protected abstract SocketAddressFactory<T> initSocketAddressFactory();
    protected abstract ResourceAddressFactory initResourceAddressFactory();
    protected abstract BridgeServiceFactory initBridgeServiceFactory();

    @Override
    public void dispose() {
    	if (connector != null) {
    		connector.dispose();
    	}
    }

    @Override
    public ConnectFuture connect(ResourceAddress address, IoHandler handler, IoSessionInitializer<? extends ConnectFuture> initializer) {
        if (started.compareAndSet(false, true)) {
            init();
        }

        return connectInternal(address, handler, initializer);
    }

    @Override
    public void connectInit(ResourceAddress address) {
        // no-op by default
    }


    @Override
    public void connectDestroy(ResourceAddress address) {
        // no-op by default
    }

    // THIS IS A BUG IN JAVA, should not need two methods just to capture the type
    protected <F extends ConnectFuture> ConnectFuture connectInternal(final ResourceAddress remoteAddress, final IoHandler handler, final IoSessionInitializer<F> initializer) {
        
        ConnectFuture future;

        ResourceAddress transport = remoteAddress.getTransport();
        if (transport != null) {
            BridgeConnector connector = bridgeServiceFactory.newBridgeConnector(transport);
            future = connector.connect(transport, handler, new IoSessionInitializer<F>() {
                @Override
                public void initializeSession(IoSession session, F future) {
                    REMOTE_ADDRESS.set(session, remoteAddress);
                    setLocalAddressFromSocketAddress(session, getTransportName());

                    if (initializer != null) {
                        initializer.initializeSession(session, future);
                    }
                }
            });

        } else {

            T socketAddress = socketAddressFactory.createSocketAddress(remoteAddress);

            future = connector.connect(socketAddress, new IoSessionInitializer<F>() {
                @Override
                public void initializeSession(IoSession session, F future) {

                    // connectors don't need lookup so set this directly on the session
                    session.setAttribute(BridgeConnectHandler.DELEGATE_KEY, handler);
                    REMOTE_ADDRESS.set(session, remoteAddress);
                    setLocalAddressFromSocketAddress(session, getTransportName());

                    if (initializer != null) {
                        initializer.initializeSession(session, future);
                    }
                }
            });
        }

        return future;
    }

    private void setLocalAddressFromSocketAddress(final IoSession session,
                                                  final String transportName) {
        SocketAddress socketAddress = session.getLocalAddress();
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
            ResourceAddress resourceAddress = newResourceAddress(inetSocketAddress,
                                                                                transportName);
            LOCAL_ADDRESS.set(session, resourceAddress);
        }
        else if (socketAddress instanceof NamedPipeAddress) {
            NamedPipeAddress namedPipeAddress = (NamedPipeAddress) socketAddress;
            ResourceAddress resourceAddress = newResourceAddress(namedPipeAddress,
                                                                                transportName);
            LOCAL_ADDRESS.set(session, resourceAddress);
        }
    }

    public  ResourceAddress newResourceAddress(NamedPipeAddress namedPipeAddress,
                                               final String transportName) {
        String addressFormat = "%s://%s";
        String pipeName = namedPipeAddress.getPipeName();
        URI transport = URI.create(format(addressFormat, transportName, pipeName));
        return resourceAddressFactory.newResourceAddress(transport);
    }

    public  ResourceAddress newResourceAddress(InetSocketAddress inetSocketAddress,
                                               final String transportName) {
        InetAddress inetAddress = inetSocketAddress.getAddress();
        String hostAddress = inetAddress.getHostAddress();
        String addressFormat = (inetAddress instanceof Inet6Address) ? "%s://[%s]:%s" : "%s://%s:%s";
        int port = inetSocketAddress.getPort();
        URI transport = URI.create(format(addressFormat, transportName, hostAddress, port));
        return resourceAddressFactory.newResourceAddress(transport);
    }

    protected abstract IoConnector initConnector();

    protected abstract String getTransportName();
}
