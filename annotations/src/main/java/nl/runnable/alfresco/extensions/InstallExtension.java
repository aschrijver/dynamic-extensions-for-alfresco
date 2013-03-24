package nl.runnable.alfresco.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as containing logic for installing an extension. Extensions typically have one such method.
 * <p>
 * It is an error to combine this annotation with {@link UninstallExtension}.
 * 
 * @author Laurens Fridael
 * 
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface InstallExtension {

}
