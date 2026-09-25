package edu.stankin.cogoalmain.web.error;

import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ExternalServiceException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} responses.
 * <p>
 * Spring MVC's own exceptions (malformed body, missing parameter, wrong method, upload too large, ...)
 * are handled by {@link ResponseEntityExceptionHandler}; bean validation errors additionally get
 * an {@code errors} list with the failing fields.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage());
    }

    // Thrown by @PreAuthorize inside controllers/services; without this handler it would never reach
    // the security AccessDeniedHandler because the advice sees it first
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalService(ExternalServiceException e) {
        return problem(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    // Races that slip past service checks end up here: unique indexes (one pending invitation,
    // one pact per goal, one payout per goal), FK restrictions, concurrent updates
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ProblemDetail handleConflict(RuntimeException e) {
        log.warn("Data conflict: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource");
    }

    // ?sort=unknownField from the client
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleBadSortProperty(PropertyReferenceException e) {
        return problem(HttpStatus.BAD_REQUEST, "Unknown sort property: " + e.getPropertyName());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", e.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), v.getMessage()))
                .toList());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        List<FieldErrorDto> errors = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toDto)
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    private static FieldErrorDto toDto(FieldError error) {
        return new FieldErrorDto(error.getField(), error.getDefaultMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    public record FieldErrorDto(String field, String message) {
    }
}
