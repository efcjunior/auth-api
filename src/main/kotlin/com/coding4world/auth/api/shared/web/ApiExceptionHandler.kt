package com.coding4world.auth.api.shared.web

import com.coding4world.auth.api.auth.application.InvalidAudienceException
import com.coding4world.auth.api.auth.application.InvalidAuthenticationException
import com.coding4world.auth.api.user.application.UserAlreadyExistsException
import com.coding4world.auth.api.user.application.UserNotFoundException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ApiExceptionHandler(
    private val problems: ApiProblemFactory,
) {
    @ExceptionHandler(InvalidAuthenticationException::class)
    fun invalidAuthentication(): ResponseEntity<ProblemDetail> =
        response(
            HttpStatus.UNAUTHORIZED,
            "Authentication failed",
            "The supplied authentication credentials are invalid",
            "INVALID_AUTHENTICATION",
        )

    @ExceptionHandler(InvalidAudienceException::class)
    fun invalidAudience(): ResponseEntity<ProblemDetail> =
        response(
            HttpStatus.BAD_REQUEST,
            "Invalid audience",
            "The requested audience is missing or not allowed",
            "INVALID_AUDIENCE",
        )

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun userAlreadyExists(): ResponseEntity<ProblemDetail> =
        response(
            HttpStatus.CONFLICT,
            "User already exists",
            "A user with this email already exists",
            "USER_ALREADY_EXISTS",
        )

    @ExceptionHandler(UserNotFoundException::class)
    fun userNotFound(exception: UserNotFoundException): ResponseEntity<ProblemDetail> =
        response(
            HttpStatus.NOT_FOUND,
            "User not found",
            exception.message ?: "The requested user was not found",
            "USER_NOT_FOUND",
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(exception: IllegalArgumentException): ResponseEntity<ProblemDetail> =
        response(HttpStatus.BAD_REQUEST, "Invalid request", exception.message ?: "The request is invalid", "INVALID_REQUEST")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(exception: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val problem = problems.create(HttpStatus.BAD_REQUEST, "Validation failed", "The request body is invalid", "VALIDATION_FAILED")
        problem.setProperty(
            "violations",
            exception.bindingResult.fieldErrors.map { error ->
                mapOf("field" to error.field, "message" to (error.defaultMessage ?: "Invalid value"))
            },
        )
        return ResponseEntity.badRequest().body(problem)
    }

    @ExceptionHandler(HandlerMethodValidationException::class, ConstraintViolationException::class)
    fun invalidParameters(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.BAD_REQUEST, "Validation failed", "One or more request parameters are invalid", "VALIDATION_FAILED")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.BAD_REQUEST, "Malformed request", "The request body could not be read", "MALFORMED_REQUEST")

    @ExceptionHandler(MissingServletRequestParameterException::class, MethodArgumentTypeMismatchException::class)
    fun invalidRequestParameter(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.BAD_REQUEST, "Invalid parameter", "A request parameter is missing or invalid", "INVALID_PARAMETER")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(): ResponseEntity<ProblemDetail> =
        response(
            HttpStatus.METHOD_NOT_ALLOWED,
            "Method not allowed",
            "The HTTP method is not supported for this resource",
            "METHOD_NOT_ALLOWED",
        )

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaTypeNotSupported(): ResponseEntity<ProblemDetail> =
        response(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type",
            "The request media type is not supported",
            "UNSUPPORTED_MEDIA_TYPE",
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun dataConflict(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.CONFLICT, "Resource conflict", "The request conflicts with an existing resource", "RESOURCE_CONFLICT")

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<ProblemDetail> {
        logger.error("Unhandled request failure with trace ID {} and type {}", problems.currentTraceId(), exception.javaClass.simpleName)
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "An unexpected error occurred",
            "INTERNAL_ERROR",
        )
    }

    private fun response(
        status: HttpStatus,
        title: String,
        detail: String,
        code: String,
    ): ResponseEntity<ProblemDetail> = ResponseEntity.status(status).body(problems.create(status, title, detail, code))

    private companion object {
        val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
