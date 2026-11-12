package devsweep;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitsTest {

    @Test
    void parsesSizes() {
        assertEquals(500, Units.parseSize("500"));
        assertEquals(10L * 1024 * 1024, Units.parseSize("10MB"));
        assertEquals(10L * 1024 * 1024, Units.parseSize("10m"));
        assertEquals((long) (1.5 * 1024 * 1024 * 1024), Units.parseSize("1.5GB"));
        assertEquals(2L * 1024 * 1024 * 1024, Units.parseSize("2GiB"));
        assertThrows(IllegalArgumentException.class, () -> Units.parseSize("lots"));
    }

    @Test
    void parsesAges() {
        assertEquals(Duration.ofDays(30), Units.parseAge("30"));
        assertEquals(Duration.ofDays(30), Units.parseAge("30d"));
        assertEquals(Duration.ofDays(42), Units.parseAge("6w"));
        assertEquals(Duration.ofDays(90), Units.parseAge("3m"));
        assertEquals(Duration.ofDays(365), Units.parseAge("1y"));
        assertThrows(IllegalArgumentException.class, () -> Units.parseAge("soon"));
    }

    @Test
    void formatsSizes() {
        assertEquals("512 B", Units.formatSize(512));
        assertEquals("1.5 KB", Units.formatSize(1536));
        assertEquals("2.0 GB", Units.formatSize(2L * 1024 * 1024 * 1024));
    }

    @Test
    void formatsAges() {
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        assertEquals("today", Units.formatAge(now, now));
        assertEquals("5d ago", Units.formatAge(now.minus(Duration.ofDays(5)), now));
        assertEquals("4mo ago", Units.formatAge(now.minus(Duration.ofDays(125)), now));
        assertEquals("3y ago", Units.formatAge(now.minus(Duration.ofDays(1100)), now));
    }
}
