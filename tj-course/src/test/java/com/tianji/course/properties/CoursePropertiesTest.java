package com.tianji.course.properties;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoursePropertiesTest {
    @Test
    void trialDefaultsToOneMinuteAndRejectsUnboundedConfiguration() {
        var properties = new CourseProperties();
        assertEquals(1, properties.getMedia().getTrailerDuration());
        properties.getMedia().setTrailerDuration(2);
        assertEquals(2, properties.getMedia().getTrailerDuration());
        assertThrows(
                IllegalArgumentException.class, () -> properties.getMedia().setTrailerDuration(0));
        assertThrows(
                IllegalArgumentException.class, () -> properties.getMedia().setTrailerDuration(31));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.getMedia().setTrailerDuration(null));
    }
}
