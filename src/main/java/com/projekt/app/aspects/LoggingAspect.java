package com.projekt.app.aspects;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demonstrates all major Spring AOP Pointcut Expression types.
 *
 * <p>A pointcut is a predicate that selects which method executions (join points)
 * an advice should intercept. Pointcut expressions are written in AspectJ syntax
 * and can be composed with &&, ||, and !.
 *
 * <h2>Pointcut Designator Quick Reference</h2>
 * <ul>
 *   <li>{@code execution(…)}   — matches method execution join points</li>
 *   <li>{@code within(…)}      — matches all join points inside a type/package</li>
 *   <li>{@code @annotation(…)} — matches methods annotated with a given annotation</li>
 *   <li>{@code @within(…)}     — matches all methods in types annotated with something</li>
 *   <li>{@code bean(…)}        — matches Spring beans by name/wildcard</li>
 *   <li>{@code args(…)}        — matches methods whose runtime args are of given types</li>
 *   <li>{@code target(…)}      — matches on the runtime type of the target object</li>
 * </ul>
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    // =========================================================================
    // 1. NAMED POINTCUTS — reusable expressions referenced by other advice
    // =========================================================================

    /**
     * Matches every public method in every class inside the services package.
     *
     * Pattern: execution( [modifier] [return-type] [class].[method]([params]) )
     *   *  = any return type
     *   com.projekt.app.services.* = any class directly in the services package
     *   .*  = any method name
     *   (..) = any number/type of parameters
     */
    @Pointcut("execution(* com.projekt.app.services.*.*(..))")
    public void serviceLayer() {}

    /**
     * Matches every public method in every class inside the controllers package.
     * The double-dot in "controllers.*" would also cover sub-packages if present.
     */
    @Pointcut("execution(* com.projekt.app.controllers.*.*(..))")
    public void controllerLayer() {}

    /**
     * Matches only "get*" methods — any method whose name starts with "get".
     * Useful for targeting all read/query methods uniformly.
     */
    @Pointcut("execution(* com.projekt.app.services.*.get*(..))")
    public void serviceGetters() {}

    /**
     * Matches any method that accepts exactly one Long argument.
     * args() binds to the runtime type of the actual argument.
     */
    @Pointcut("args(java.lang.Long)")
    public void singleLongArg() {}

    /**
     * within() matches ALL join points inside a package, regardless of method signature.
     * Unlike execution(), it selects by *declaring type*, not method signature.
     */
    @Pointcut("within(com.projekt.app.services.*)")
    public void withinServicePackage() {}

    /**
     * bean() selects Spring-managed beans by name (supports * wildcard).
     * "bookService" targets that specific bean; "*Service" would match all services.
     */
    @Pointcut("bean(bookService)")
    public void bookServiceBean() {}

    /**
     * Composed pointcut: service-layer methods that also take a single Long.
     * Combines two named pointcuts with &&.
     */
    @Pointcut("serviceLayer() && singleLongArg()")
    public void serviceMethodWithLongId() {}

    // =========================================================================
    // 2. ADVICE TYPES — what to do at the matched join points
    // =========================================================================

    /**
     * @Before — runs BEFORE the matched method executes.
     *
     * Uses the named pointcut "serviceLayer()".
     * JoinPoint gives access to the method signature and its arguments.
     */
    @Before("serviceLayer()")
    public void logBeforeServiceMethod(JoinPoint jp) {
        log.info("[BEFORE] {}.{}() called with args: {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                jp.getArgs());
    }

    /**
     * @AfterReturning — runs AFTER the method returns normally (no exception).
     *
     * Uses an inline execution() expression instead of a named pointcut.
     * "returning" binds the actual return value to a parameter.
     */
    @AfterReturning(
        pointcut = "execution(* com.projekt.app.services.BookService.getBookById(..))",
        returning = "result"
    )
    public void logAfterGetBookById(JoinPoint jp, Object result) {
        log.info("[AFTER_RETURNING] getBookById returned: {}", result);
    }

    /**
     * @AfterThrowing — runs only when the method exits by throwing an exception.
     *
     * "throwing" binds the actual exception so you can inspect its type/message.
     * Pointcut: any method in any class inside com.projekt.app and sub-packages.
     */
    @AfterThrowing(
        pointcut = "within(com.projekt.app..*)",
        throwing = "ex"
    )
    public void logException(JoinPoint jp, Exception ex) {
        log.error("[AFTER_THROWING] Exception in {}.{}(): {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                ex.getMessage());
    }

    /**
     * @After — runs AFTER the method exits, whether normally or by exception.
     * Think of it as a "finally" block equivalent.
     *
     * Uses the composed pointcut that targets service methods taking a Long ID.
     */
    @After("serviceMethodWithLongId()")
    public void logAfterLookupById(JoinPoint jp) {
        log.info("[AFTER] Finished {}.{}()",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName());
    }

    /**
     * @Around — the most powerful advice; wraps the entire method execution.
     *
     * You MUST call proceed() to actually invoke the target method.
     * This example measures execution time for every controller endpoint.
     *
     * Inline bean() expression: matches all join points on beans named like "*Controller".
     */
    @Around("bean(*Controller)")
    public Object measureControllerTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();  // invoke the real method
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("[AROUND] {}.{}() took {} ms",
                    pjp.getTarget().getClass().getSimpleName(),
                    pjp.getSignature().getName(),
                    elapsed);
        }
    }
}
