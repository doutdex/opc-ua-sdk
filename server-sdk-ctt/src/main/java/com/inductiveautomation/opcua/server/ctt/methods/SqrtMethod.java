/*
 * Copyright 2014
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

package com.inductiveautomation.opcua.server.ctt.methods;

import com.inductiveautomation.opcua.sdk.server.util.AnnotationBasedInvocationHandler.InvocationContext;
import com.inductiveautomation.opcua.sdk.server.util.AnnotationBasedInvocationHandler.Out;
import com.inductiveautomation.opcua.sdk.server.util.UaInputArgument;
import com.inductiveautomation.opcua.sdk.server.util.UaMethod;
import com.inductiveautomation.opcua.sdk.server.util.UaOutputArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqrtMethod {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @UaMethod
    public void invoke(
            InvocationContext context,

            @UaInputArgument(
                    name = "x",
                    description = "A value.")
            Double x,

            @UaOutputArgument(
                    name = "x_sqrt",
                    description = "The positive square root of x. If the argument is NaN or less than zero, the result is NaN.")
            Out<Double> xSqrt) {

        logger.debug(String.format("Invoking sqrt() method of Object '%s'", context.getObjectNode().getBrowseName().getName()));

        xSqrt.set(Math.sqrt(x));
    }

}
