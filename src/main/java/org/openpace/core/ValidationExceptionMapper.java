/*
 * Copyright 2024 Open Pace Contributors
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
package org.openpace.core;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Exception mapper for Bean Validation errors.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(ValidationExceptionMapper.class.getName());

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String violations = exception.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining(", "));

        LOG.warning("Validation error: " + violations);

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ErrorResponse("Validation Error", violations))
            .build();
    }
}
