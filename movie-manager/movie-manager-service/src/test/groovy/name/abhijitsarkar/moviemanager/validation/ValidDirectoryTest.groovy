/*
 * Copyright (c) ${date}, the original author or authors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * A copy of the GNU General Public License accompanies this software,
 * and is also available at http://www.gnu.org/licenses.
 */

package name.abhijitsarkar.moviemanager.validation

import name.abhijitsarkar.moviemanager.service.facade.MovieFacadeIntegrationTest
import name.abhijitsarkar.moviemanager.service.rip.MovieRipService
import org.junit.Test

import javax.validation.ConstraintViolation
import javax.validation.Validation
import javax.validation.ValidatorFactory
import javax.validation.executable.ExecutableValidator
import java.lang.annotation.Annotation
import java.lang.reflect.Method

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

/**
 * @author Abhijit Sarkar
 */
class ValidDirectoryTest {
    private ValidatorFactory factory = Validation.buildDefaultValidatorFactory()
    private ExecutableValidator executableValidator = factory.validator.forExecutables()

    private MovieRipService movieRipService = new MovieRipService()

    @Test
    public void testNonExistentDirectory() {
        Method getMovieRips = MovieRipService.getMethod('getMovieRips', String)
        Object[] args = ['whatever'] as String[]

        Set<ConstraintViolation<MovieRipService>> violations = executableValidator.validateParameters(
                movieRipService,
                getMovieRips,
                args
        )

        assertNumViolations(violations, 1)
    }

    @Test
    public void testNullDirectory() {
        Method getMovieRips = MovieRipService.getMethod('getMovieRips', String)
        Object[] args = [null] as String[]

        Set<ConstraintViolation<MovieRipService>> violations = executableValidator.validateParameters(
                movieRipService,
                getMovieRips,
                args
        )

        assertNumViolations(violations, 1)
    }

    @Test
    public void testValidDirectory() {
        Method getMovieRips = MovieRipService.getMethod('getMovieRips', String)
        String movieDir = new File(MovieFacadeIntegrationTest.class.getResource('/movies').toURI()).absolutePath
        Object[] args = [movieDir] as String[]

        Set<ConstraintViolation<MovieRipService>> violations = executableValidator.validateParameters(
                movieRipService,
                getMovieRips,
                args
        )

        assertNoViolations(violations)
    }

    private void assertNumViolations(Set<ConstraintViolation<MovieRipService>> violations, int numViolations) {
        assert numViolations == violations.size()

        Class<? extends Annotation> constraintType = null

        violations.each { ConstraintViolation<MovieRipService> aViolation ->
            constraintType = aViolation.constraintDescriptor.annotation.annotationType()

            assert ValidDirectory == constraintType
        }
    }

    private void assertNoViolations(Set<ConstraintViolation<MovieRipService>> violations) {
        assert violations.isEmpty()

        assert !violations
    }
}
