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

package org.kaazing.gateway.resource.address.httpxe;

import static org.kaazing.gateway.resource.address.ResourceFactories.changeSchemeOnly;
import static org.kaazing.gateway.resource.address.httpxe.HttpxeResourceAddressFactorySpi.PROTOCOL_NAME;

import java.net.URI;
import java.util.Map;

import org.kaazing.gateway.resource.address.ResourceFactory;
import org.kaazing.gateway.resource.address.ResourceOptions;
import org.kaazing.gateway.resource.address.http.HttpsResourceAddressFactorySpi;

public class HttpxeSslResourceAddressFactorySpi extends HttpsResourceAddressFactorySpi {

    private static final String SCHEME_NAME = "httpxe+ssl";
    private static final ResourceFactory TRANSPORT_FACTORY = changeSchemeOnly("https");

    @Override
    public String getSchemeName() {
        return SCHEME_NAME;
    }

    @Override
    protected ResourceFactory getTransportFactory() {
        return TRANSPORT_FACTORY;
    }
    
    @Override
    protected String getProtocolName() {
        return PROTOCOL_NAME;
    }

    @Override
    protected void setAlternateOption(URI location,
                                      ResourceOptions options,
                                      Map<String, Object> optionsByName) {
        // do not make alternates for httpxe addresses
    }

}
