/*
 * This file is part of ARSnova Backend.
 * Copyright (C) 2012-2017 The ARSnova Team
 *
 * ARSnova Backend is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ARSnova Backend is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.thm.arsnova.controller;

import de.thm.arsnova.exceptions.BadRequestException;
import de.thm.arsnova.exceptions.ForbiddenException;
import de.thm.arsnova.exceptions.NoContentException;
import de.thm.arsnova.exceptions.NotFoundException;
import de.thm.arsnova.exceptions.NotImplementedException;
import de.thm.arsnova.exceptions.PayloadTooLargeException;
import de.thm.arsnova.exceptions.PreconditionFailedException;
import de.thm.arsnova.exceptions.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Translates exceptions into HTTP status codes.
 */
@ControllerAdvice
public class ControllerExceptionHandler extends AbstractControllerExceptionHandler {
	@ExceptionHandler(NotFoundException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, Object> handleNotFoundException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(UnauthorizedException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public Map<String, Object> handleUnauthorizedException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	@ResponseBody
	public Map<String, Object> handleAuthenticationCredentialsNotFoundException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(AccessDeniedException.class)
	@ResponseBody
	public Map<String, Object> handleAccessDeniedException(
			final Exception e,
			final HttpServletRequest request,
			final HttpServletResponse response
			) {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| authentication.getPrincipal() == null
				|| authentication instanceof AnonymousAuthenticationToken) {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
		} else {
			response.setStatus(HttpStatus.FORBIDDEN.value());
		}

		return handleException(e);
	}

	@ExceptionHandler(ForbiddenException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public Map<String, Object> handleForbiddenException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(NoContentException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Map<String, Object> handleNoContentException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(BadRequestException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleBadRequestException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(PreconditionFailedException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.PRECONDITION_FAILED)
	public Map<String, Object> handlePreconditionFailedException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(NotImplementedException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
	public Map<String, Object> handleNotImplementedException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(PayloadTooLargeException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
	public Map<String, Object> handlePayloadTooLargeException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseBody
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleHttpMessageNotReadableException(final Exception e, final HttpServletRequest request) {
		return handleException(e);
	}
}
